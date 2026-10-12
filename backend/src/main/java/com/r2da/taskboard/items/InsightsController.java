package com.r2da.taskboard.items;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.r2da.taskboard.auth.CurrentUser;
import com.r2da.taskboard.common.Problems;
import com.r2da.taskboard.projects.Access;
import com.r2da.taskboard.projects.Kind;
import com.r2da.taskboard.projects.Perm;
import com.r2da.taskboard.projects.StatusRef;
import com.r2da.taskboard.users.UserRef;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

@RestController
@RequestMapping("/api/v1/projects/{key}")
public class InsightsController {

    public record SearchResult(List<ItemDto> userstories, List<ItemDto> tasks, List<ItemDto> issues,
            List<String> wikiPages) {
    }

    public record Totals(long open, long closed, long blocked, long overdue, long created7d, long created30d,
            long closed7d, long closed30d, BigDecimal pointsOpen, BigDecimal pointsClosed) {
    }

    public record TaskTotals(long open, long closed, long iocaine) {
    }

    public record ByStatus(StatusRef status, long userstories, BigDecimal points) {
    }

    public record ByAssignee(UserRef user, long open, long closed) {
    }

    public record Stats(Totals totals, TaskTotals tasks, List<ByStatus> byStatus, List<ByAssignee> byAssignee) {
    }

    public record MemberStats(Long userId, long closedBugs, long iocaineTasks, long wikiChanges, long createdBugs,
            long closedTasks) {
    }

    private static final int SEARCH_LIMIT = 50;

    private final Access access;
    private final ItemService service;
    private final ItemRepository items;
    private final JdbcClient jdbc;
    private final ZoneId zone;

    InsightsController(Access access, ItemService service, ItemRepository items, JdbcClient jdbc,
            @Value("${app.time-zone}") ZoneId zone) {
        this.access = access;
        this.service = service;
        this.items = items;
        this.jdbc = jdbc;
        this.zone = zone;
    }

    @GetMapping("/search")
    @Operation(summary = "Busca histórias, tarefas, problemas e páginas da wiki por texto ou #ref",
            description = "Sem texto, devolve os itens alterados mais recentemente. Até 50 de cada tipo.")
    @Transactional(readOnly = true)
    public SearchResult search(@PathVariable String key, @RequestParam(defaultValue = "") String q,
            @AuthenticationPrincipal CurrentUser me) {
        Access.Ctx ctx = access.require(key, Perm.view_project, me);
        Long projectId = ctx.project().getId();
        Map<Kind, List<ItemDto>> found = new HashMap<>();
        for (Kind kind : Kind.values()) {
            if (!ctx.can(Perm.of("view", kind))) {
                found.put(kind, List.of());
                continue;
            }
            List<Long> ids = q.isBlank()
                    ? jdbc.sql("""
                            select id from items where project_id = :project and type = :type
                            order by modified_at desc, id desc limit :limit
                            """).param("project", projectId).param("type", kind.name()).param("limit", SEARCH_LIMIT)
                            .query(Long.class).list()
                    : service.searchIds(kind, projectId, q).stream().limit(SEARCH_LIMIT).toList();
            found.put(kind, service.toDtos(ItemService.inOrder(items.findAllById(ids), ids)));
        }
        List<String> pages = !ctx.can(Perm.view_wiki_pages) ? List.of()
                : jdbc.sql("""
                        select slug from wiki_pages
                        where project_id = :project and (:like = '%%' or slug ilike :like or content ilike :like)
                        order by slug limit :limit
                        """).param("project", projectId).param("like", "%" + like(q.trim()) + "%")
                        .param("limit", SEARCH_LIMIT).query(String.class).list();
        return new SearchResult(found.get(Kind.us), found.get(Kind.task), found.get(Kind.issue), pages);
    }

