package com.r2da.taskboard.projects;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.r2da.taskboard.auth.CurrentUser;
import com.r2da.taskboard.common.Problems;
import com.r2da.taskboard.common.Slugs;
import com.r2da.taskboard.users.User;
import com.r2da.taskboard.users.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProjectService {

    public record StatusDto(Long id, Kind kind, String name, String color, int sortOrder, boolean isClosed,
            boolean isArchived, Integer wipLimit) {

        public static StatusDto of(Status s) {
            return new StatusDto(s.getId(), s.getKind(), s.getName(), s.getColor(), s.getSortOrder(), s.isClosed(),
                    s.isArchived(), s.getWipLimit());
        }
    }

    public record ChoiceDto(Long id, Choice.Kind kind, String name, String color, BigDecimal value, int sortOrder) {

        public static ChoiceDto of(Choice c) {
            return new ChoiceDto(c.getId(), c.getKind(), c.getName(), c.getColor(), c.getValue(), c.getSortOrder());
        }
    }

    public record RoleDto(Long id, String name, int sortOrder, Set<Perm> permissions, boolean followsAll,
            long membersCount) {
    }

    public record MemberDto(Long userId, String username, String fullName, String email, Long roleId,
            String roleName, boolean isAdmin, boolean isOwner) {
    }

    public record MilestoneDto(Long id, String name, String slug, LocalDate estimatedStart,
            LocalDate estimatedFinish, boolean isClosed, long usOpen, long usClosed, BigDecimal pointsOpen,
            BigDecimal pointsClosed) {
    }

    public record My(boolean isMember, boolean isAdmin, boolean isOwner, Long roleId, String roleName,
            Set<Perm> permissions, boolean liked, boolean watching) {
    }

    public record ProjectDto(Long id, String slug, String name, String description, boolean isPrivate,
            boolean isArchived, Long ownerId, Map<String, String> tagsColors, Instant createdAt, Instant modifiedAt,
            boolean isBacklogActivated, boolean isKanbanActivated, boolean isIssuesActivated,
            boolean isWikiActivated, long likes, long watchers, long membersCount, My my,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<StatusDto> statuses,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<MemberDto> members,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<MilestoneDto> milestones,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<RoleDto> roles,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<ChoiceDto> points,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<ChoiceDto> issueTypes,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<ChoiceDto> severities,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<ChoiceDto> priorities) {
    }

    private record StatusSeed(String name, String color, boolean closed, boolean archived) {
    }

    private record ChoiceSeed(Choice.Kind kind, String name, String color, String value) {
    }

    private record RoleSeed(String name, Set<Perm> permissions, boolean followsAll) {
    }

    private record MilestoneStats(long open, long closed, BigDecimal pointsOpen, BigDecimal pointsClosed) {

        static final MilestoneStats EMPTY = new MilestoneStats(0, 0, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private record Counts(long members, long likes, long watchers, boolean liked, boolean watching) {

        static final Counts EMPTY = new Counts(0, 0, 0, false, false);
    }

    private static final List<StatusSeed> STATUSES = List.of(
            new StatusSeed("A fazer", "#1f7d74", false, false),
            new StatusSeed("Em andamento", "#97c65c", false, false),
            new StatusSeed("Pronto para homologar", "#8c52ff", false, false),
            new StatusSeed("Em homologação", "#c6a9ff", false, false),
            new StatusSeed("Aguardando autorização produção", "#d3ac50", false, false),
            new StatusSeed("Subir em produção", "#137acf", false, false),
            new StatusSeed("Validando em produção", "#9f760a", false, false),
            new StatusSeed("Concluído", "#09b260", true, false),
            new StatusSeed("Precisa de informação", "#990000", false, false),
            new StatusSeed("Arquivado", "#aeb5b9", true, true));

    private static final List<ChoiceSeed> CHOICES = List.of(
            new ChoiceSeed(Choice.Kind.points, "?", "#70728f", null),
            new ChoiceSeed(Choice.Kind.points, "0", "#70728f", "0"),
            new ChoiceSeed(Choice.Kind.points, "1/2", "#70728f", "0.5"),
            new ChoiceSeed(Choice.Kind.points, "1", "#70728f", "1"),
            new ChoiceSeed(Choice.Kind.points, "2", "#70728f", "2"),
            new ChoiceSeed(Choice.Kind.points, "3", "#70728f", "3"),
            new ChoiceSeed(Choice.Kind.points, "5", "#70728f", "5"),
            new ChoiceSeed(Choice.Kind.points, "8", "#70728f", "8"),
            new ChoiceSeed(Choice.Kind.points, "10", "#70728f", "10"),
            new ChoiceSeed(Choice.Kind.points, "13", "#70728f", "13"),
            new ChoiceSeed(Choice.Kind.points, "20", "#70728f", "20"),
            new ChoiceSeed(Choice.Kind.points, "40", "#70728f", "40"),
            new ChoiceSeed(Choice.Kind.issue_type, "Bug", "#e44057", null),
            new ChoiceSeed(Choice.Kind.issue_type, "Pergunta", "#5178d3", null),
            new ChoiceSeed(Choice.Kind.issue_type, "Melhoria", "#40e4ce", null),
            new ChoiceSeed(Choice.Kind.severity, "Baixa", "#70728f", null),
            new ChoiceSeed(Choice.Kind.severity, "Normal", "#40e47c", null),
            new ChoiceSeed(Choice.Kind.severity, "Importante", "#e4a240", null),
            new ChoiceSeed(Choice.Kind.severity, "Crítica", "#d31313", null),
            new ChoiceSeed(Choice.Kind.priority, "Baixa", "#a8e440", null),
            new ChoiceSeed(Choice.Kind.priority, "Normal", "#e4ce40", null),
            new ChoiceSeed(Choice.Kind.priority, "Alta", "#e47c40", null));

    private static final Set<Perm> TEAM = without(Perm.ALL, Perm.add_milestone, Perm.modify_milestone,
            Perm.delete_milestone, Perm.add_wiki_page, Perm.modify_wiki_page, Perm.delete_wiki_page);

    private final ProjectRepository projects;
    private final MembershipRepository memberships;
    private final StatusRepository statuses;
    private final MilestoneRepository milestones;
    private final RoleRepository roles;
    private final ChoiceRepository choices;
    private final UserRepository users;
    private final Access access;
    private final JdbcClient jdbc;

    private static Set<Perm> without(Set<Perm> all, Perm... removed) {
        Set<Perm> gone = Set.of(removed);
        return all.stream().filter(p -> !gone.contains(p)).collect(Collectors.toUnmodifiableSet());
    }

    private static List<RoleSeed> roleSeeds(boolean kanban) {
        return List.of(
                new RoleSeed("Product Owner", Perm.ALL, true),
                new RoleSeed("R2DA", kanban ? TEAM
                        : without(TEAM, Perm.add_us, Perm.modify_us, Perm.comment_us, Perm.delete_us), false),
                new RoleSeed("Líder de Projeto", Perm.ALL, true),
                new RoleSeed("Consulta", Perm.READ, false));
    }

    @Transactional
    public Project create(String name, String description, Boolean isPrivate, String template, CurrentUser me) {
        if (template != null && !template.equals("scrum") && !template.equals("kanban")) {
            throw Problems.badRequest("template: use \"scrum\" ou \"kanban\".");
        }
        String base = Slugs.slugify(name);
        String slug = base;
        for (int i = 2; slug.equals("new") || projects.existsBySlug(slug); i++) {
            slug = base + "-" + i;
        }
        Project project = new Project();
        project.setSlug(slug);
        project.setName(name.trim());
        project.setDescription(description == null ? "" : description);
        project.setOwnerId(me.id());
        project.setPrivate(!Boolean.FALSE.equals(isPrivate));
        project.setBacklogActivated(!"kanban".equals(template));
        project.setKanbanActivated(!"scrum".equals(template));
        projects.save(project);
        Long id = project.getId();

        Long ownerRole = null;
        int order = 0;
        for (RoleSeed seed : roleSeeds(project.isKanbanActivated())) {
            Role role = new Role();
            role.setProjectId(id);
            role.setName(seed.name());
            role.setSortOrder(++order);
            role.setPermissions(seed.permissions().stream().sorted().map(Perm::name).toArray(String[]::new));
            role.setFollowsAll(seed.followsAll());
            roles.save(role);
            if (ownerRole == null) {
                ownerRole = role.getId();
            }
        }
        Membership owner = new Membership();
        owner.setProjectId(id);
        owner.setUserId(me.id());
        owner.setRoleId(ownerRole);
        owner.setAdmin(true);
        memberships.save(owner);

        for (Kind kind : Kind.values()) {
            order = 0;
            for (StatusSeed seed : STATUSES) {
                Status status = new Status();
                status.setProjectId(id);
                status.setKind(kind);
                status.setName(seed.name());
                status.setColor(seed.color());
                status.setClosed(seed.closed());
                status.setArchived(seed.archived() && kind == Kind.us);
                status.setSortOrder(++order);
                statuses.save(status);
            }
        }
        order = 0;
        for (ChoiceSeed seed : CHOICES) {
            Choice choice = new Choice();
            choice.setProjectId(id);
            choice.setKind(seed.kind());
            choice.setName(seed.name());
            choice.setColor(seed.color());
            choice.setValue(seed.value() == null ? null : new BigDecimal(seed.value()));
            choice.setSortOrder(++order);
            choices.save(choice);
        }
        return project;
    }

    @Transactional(readOnly = true)
    public List<ProjectDto> visibleTo(CurrentUser me, boolean archived) {
        Map<Long, Integer> order = memberships.findByUserId(me.id()).stream()
                .collect(Collectors.toMap(Membership::getProjectId, Membership::getUserOrder));
        List<Project> list = projects.visibleTo(me.id(), me.superuser(), archived).stream()
                .sorted(Comparator.comparingInt((Project p) -> order.containsKey(p.getId()) ? 0 : 1)
                        .thenComparingInt(p -> order.getOrDefault(p.getId(), 0))
                        .thenComparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        Map<Long, Counts> counts = counts(list.stream().map(Project::getId).toList(), me);
        return list.stream()
                .map(p -> dto(access.resolve(p, me), counts.getOrDefault(p.getId(), Counts.EMPTY), false))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectDto summary(Access.Ctx ctx, CurrentUser me) {
        Long id = ctx.project().getId();
        return dto(ctx, counts(List.of(id), me).getOrDefault(id, Counts.EMPTY), false);
    }

    @Transactional(readOnly = true)
    public ProjectDto detail(Access.Ctx ctx, CurrentUser me) {
        Long id = ctx.project().getId();
        return dto(ctx, counts(List.of(id), me).getOrDefault(id, Counts.EMPTY), true);
    }

    private ProjectDto dto(Access.Ctx ctx, Counts counts, boolean detail) {
        Project p = ctx.project();
        Long id = p.getId();
        Role role = ctx.role();
        My my = new My(ctx.member(), ctx.admin(), ctx.owner(), role == null ? null : role.getId(),
                role == null ? null : role.getName(), ctx.perms(), counts.liked(), counts.watching());
        Map<Choice.Kind, List<ChoiceDto>> lists = !detail ? Map.of()
                : choices.findByProjectIdOrderByKindAscSortOrderAscIdAsc(id).stream().map(ChoiceDto::of)
                        .collect(Collectors.groupingBy(ChoiceDto::kind));
        return new ProjectDto(id, p.getSlug(), p.getName(), p.getDescription(), p.isPrivate(), p.isArchived(),
                p.getOwnerId(), p.getTagsColors(), p.getCreatedAt(), p.getModifiedAt(), p.isBacklogActivated(),
                p.isKanbanActivated(), p.isIssuesActivated(), p.isWikiActivated(), counts.likes(),
                counts.watchers(), counts.members(), my,
                detail ? statuses(id) : null,
                detail ? members(p) : null,
                detail ? milestones(id) : null,
                detail ? roles(id) : null,
                detail ? lists.getOrDefault(Choice.Kind.points, List.of()) : null,
                detail ? lists.getOrDefault(Choice.Kind.issue_type, List.of()) : null,
                detail ? lists.getOrDefault(Choice.Kind.severity, List.of()) : null,
                detail ? lists.getOrDefault(Choice.Kind.priority, List.of()) : null);
    }

    private Map<Long, Counts> counts(List<Long> projectIds, CurrentUser me) {
        Map<Long, Counts> counts = new HashMap<>();
        if (projectIds.isEmpty()) {
            return counts;
        }
        jdbc.sql("""
                select p.id,
                       (select count(*) from memberships m where m.project_id = p.id) as members,
                       (select count(*) from project_fans f where f.project_id = p.id and f.kind = 'like') as likes,
                       (select count(*) from project_fans f where f.project_id = p.id and f.kind = 'watch') as watchers,
                       exists (select 1 from project_fans f
                               where f.project_id = p.id and f.user_id = :me and f.kind = 'like') as liked,
                       exists (select 1 from project_fans f
                               where f.project_id = p.id and f.user_id = :me and f.kind = 'watch') as watching
                from projects p where p.id in (:ids)
                """).param("me", me.id()).param("ids", projectIds).query(rs -> {
            counts.put(rs.getLong("id"), new Counts(rs.getLong("members"), rs.getLong("likes"),
                    rs.getLong("watchers"), rs.getBoolean("liked"), rs.getBoolean("watching")));
        });
        return counts;
    }

    public List<StatusDto> statuses(Long projectId) {
        return statuses.findByProjectIdOrderByKindAscSortOrderAsc(projectId).stream().map(StatusDto::of).toList();
    }

    public List<RoleDto> roles(Long projectId) {
        Map<Long, Long> members = memberships.findByProjectId(projectId).stream()
                .collect(Collectors.groupingBy(Membership::getRoleId, Collectors.counting()));
        return roles.findByProjectIdOrderBySortOrderAscIdAsc(projectId).stream()
                .map(r -> new RoleDto(r.getId(), r.getName(), r.getSortOrder(), Perm.parse(r.getPermissions()),
                        r.isFollowsAll(),
                        members.getOrDefault(r.getId(), 0L)))
                .toList();
    }

    public List<MemberDto> members(Project project) {
        List<Membership> list = memberships.findByProjectId(project.getId());
        Map<Long, User> byId = users.findAllById(list.stream().map(Membership::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, String> roleNames = roles.findByProjectIdOrderBySortOrderAscIdAsc(project.getId()).stream()
                .collect(Collectors.toMap(Role::getId, Role::getName));
        return list.stream()
                .map(m -> member(project, m, byId.get(m.getUserId()), roleNames.get(m.getRoleId())))
                .sorted((a, b) -> a.fullName().compareToIgnoreCase(b.fullName()))
                .toList();
    }

    public static MemberDto member(Project project, Membership m, User u, String roleName) {
        boolean owner = project.getOwnerId().equals(u.getId());
        return new MemberDto(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), m.getRoleId(), roleName,
                owner || m.isAdmin(), owner);
    }

    public List<MilestoneDto> milestones(Long projectId) {
        Map<Long, MilestoneStats> stats = new HashMap<>();
        jdbc.sql("""
                select i.milestone_id,
                       count(*) filter (where not s.is_closed) as open,
                       count(*) filter (where s.is_closed) as closed,
                       coalesce(sum(i.points) filter (where not s.is_closed), 0) as points_open,
                       coalesce(sum(i.points) filter (where s.is_closed), 0) as points_closed
                from items i join statuses s on s.id = i.status_id
                where i.project_id = :project and i.type = 'us' and i.milestone_id is not null
                group by i.milestone_id
                """)
                .param("project", projectId)
                .query(rs -> {
                    stats.put(rs.getLong("milestone_id"), new MilestoneStats(rs.getLong("open"),
                            rs.getLong("closed"), rs.getBigDecimal("points_open"), rs.getBigDecimal("points_closed")));
                });
        return milestones.findByProjectIdOrderByEstimatedStartDescIdDesc(projectId).stream()
                .map(m -> milestone(m, stats.getOrDefault(m.getId(), MilestoneStats.EMPTY)))
                .toList();
    }

    public MilestoneDto milestone(Milestone m) {
        return milestones(m.getProjectId()).stream().filter(dto -> dto.id().equals(m.getId())).findFirst()
                .orElseGet(() -> milestone(m, MilestoneStats.EMPTY));
    }

    private static MilestoneDto milestone(Milestone m, MilestoneStats s) {
        return new MilestoneDto(m.getId(), m.getName(), m.getSlug(), m.getEstimatedStart(), m.getEstimatedFinish(),
                m.isClosed(), s.open(), s.closed(), s.pointsOpen(), s.pointsClosed());
    }
}
