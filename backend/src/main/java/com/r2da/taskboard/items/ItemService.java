package com.r2da.taskboard.items;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.r2da.taskboard.auth.CurrentUser;
import com.r2da.taskboard.common.FileStore;
import com.r2da.taskboard.common.PageDto;
import com.r2da.taskboard.common.Problems;
import com.r2da.taskboard.history.Action;
import com.r2da.taskboard.history.HistoryService;
import com.r2da.taskboard.history.HistoryService.HistoryDto;
import com.r2da.taskboard.history.HistoryService.Target;
import com.r2da.taskboard.items.ItemDto.ChoiceRef;
import com.r2da.taskboard.items.ItemDto.MilestoneRef;
import com.r2da.taskboard.items.ItemDto.ParentRef;
import com.r2da.taskboard.projects.Access;
import com.r2da.taskboard.projects.Choice;
import com.r2da.taskboard.projects.ChoiceRepository;
import com.r2da.taskboard.projects.Kind;
import com.r2da.taskboard.projects.MembershipRepository;
import com.r2da.taskboard.projects.Milestone;
import com.r2da.taskboard.projects.MilestoneRepository;
import com.r2da.taskboard.projects.Perm;
import com.r2da.taskboard.projects.Project;
import com.r2da.taskboard.projects.ProjectRepository;
import com.r2da.taskboard.projects.Status;
import com.r2da.taskboard.projects.StatusRef;
import com.r2da.taskboard.projects.StatusRepository;
import com.r2da.taskboard.users.User;
import com.r2da.taskboard.users.UserRef;
import com.r2da.taskboard.users.UserRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class ItemService {

    public record Filter(Long status, String milestone, Long parent, String assignee, String tag, String q,
            LocalDate dueBefore, Instant modifiedSince, Boolean closed, Boolean blocked, Boolean watching,
            Boolean archived, String sort, int page, int size) {
    }

    private static final Map<String, String> SORTS = Map.of("order", "i.board_order", "list", "i.list_order",
            "ref", "i.ref", "created", "i.created_at", "modified", "i.modified_at", "due", "i.due_date", "points",
            "i.points");
    private static final BigDecimal MAX_POINTS = new BigDecimal("9999.9");
    private static final Set<String> MOVE_FIELDS = Set.of("order", "statusId", "milestoneId", "parentId", "beforeId");

    private final ItemRepository items;
    private final Access access;
    private final HistoryService history;
    private final StatusRepository statuses;
    private final MilestoneRepository milestones;
    private final ChoiceRepository choices;
    private final MembershipRepository memberships;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final FileStore files;
    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;
    private final JsonMapper mapper;

    @Transactional(readOnly = true)
    public PageDto<ItemDto> list(String projectKey, Kind kind, Filter f, CurrentUser me) {
        Long projectId = access.require(projectKey, Perm.of("view", kind), me).project().getId();
        StringBuilder where = new StringBuilder("i.project_id = :project and i.type = :type");
        Map<String, Object> params = new HashMap<>();
        params.put("project", projectId);
        params.put("type", kind.name());
        if (f.status() != null) {
            where.append(" and i.status_id = :status");
            params.put("status", f.status());
        }
        if (f.milestone() != null) {
            if (f.milestone().equals("none")) {
                where.append(" and i.milestone_id is null");
            } else if (f.milestone().matches("\\d{1,18}")) {
                where.append(" and i.milestone_id = :milestone");
                params.put("milestone", Long.valueOf(f.milestone()));
            } else {
                throw Problems.badRequest("milestone: use o id da sprint ou \"none\" para o backlog.");
            }
        }
        if (f.parent() != null) {
            where.append(" and i.parent_id = :parent");
            params.put("parent", f.parent());
        }
        if (f.assignee() != null) {
            Long userId = assigneeFilter(f.assignee(), me);
            if (userId == null) {
                where.append(" and cardinality(i.assigned_users) = 0");
            } else {
                where.append(" and :assignee = any (i.assigned_users)");
                params.put("assignee", userId);
            }
        }
        if (Boolean.TRUE.equals(f.watching())) {
            where.append(" and :watcher = any (i.watchers)");
            params.put("watcher", me.id());
        }
        if (f.tag() != null) {
            where.append(" and :tag = any (i.tags)");
            params.put("tag", f.tag());
        }
        if (f.dueBefore() != null) {
            where.append(" and i.due_date < :dueBefore");
            params.put("dueBefore", f.dueBefore());
        }
        if (f.modifiedSince() != null) {
            where.append(" and i.modified_at >= :modifiedSince");
            params.put("modifiedSince", java.sql.Timestamp.from(f.modifiedSince()));
        }
        if (f.blocked() != null) {
            where.append(" and i.is_blocked = :blocked");
            params.put("blocked", f.blocked());
        }
        if (f.closed() != null) {
            where.append(" and s.is_closed = :closed");
            params.put("closed", f.closed());
        }
        if (f.archived() != null) {
            where.append(" and s.is_archived = :archived");
            params.put("archived", f.archived());
        }
        if (f.q() != null && !f.q().isBlank()) {
            List<Long> ids = searchIds(kind, projectId, f.q());
            where.append(" and i.id in (:found)");
            params.put("found", ids.isEmpty() ? List.of(-1L) : ids);
        }
        String from = " from items i join statuses s on s.id = i.status_id where " + where;
        int size = Math.clamp(f.size(), 1, PageDto.MAX_SIZE);
        int page = Math.max(f.page(), 1);
        long total = jdbc.sql("select count(*)" + from).params(params).query(Long.class).single();
        List<Long> ids = jdbc.sql("select i.id" + from + " order by " + sort(f.sort()) + ", i.id limit :limit offset :offset")
                .params(params).param("limit", size).param("offset", (page - 1) * size).query(Long.class).list();
        return new PageDto<>(toDtos(inOrder(items.findAllById(ids), ids)), page, size, total);
    }

    private static String sort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "i.board_order";
        }
        boolean descending = sort.startsWith("-");
        String column = SORTS.get(descending ? sort.substring(1) : sort);
        if (column == null) {
            throw Problems.badRequest("sort: use " + SORTS.keySet().stream().sorted().collect(Collectors.joining(", "))
                    + ", com \"-\" na frente para ordem decrescente.");
        }
        return column + (descending ? " desc nulls last" : " asc nulls last");
    }

    static List<Item> inOrder(List<Item> list, List<Long> ids) {
        return list.stream().sorted(Comparator.comparingInt(item -> ids.indexOf(item.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public ItemDto get(Long id, CurrentUser me) {
        return detail(require(id, "view", me));
    }

    @Transactional(readOnly = true)
    public ItemDto getByRef(String projectKey, int ref, CurrentUser me) {
        Access.Ctx ctx = access.require(projectKey, Perm.view_project, me);
        Item item = items.findByProjectIdAndRef(ctx.project().getId(), ref).filter(i -> ctx.can(Perm.of("view", i.getType())))
                .orElseThrow(() -> Problems.notFound("Item #" + ref + " não encontrado."));
        return detail(item);
    }

    @Transactional(readOnly = true)
    public List<HistoryDto> history(Long id, CurrentUser me) {
        Item item = require(id, "view", me);
        return history.of(item.getType().name(), item.getId());
    }

    @Transactional
    public ItemDto create(String projectKey, Kind kind, Map<String, Object> body, CurrentUser me) {
        Long projectId = access.require(projectKey, Perm.of("add", kind), me).project().getId();
        return detail(create(projectId, kind, body, me));
    }

    @Transactional
    public List<ItemDto> bulkCreate(String projectKey, Kind kind, String text, Map<String, Object> body,
            CurrentUser me) {
        Long projectId = access.require(projectKey, Perm.of("add", kind), me).project().getId();
        List<String> subjects = text == null ? List.of()
                : text.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
        if (subjects.isEmpty() || subjects.size() > 200) {
            throw Problems.badRequest("bulk: informe de 1 a 200 linhas, um item por linha.");
        }
        boolean top = "top".equals(body.get("position"));
        List<Item> created = new ArrayList<>();
        for (String subject : top ? subjects.reversed() : subjects) {
            Map<String, Object> one = new HashMap<>(body);
            one.put("subject", subject);
            created.add(create(projectId, kind, one, me));
        }
        return toDtos(top ? created.reversed() : created);
    }

    private Item create(Long projectId, Kind kind, Map<String, Object> body, CurrentUser me) {
        ItemInput in = input(body, kind);
        Item item = new Item();
        item.setProjectId(projectId);
        item.setType(kind);
        item.setCreatedBy(me.id());
        item.setSubject(text("subject", in.subject(), 500, true));
        Map<String, Object> ignored = new HashMap<>();
        apply(item, in, body.keySet(), ignored, me);
        if (item.getStatusId() == null) {
            changeStatus(item, defaultStatus(projectId, kind), ignored);
        }
        if (kind == Kind.task && item.getParentId() == null && item.getMilestoneId() == null) {
            throw Problems.badRequest("A tarefa precisa de uma história de usuário (parentId) ou de uma sprint (milestoneId).");
        }
        if (kind == Kind.issue) {
            defaultChoices(item);
        }
        if (in.position() != null && !in.position().equals("top") && !in.position().equals("bottom")) {
            throw Problems.badRequest("position: use \"top\" ou \"bottom\".");
        }
        boolean top = "top".equals(in.position());
        item.setRef(nextRef(projectId));
        item.setBoardOrder(edge(item, true, top));
        item.setListOrder(edge(item, false, top));
        items.saveAndFlush(item);
        if (body.containsKey("watchers")) {
            writeWatchers(item, in.watchers());
            entityManager.refresh(item);
        }
        history.record(projectId, target(item), Action.create, Map.of(), null, List.of(item.getAssignedUsers()), me);
        return item;
    }

    @Transactional
    public ItemDto update(Long id, Map<String, Object> body, CurrentUser me) {
        Item item = require(id, "modify", me);
        ItemInput in = input(body, item.getType());
        if (in.version() == null) {
            throw Problems.badRequest("version é obrigatório: envie a versão lida do item.");
        }
        if (in.version() != item.getVersion()) {
            throw Problems.conflict("O item foi alterado por outra pessoa. Recarregue e tente de novo.");
        }
        Map<String, Object> diff = new LinkedHashMap<>();
        List<Long> assignedBefore = List.of(item.getAssignedUsers());
        Set<String> moved = apply(item, in, body.keySet(), diff, me);
        items.saveAndFlush(item);
        if (moved.contains("board")) {
            toEnd(item, true);
        }
        if (moved.contains("list")) {
            toEnd(item, false);
            syncTasks(item);
        }
        if (body.containsKey("watchers")) {
            writeWatchers(item, in.watchers());
        }
        entityManager.refresh(item);
        if (!diff.isEmpty()) {
            history.record(item.getProjectId(), target(item), Action.change, diff, null,
                    Stream.of(item.getAssignedUsers()).filter(userId -> !assignedBefore.contains(userId)).toList(),
                    me);
        }
        return detail(item);
    }

    @Transactional
    public ItemDto move(Long id, Map<String, Object> body, CurrentUser me) {
        Item item = require(id, "modify", me);
        List<String> unknown = body.keySet().stream().filter(k -> !MOVE_FIELDS.contains(k)).sorted().toList();
        if (!unknown.isEmpty()) {
            throw Problems.badRequest("Campos desconhecidos: " + String.join(", ", unknown) + ".");
        }
        Object order = body.getOrDefault("order", "board");
        if (!"board".equals(order) && !"list".equals(order)) {
            throw Problems.badRequest("order: use \"board\" ou \"list\".");
        }
        Map<String, Object> fields = new HashMap<>(body);
        fields.remove("order");
        Object before = fields.remove("beforeId");
        if (before != null && !(before instanceof Number)) {
            throw Problems.badRequest("beforeId precisa ser o id de um item.");
        }
        Long beforeId = before == null ? null : ((Number) before).longValue();
        Map<String, Object> diff = new LinkedHashMap<>();
        apply(item, input(fields, item.getType()), fields.keySet(), diff, me);
        if (!diff.isEmpty()) {
            items.saveAndFlush(item);
            syncTasks(item);
        }
        boolean board = "board".equals(order);
        List<Long> siblings = scoped("select id from items where ", item, board)
                .sql(" and id <> :id order by " + (board ? "board_order" : "list_order") + ", id")
                .param("id", item.getId()).ids();
        List<Long> ordered = new ArrayList<>(siblings);
        int index = beforeId == null ? ordered.size() : ordered.indexOf(beforeId);
        if (index < 0) {
            throw Problems.badRequest("beforeId não está no destino.");
        }
        ordered.add(index, item.getId());
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            rows.add(new Object[] { i + 1, ordered.get(i) });
        }
        jdbcTemplate.batchUpdate("update items set " + (board ? "board_order" : "list_order") + " = ? where id = ?", rows);
        entityManager.refresh(item);
        if (!diff.isEmpty()) {
            history.record(item.getProjectId(), target(item), Action.change, diff, null, me);
        }
        return detail(item);
    }

    @Transactional
    public int bulkMilestone(String projectKey, List<Long> ids, Long milestoneId, CurrentUser me) {
        Long projectId = access.require(projectKey, Perm.modify_us, me).project().getId();
        Milestone next = milestoneId == null ? null : openMilestone(projectId, milestoneId);
        int changed = 0;
        for (Item item : inOrder(items.findAllById(ids), ids)) {
            if (!item.getProjectId().equals(projectId) || item.getType() != Kind.us
                    || Objects.equals(item.getMilestoneId(), milestoneId)) {
                continue;
            }
            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("milestone",
                    Arrays.asList(milestoneName(item.getMilestoneId()), next == null ? null : next.getName()));
            item.setMilestoneId(milestoneId);
            items.saveAndFlush(item);
            toEnd(item, false);
            syncTasks(item);
            history.record(projectId, target(item), Action.change, diff, null, me);
            changed++;
        }
        return changed;
    }

    @Transactional
    public void delete(Long id, CurrentUser me) {
        Item item = require(id, "delete", me);
        history.record(item.getProjectId(), target(item), Action.delete, Map.of(), null, me);
        List<String> keys = jdbc.sql("""
                select a.storage_key from attachments a join items i on i.id = a.item_id
                where i.id = :id or i.parent_id = :id
                """).param("id", id).query(String.class).list();
        items.delete(item);
        keys.forEach(key -> files.delete(item.getProjectId(), key));
    }

    @Transactional
    public ItemDto watch(Long id, boolean watching, CurrentUser me) {
        Item item = require(id, "view", me);
        String without = "array_remove(watchers, cast(:me as bigint))";
        jdbc.sql("update items set watchers = "
                + (watching ? "array_append(" + without + ", cast(:me as bigint))" : without) + " where id = :id")
                .param("me", me.id()).param("id", id).update();
        entityManager.refresh(item);
        return detail(item);
    }

    public Item require(Long id, String action, CurrentUser me) {
        Item item = items.findById(id).orElseThrow(() -> Problems.notFound("Item não encontrado."));
        access.require(item.getProjectId(), Perm.of(action, item.getType()), me);
        return item;
    }

    private Set<String> apply(Item item, ItemInput in, Set<String> has, Map<String, Object> diff, CurrentUser me) {
        Long projectId = item.getProjectId();
        Kind kind = item.getType();
        Set<String> moved = new LinkedHashSet<>();
        if (has.contains("subject")) {
            String subject = text("subject", in.subject(), 500, true);
            diff(diff, "subject", item.getSubject(), subject);
            item.setSubject(subject);
        }
        if (has.contains("description")) {
            String description = text("description", in.description(), 100_000, false);
            diff(diff, "description", item.getDescription(), description);
            item.setDescription(description);
        }
        if (has.contains("statusId")) {
            if (in.statusId() == null) {
                throw Problems.badRequest("statusId não pode ser nulo.");
            }
            Status status = statuses.findById(in.statusId())
                    .filter(s -> s.getProjectId().equals(projectId) && s.getKind() == kind)
                    .orElseThrow(() -> Problems.badRequest("statusId não é um status deste projeto para este tipo de item."));
            if (changeStatus(item, status, diff)) {
                moved.add("board");
            }
        }
        if (has.contains("parentId") && !Objects.equals(in.parentId(), item.getParentId())) {
            Item parent = in.parentId() == null ? null
                    : items.findById(in.parentId())
                            .filter(p -> p.getProjectId().equals(projectId) && p.getType() == Kind.us)
                            .orElseThrow(() -> Problems.badRequest("parentId não é uma história de usuário deste projeto."));
            diff.put("parent", Arrays.asList(parentLabel(item.getParentId()), parent == null ? null : label(parent)));
            item.setParentId(in.parentId());
            moved.add("board");
            moved.add("list");
        }
        boolean follows = kind == Kind.task && item.getParentId() != null;
        Long milestoneId = follows ? items.findById(item.getParentId()).map(Item::getMilestoneId).orElse(null)
                : has.contains("milestoneId") ? in.milestoneId() : item.getMilestoneId();
        if (!Objects.equals(milestoneId, item.getMilestoneId())) {
            Milestone next = milestoneId == null ? null
                    : follows ? milestones.findById(milestoneId).orElse(null) : openMilestone(projectId, milestoneId);
            diff.put("milestone",
                    Arrays.asList(milestoneName(item.getMilestoneId()), next == null ? null : next.getName()));
            item.setMilestoneId(milestoneId);
            moved.add("list");
            if (kind == Kind.task) {
                moved.add("board");
            }
        }
        if (has.contains("points")) {
            BigDecimal points = in.points() == null ? null : in.points().setScale(1, RoundingMode.HALF_UP);
            if (points != null && (points.signum() < 0 || points.compareTo(MAX_POINTS) > 0)) {
                throw Problems.badRequest("points precisa estar entre 0 e " + MAX_POINTS + ".");
            }
            boolean same = points == null ? item.getPoints() == null
                    : item.getPoints() != null && points.compareTo(item.getPoints()) == 0;
            if (!same) {
                diff.put("points", Arrays.asList(item.getPoints(), points));
                item.setPoints(points);
            }
        }
        if (has.contains("assignedTo") || has.contains("assignedUsers")) {
            List<Long> next = has.contains("assignedUsers")
                    ? in.assignedUsers() == null ? List.of() : in.assignedUsers().stream().distinct().toList()
                    : in.assignedTo() == null ? List.of() : List.of(in.assignedTo());
            List<Long> previous = List.of(item.getAssignedUsers());
            if (!next.equals(previous)) {
                if (next.stream().anyMatch(Objects::isNull) || next.size() > 20) {
                    throw Problems.badRequest("assignedUsers: no máximo 20 ids de membros do projeto.");
                }
                List<Long> added = next.stream().filter(userId -> !previous.contains(userId)).toList();
                added.forEach(userId -> requireMember(projectId, userId));
                diff.put("assignedTo", Arrays.asList(names(previous), names(next)));
                item.setAssignedUsers(next.toArray(Long[]::new));
            }
        }
        if (has.contains("tags")) {
            String[] tags = tags(in.tags());
            if (!Arrays.equals(tags, item.getTags())) {
                diff.put("tags", Arrays.asList(List.of(item.getTags()), List.of(tags)));
                item.setTags(tags);
            }
        }
        if (has.contains("tagsColors") && in.tagsColors() != null) {
            colorNewTags(projectId, item.getTags(), in.tagsColors());
        }
        if (has.contains("dueDate")) {
            diff(diff, "dueDate", string(item.getDueDate()), string(in.dueDate()));
            item.setDueDate(in.dueDate());
        }
        if (has.contains("typeId")) {
            item.setTypeId(choice(item, Choice.Kind.issue_type, "type", item.getTypeId(), in.typeId(), diff));
        }
        if (has.contains("severityId")) {
            item.setSeverityId(choice(item, Choice.Kind.severity, "severity", item.getSeverityId(), in.severityId(), diff));
        }
        if (has.contains("priorityId")) {
            item.setPriorityId(choice(item, Choice.Kind.priority, "priority", item.getPriorityId(), in.priorityId(), diff));
        }
        if (has.contains("isBlocked")) {
            boolean blocked = Boolean.TRUE.equals(in.isBlocked());
            diff(diff, "isBlocked", item.isBlocked(), blocked);
            item.setBlocked(blocked);
            if (!blocked && !has.contains("blockedNote")) {
                item.setBlockedNote("");
            }
        }
        if (has.contains("blockedNote")) {
            String note = text("blockedNote", in.blockedNote(), 1000, false);
            diff(diff, "blockedNote", item.getBlockedNote(), note);
            item.setBlockedNote(note);
        }
        if (has.contains("teamRequirement")) {
            boolean value = Boolean.TRUE.equals(in.teamRequirement());
            diff(diff, "teamRequirement", item.isTeamRequirement(), value);
            item.setTeamRequirement(value);
        }
        if (has.contains("clientRequirement")) {
            boolean value = Boolean.TRUE.equals(in.clientRequirement());
            diff(diff, "clientRequirement", item.isClientRequirement(), value);
            item.setClientRequirement(value);
        }
        if (has.contains("isIocaine")) {
            boolean value = Boolean.TRUE.equals(in.isIocaine());
            diff(diff, "isIocaine", item.isIocaine(), value);
            item.setIocaine(value);
        }
        return moved;
    }

    private boolean changeStatus(Item item, Status next, Map<String, Object> diff) {
        if (next.getId().equals(item.getStatusId())) {
            return false;
        }
        String previous = item.getStatusId() == null ? null
                : statuses.findById(item.getStatusId()).map(Status::getName).orElse(null);
        diff.put("status", Arrays.asList(previous, next.getName()));
        item.setStatusId(next.getId());
        if (!next.isClosed()) {
            item.setFinishedAt(null);
        } else if (item.getFinishedAt() == null) {
            item.setFinishedAt(Instant.now());
        }
        return true;
    }

    private Long choice(Item item, Choice.Kind kind, String field, Long previous, Long next, Map<String, Object> diff) {
        if (Objects.equals(previous, next)) {
            return previous;
        }
        String nextName = next == null ? null
                : choices.findById(next).filter(c -> c.getProjectId().equals(item.getProjectId()) && c.getKind() == kind)
                        .map(Choice::getName)
                        .orElseThrow(() -> Problems.badRequest(field + "Id não é um valor deste projeto."));
        diff.put(field, Arrays.asList(previous == null ? null : choices.findById(previous).map(Choice::getName).orElse(null),
                nextName));
        return next;
    }

    private void colorNewTags(Long projectId, String[] tags, Map<String, String> colors) {
        Project project = projects.findById(projectId).orElseThrow();
        Map<String, String> known = new HashMap<>(project.getTagsColors());
        boolean changed = false;
        for (String tag : tags) {
            String color = colors.get(tag);
            if (color == null || known.containsKey(tag)) {
                continue;
            }
            if (!color.matches("#[0-9A-Fa-f]{6}")) {
                throw Problems.badRequest("tagsColors: use {\"tag\": \"#RRGGBB\"}.");
            }
            known.put(tag, color.toLowerCase(Locale.ROOT));
            changed = true;
        }
        if (changed) {
            project.setTagsColors(known);
            projects.save(project);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> neighbors(Long id, CurrentUser me) {
        Item item = require(id, "view", me);
        String scope = "project_id = :project and type = :type and milestone_id is not distinct from cast(:milestone as bigint)";
        Map<String, Object> result = new HashMap<>();
        result.put("previous", jdbc.sql("select max(ref) from items where " + scope + " and ref < :ref")
                .param("project", item.getProjectId()).param("type", item.getType().name())
                .param("milestone", item.getMilestoneId()).param("ref", item.getRef()).query(Integer.class).optional()
                .orElse(null));
        result.put("next", jdbc.sql("select min(ref) from items where " + scope + " and ref > :ref")
                .param("project", item.getProjectId()).param("type", item.getType().name())
                .param("milestone", item.getMilestoneId()).param("ref", item.getRef()).query(Integer.class).optional()
                .orElse(null));
        return result;
    }

    private void defaultChoices(Item item) {
        Map<Choice.Kind, List<Choice>> lists = choices.findByProjectIdOrderByKindAscSortOrderAscIdAsc(item.getProjectId())
                .stream().collect(Collectors.groupingBy(Choice::getKind));
        Function<Choice.Kind, Long> pick = kind -> {
            List<Choice> list = lists.getOrDefault(kind, List.of());
            return list.stream().filter(c -> c.getName().equalsIgnoreCase("Normal")).findFirst()
                    .or(() -> list.stream().findFirst()).map(Choice::getId).orElse(null);
        };
        if (item.getTypeId() == null) {
            item.setTypeId(pick.apply(Choice.Kind.issue_type));
        }
        if (item.getSeverityId() == null) {
            item.setSeverityId(pick.apply(Choice.Kind.severity));
        }
        if (item.getPriorityId() == null) {
            item.setPriorityId(pick.apply(Choice.Kind.priority));
        }
    }

    private Status defaultStatus(Long projectId, Kind kind) {
        List<Status> list = statuses.findByProjectIdAndKindOrderBySortOrder(projectId, kind);
        return list.stream().filter(s -> !s.isClosed()).findFirst().orElseGet(list::getFirst);
    }

    private Milestone openMilestone(Long projectId, Long milestoneId) {
        Milestone milestone = milestones.findById(milestoneId).filter(m -> m.getProjectId().equals(projectId))
                .orElseThrow(() -> Problems.badRequest("milestoneId não é uma sprint deste projeto."));
        if (milestone.isClosed()) {
            throw Problems.badRequest("A sprint \"" + milestone.getName() + "\" está fechada.");
        }
        return milestone;
    }

    private void requireMember(Long projectId, Long userId) {
        if (memberships.findByProjectIdAndUserId(projectId, userId).isEmpty()) {
            throw Problems.badRequest("O responsável precisa ser membro do projeto.");
        }
    }

    private void writeWatchers(Item item, List<Long> watchers) {
        List<Long> ids = watchers == null ? List.of() : watchers.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.size() > 50) {
            throw Problems.badRequest("watchers: no máximo 50 pessoas.");
        }
        ids.forEach(userId -> requireMember(item.getProjectId(), userId));
        jdbcTemplate.update("update items set watchers = ? where id = ?", ps -> {
            ps.setArray(1, ps.getConnection().createArrayOf("bigint", ids.toArray()));
            ps.setLong(2, item.getId());
        });
    }

    private void syncTasks(Item item) {
        if (item.getType() == Kind.us) {
            jdbc.sql("""
                    update items set milestone_id = cast(:milestone as bigint)
                    where parent_id = :id and milestone_id is distinct from cast(:milestone as bigint)
                    """).param("milestone", item.getMilestoneId()).param("id", item.getId()).update();
        }
    }

    private final class Scoped {

        private final StringBuilder sql;
        private final Map<String, Object> params = new HashMap<>();

        Scoped(String prefix) {
            this.sql = new StringBuilder(prefix);
        }

        Scoped sql(String more) {
            sql.append(more);
            return this;
        }

        Scoped param(String name, Object value) {
            params.put(name, value);
            return this;
        }

        List<Long> ids() {
            return jdbc.sql(sql.toString()).params(params).query(Long.class).list();
        }

        Integer number() {
            return jdbc.sql(sql.toString()).params(params).query(Integer.class).single();
        }

        void update() {
            jdbc.sql(sql.toString()).params(params).update();
        }
    }

    private Scoped scoped(String prefix, Item item, boolean board) {
        Scoped scoped = new Scoped(prefix).sql("project_id = :project and type = :type")
                .param("project", item.getProjectId()).param("type", item.getType().name());
        if (board) {
            scoped.sql(" and status_id = :status").param("status", item.getStatusId());
        }
        boolean task = item.getType() == Kind.task;
        if (task) {
            scoped.sql(" and parent_id is not distinct from cast(:parent as bigint)").param("parent", item.getParentId());
        }
        if (task ? board || item.getParentId() == null : !board) {
            scoped.sql(" and milestone_id is not distinct from cast(:milestone as bigint)")
                    .param("milestone", item.getMilestoneId());
        }
        return scoped;
    }

    private int edge(Item item, boolean board, boolean top) {
        String column = board ? "board_order" : "list_order";
        return scoped("select " + (top ? "coalesce(min(" + column + "), 1) - 1" : "coalesce(max(" + column + "), 0) + 1")
                + " from items where ", item, board).number();
    }

    private void toEnd(Item item, boolean board) {
        String column = board ? "board_order" : "list_order";
        int last = scoped("select coalesce(max(" + column + "), 0) + 1 from items where ", item, board)
                .sql(" and id <> :id").param("id", item.getId()).number();
        jdbc.sql("update items set " + column + " = :value where id = :id").param("value", last)
                .param("id", item.getId()).update();
    }

    private ItemInput input(Map<String, Object> body, Kind kind) {
        Set<String> allowed = ItemInput.FIELDS.get(kind);
        List<String> unknown = body.keySet().stream().filter(k -> !allowed.contains(k)).sorted().toList();
        if (!unknown.isEmpty()) {
            throw Problems.badRequest("Campos que não existem para " + kind.label.toLowerCase(Locale.ROOT) + ": "
                    + String.join(", ", unknown) + ". Aceitos: "
                    + allowed.stream().sorted().collect(Collectors.joining(", ")) + ".");
        }
        try {
            return mapper.convertValue(body, ItemInput.class);
        } catch (JacksonException | IllegalArgumentException e) {
            throw Problems.badRequest("Corpo inválido: confira os tipos dos campos.");
        }
    }

    private int nextRef(Long projectId) {
        return jdbc.sql("update projects set last_ref = last_ref + 1 where id = :id returning last_ref")
                .param("id", projectId).query(Integer.class).single();
    }

    private Long assigneeFilter(String value, CurrentUser me) {
        if (value.equals("none")) {
            return null;
        }
        if (value.equals("me")) {
            return me.id();
        }
        if (value.matches("\\d{1,18}")) {
            return Long.valueOf(value);
        }
        return users.findByUsername(value.toLowerCase(Locale.ROOT)).map(User::getId).orElse(-1L);
    }

    List<Long> searchIds(Kind kind, Long projectId, String text) {
        String term = text.trim();
        String digits = term.startsWith("#") ? term.substring(1) : term;
        int ref = digits.matches("\\d{1,9}") ? Integer.parseInt(digits) : -1;
        String like = "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbc.sql("""
                select id from items
                where project_id = :project and type = :type
                  and (search_vector @@ websearch_to_tsquery('portuguese', :term) or subject ilike :like or ref = :ref)
                order by (ref = :ref) desc, ts_rank(search_vector, websearch_to_tsquery('portuguese', :term)) desc,
                         id desc
                limit 500
                """)
                .param("project", projectId).param("type", kind.name()).param("term", term).param("like", like)
                .param("ref", ref).query(Long.class).list();
    }

    private String names(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return null;
        }
        Map<Long, UserRef> refs = userRefs(userIds);
        return userIds.stream().map(refs::get).filter(Objects::nonNull).map(UserRef::fullName)
                .collect(Collectors.joining(", "));
    }

    Map<Long, UserRef> userRefs(Collection<Long> ids) {
        return users.findAllById(ids.stream().filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, UserRef::of));
    }

    private String milestoneName(Long milestoneId) {
        return milestoneId == null ? null : milestones.findById(milestoneId).map(Milestone::getName).orElse(null);
    }

    private String parentLabel(Long parentId) {
        return parentId == null ? null : items.findById(parentId).map(ItemService::label).orElse(null);
    }

    private static String label(Item item) {
        return "#" + item.getRef() + " " + item.getSubject();
    }

    private static String string(LocalDate date) {
        return date == null ? null : date.toString();
    }

    static Target target(Item item) {
        return new Target(item.getType().name(), item.getId(), item.getRef(), item.getSubject());
    }

    private static void diff(Map<String, Object> diff, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            diff.put(field, Arrays.asList(before, after));
        }
    }

    private static String text(String field, String value, int max, boolean required) {
        String text = value == null ? "" : required ? value.trim() : value;
        if (required && text.isEmpty()) {
            throw Problems.badRequest(field + " é obrigatório.");
        }
        if (text.length() > max) {
            throw Problems.badRequest(field + " pode ter no máximo " + max + " caracteres.");
        }
        return text;
    }

    private static String[] tags(List<String> tags) {
        if (tags == null) {
            return new String[0];
        }
        List<String> clean = tags.stream().filter(Objects::nonNull).map(String::trim).filter(t -> !t.isEmpty())
                .distinct().toList();
        if (clean.size() > 20 || clean.stream().anyMatch(t -> t.length() > 50)) {
            throw Problems.badRequest("tags: no máximo 20 tags de até 50 caracteres.");
        }
        return clean.toArray(String[]::new);
    }

    private ItemDto detail(Item item) {
        ItemDto dto = toDtos(List.of(item)).getFirst();
        return item.getType() == Kind.us ? dto.withTasks(toDtos(items.findByParentIdOrderByListOrderAscIdAsc(item.getId())))
                : dto;
    }

    List<ItemDto> toDtos(List<Item> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(Item::getId).toList();
        Map<Long, StatusRef> statusRefs = statuses.findAllById(list.stream().map(Item::getStatusId).distinct().toList())
                .stream().collect(Collectors.toMap(Status::getId, StatusRef::of));
        Map<Long, UserRef> userRefs = userRefs(list.stream().flatMap(i -> Stream.concat(Stream.of(i.getCreatedBy()),
                Stream.concat(Arrays.stream(i.getAssignedUsers()), Arrays.stream(i.getWatchers())))).toList());
        Map<Long, MilestoneRef> milestoneRefs = milestones
                .findAllById(list.stream().map(Item::getMilestoneId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Milestone::getId, m -> new MilestoneRef(m.getId(), m.getName(), m.getSlug())));
        Map<Long, ParentRef> parentRefs = items
                .findAllById(list.stream().map(Item::getParentId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Item::getId, p -> new ParentRef(p.getId(), p.getRef(), p.getSubject())));
        Map<Long, ChoiceRef> choiceRefs = choices
                .findAllById(list.stream().flatMap(i -> Stream.of(i.getTypeId(), i.getSeverityId(), i.getPriorityId()))
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Choice::getId, c -> new ChoiceRef(c.getId(), c.getName(), c.getColor())));
        Map<Long, long[]> taskCounts = new HashMap<>();
        jdbc.sql("""
                select t.parent_id, count(*) as total, count(*) filter (where s.is_closed) as done
                from items t join statuses s on s.id = t.status_id
                where t.parent_id in (:ids) group by t.parent_id
                """).param("ids", ids).query(rs -> {
            taskCounts.put(rs.getLong("parent_id"), new long[] { rs.getLong("total"), rs.getLong("done") });
        });
        Map<Long, Long> commentCounts = counts("select item_id, count(*) from comments where item_id in (:ids) and deleted_at is null group by item_id", ids);
        Map<Long, Long> attachmentCounts = counts("select item_id, count(*) from attachments where item_id in (:ids) group by item_id", ids);
        long[] none = { 0, 0 };
        return list.stream().map(i -> {
            List<UserRef> assigned = refs(i.getAssignedUsers(), userRefs);
            return new ItemDto(i.getId(), i.getProjectId(), i.getType(), i.getRef(), i.getSubject(),
                    i.getDescription(), statusRefs.get(i.getStatusId()), milestoneRefs.get(i.getMilestoneId()),
                    parentRefs.get(i.getParentId()), i.getPoints(), assigned.isEmpty() ? null : assigned.getFirst(),
                    assigned, refs(i.getWatchers(), userRefs), userRefs.get(i.getCreatedBy()), List.of(i.getTags()),
                    i.getDueDate(), choiceRefs.get(i.getTypeId()), choiceRefs.get(i.getSeverityId()),
                    choiceRefs.get(i.getPriorityId()), i.getBoardOrder(), i.getListOrder(), i.isBlocked(),
                    i.getBlockedNote(), i.isTeamRequirement(), i.isClientRequirement(), i.isIocaine(), i.getVersion(),
                    i.getCreatedAt(), i.getModifiedAt(), i.getFinishedAt(), taskCounts.getOrDefault(i.getId(), none)[0],
                    taskCounts.getOrDefault(i.getId(), none)[1], commentCounts.getOrDefault(i.getId(), 0L),
                    attachmentCounts.getOrDefault(i.getId(), 0L), null);
        }).toList();
    }

    private Map<Long, Long> counts(String sql, List<Long> ids) {
        Map<Long, Long> counts = new HashMap<>();
        jdbc.sql(sql).param("ids", ids).query(rs -> {
            counts.put(rs.getLong(1), rs.getLong(2));
        });
        return counts;
    }

    private static List<UserRef> refs(Long[] ids, Map<Long, UserRef> users) {
        return Arrays.stream(ids).map(users::get).filter(Objects::nonNull).toList();
    }
}