    private static String like(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @GetMapping("/stats")
    @Operation(summary = "Números do projeto ou de uma sprint")
    public Stats stats(@PathVariable String key,
            @Parameter(description = "Id da sprint, ou \"none\" para o backlog; omitido = projeto inteiro") @RequestParam(required = false) String milestone,
            @AuthenticationPrincipal CurrentUser me) {
        Long projectId = access.require(key, Perm.view_us, me).project().getId();
        String scope = "";
        Long milestoneId = null;
        if (milestone != null && milestone.equals("none")) {
            scope = " and i.milestone_id is null";
        } else if (milestone != null && milestone.matches("\\d{1,18}")) {
            scope = " and i.milestone_id = :milestone";
            milestoneId = Long.valueOf(milestone);
        } else if (milestone != null) {
            throw Problems.badRequest("milestone: use o id da sprint ou \"none\" para o backlog.");
        }
        Map<String, Object> params = new HashMap<>();
        params.put("project", projectId);
        params.put("milestone", milestoneId);
        params.put("today", LocalDate.now(zone));
        Totals totals = jdbc.sql("""
                select count(*) filter (where not s.is_closed) as open,
                       count(*) filter (where s.is_closed) as closed,
                       count(*) filter (where i.is_blocked and not s.is_closed) as blocked,
                       count(*) filter (where i.due_date < :today and not s.is_closed) as overdue,
                       count(*) filter (where i.created_at >= now() - interval '7 days') as created_7d,
                       count(*) filter (where i.created_at >= now() - interval '30 days') as created_30d,
                       count(*) filter (where i.finished_at >= now() - interval '7 days') as closed_7d,
                       count(*) filter (where i.finished_at >= now() - interval '30 days') as closed_30d,
                       coalesce(sum(i.points) filter (where not s.is_closed), 0) as points_open,
                       coalesce(sum(i.points) filter (where s.is_closed), 0) as points_closed
                from items i join statuses s on s.id = i.status_id
                where i.project_id = :project and i.type = 'us'""" + scope)
                .params(params)
                .query((rs, row) -> new Totals(rs.getLong("open"), rs.getLong("closed"), rs.getLong("blocked"),
                        rs.getLong("overdue"), rs.getLong("created_7d"), rs.getLong("created_30d"),
                        rs.getLong("closed_7d"), rs.getLong("closed_30d"), rs.getBigDecimal("points_open"),
                        rs.getBigDecimal("points_closed")))
                .single();
        TaskTotals tasks = jdbc.sql("""
                select count(*) filter (where not s.is_closed) as open,
                       count(*) filter (where s.is_closed) as closed,
                       count(*) filter (where i.is_iocaine) as iocaine
                from items i join statuses s on s.id = i.status_id
                where i.project_id = :project and i.type = 'task'""" + scope)
                .params(params)
                .query((rs, row) -> new TaskTotals(rs.getLong("open"), rs.getLong("closed"), rs.getLong("iocaine")))
                .single();
        List<ByStatus> byStatus = jdbc.sql("""
                select s.id, s.name, s.color, s.is_closed, count(i.id) as userstories,
                       coalesce(sum(i.points), 0) as points
                from statuses s left join items i on i.status_id = s.id""" + scope + """

                where s.project_id = :project and s.kind = 'us'
                group by s.id order by s.sort_order
                """)
                .params(params)
                .query((rs, row) -> new ByStatus(new StatusRef(rs.getLong("id"), rs.getString("name"),
                        rs.getString("color"), rs.getBoolean("is_closed")), rs.getLong("userstories"),
                        rs.getBigDecimal("points")))
                .list();
        record Row(Long userId, long open, long closed) {
        }
        List<Row> rows = jdbc.sql("""
                select a.user_id, count(*) filter (where not s.is_closed) as open,
                       count(*) filter (where s.is_closed) as closed
                from items i
                join statuses s on s.id = i.status_id
                left join lateral unnest(i.assigned_users) as a (user_id) on true
                where i.project_id = :project and i.type = 'us'""" + scope + """

                group by a.user_id order by open desc
                """)
                .params(params)
                .query((rs, row) -> new Row((Long) rs.getObject("user_id"), rs.getLong("open"),
                        rs.getLong("closed")))
                .list();
        Map<Long, UserRef> users = service.userRefs(rows.stream().map(Row::userId).toList());
        List<ByAssignee> byAssignee = new ArrayList<>();
        for (Row row : rows) {
            byAssignee.add(new ByAssignee(users.get(row.userId()), row.open(), row.closed()));
        }
        return new Stats(totals, tasks, byStatus, byAssignee);
    }

    @GetMapping("/member-stats")
    @Operation(summary = "Números de cada membro: problemas fechados e criados, tarefas fechadas, iocaína e edições da wiki")
    public List<MemberStats> memberStats(@PathVariable String key, @AuthenticationPrincipal CurrentUser me) {
        Long projectId = access.require(key, Perm.view_project, me).project().getId();
        return jdbc.sql("""
                select m.user_id,
                       (select count(*) from items i join statuses s on s.id = i.status_id
                        where i.project_id = m.project_id and i.type = 'issue' and s.is_closed
                          and m.user_id = any (i.assigned_users)) as closed_bugs,
                       (select count(*) from items i
                        where i.project_id = m.project_id and i.type = 'task' and i.is_iocaine
                          and m.user_id = any (i.assigned_users)) as iocaine_tasks,
                       (select count(*) from history h
                        where h.project_id = m.project_id and h.target_type = 'wiki' and h.actor_id = m.user_id) as wiki_changes,
                       (select count(*) from items i
                        where i.project_id = m.project_id and i.type = 'issue' and i.created_by = m.user_id) as created_bugs,
                       (select count(*) from items i join statuses s on s.id = i.status_id
                        where i.project_id = m.project_id and i.type = 'task' and s.is_closed
                          and m.user_id = any (i.assigned_users)) as closed_tasks
                from memberships m where m.project_id = :project
                """)
                .param("project", projectId)
                .query((rs, row) -> new MemberStats(rs.getLong("user_id"), rs.getLong("closed_bugs"),
                        rs.getLong("iocaine_tasks"), rs.getLong("wiki_changes"), rs.getLong("created_bugs"),
                        rs.getLong("closed_tasks")))
                .list();
    }
}
