package com.r2da.taskboard.users;

import java.util.List;
import java.util.Locale;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.r2da.taskboard.auth.CurrentUser;
import com.r2da.taskboard.common.PageDto;
import com.r2da.taskboard.common.Problems;
import com.r2da.taskboard.history.HistoryService;
import com.r2da.taskboard.history.HistoryService.HistoryDto;
import com.r2da.taskboard.history.HistoryService.ProjectRef;
import com.r2da.taskboard.projects.Kind;
import com.r2da.taskboard.projects.Project;
import com.r2da.taskboard.projects.ProjectRepository;
import com.r2da.taskboard.projects.StatusRef;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProfileController {

    public record DashItem(Long id, Kind type, int ref, String subject, StatusRef status, ProjectRef project) {
    }

    public record Dashboard(List<DashItem> workingOn, List<DashItem> watching) {
    }

    public record Profile(Long id, String username, String fullName, String bio, List<String> roles, long projects,
            long closedUserStories, long contacts) {
    }

    private static final int DASHBOARD_LIMIT = 50;

    private final UserRepository users;
    private final ProjectRepository projects;
    private final HistoryService history;
    private final JdbcClient jdbc;

    @GetMapping("/me/dashboard")
    @Operation(summary = "O que está aberto e atribuído a quem chama, e o que a pessoa observa")
    Dashboard dashboard(@AuthenticationPrincipal CurrentUser me) {
        return new Dashboard(open("i.assigned_users", me), open("i.watchers", me));
    }

    private List<DashItem> open(String column, CurrentUser me) {
        return jdbc.sql("""
                select i.id, i.type, i.ref, i.subject, s.id as status_id, s.name as status_name, s.color,
                       s.is_closed, p.id as project_id, p.slug, p.name as project_name
                from items i
                join statuses s on s.id = i.status_id
                join projects p on p.id = i.project_id
                where :me = any (""" + column + """
                ) and not s.is_closed and not p.is_archived
                  and exists (select 1 from memberships m where m.project_id = p.id and m.user_id = :me)
                order by i.modified_at desc, i.id desc limit :limit
                """)
                .param("me", me.id()).param("limit", DASHBOARD_LIMIT)
                .query((rs, row) -> new DashItem(rs.getLong("id"), Kind.valueOf(rs.getString("type")),
                        rs.getInt("ref"), rs.getString("subject"),
                        new StatusRef(rs.getLong("status_id"), rs.getString("status_name"), rs.getString("color"),
                                rs.getBoolean("is_closed")),
                        new ProjectRef(rs.getLong("project_id"), rs.getString("slug"), rs.getString("project_name"))))
                .list();
    }

    @GetMapping("/users/{username}/profile")
    Profile profile(@PathVariable String username, @AuthenticationPrincipal CurrentUser me) {
        User user = find(username);
        Long id = user.getId();
        List<String> roles = jdbc.sql("""
                select distinct r.name from memberships m join roles r on r.id = m.role_id
                where m.user_id = :user order by r.name
                """).param("user", id).query(String.class).list();
        long memberOf = count("select count(*) from memberships where user_id = :user", id);
        long closed = count("""
                select count(*) from items i join statuses s on s.id = i.status_id
                where i.type = 'us' and s.is_closed and :user = any (i.assigned_users)
                """, id);
        long contacts = count("""
                select count(distinct other.user_id) from memberships mine
                join memberships other on other.project_id = mine.project_id and other.user_id <> mine.user_id
                where mine.user_id = :user
                """, id);
        return new Profile(id, user.getUsername(), user.getFullName(), user.getBio(), roles, memberOf, closed, contacts);
    }

    @GetMapping("/users/{username}/timeline")
    PageDto<HistoryDto> timeline(@PathVariable String username, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int size, @AuthenticationPrincipal CurrentUser me) {
        User user = find(username);
        List<Long> visible = projects.visibleTo(me.id(), me.superuser(), false).stream().map(Project::getId).toList();
        if (visible.isEmpty()) {
            return new PageDto<>(List.of(), 1, size, 0);
        }
        return history.timeline(user.getId(), visible, page, size);
    }

    private User find(String username) {
        return users.findByUsername(username.toLowerCase(Locale.ROOT)).filter(User::isActive)
                .orElseThrow(() -> Problems.notFound("Usuário não encontrado."));
    }

    private long count(String sql, Long userId) {
        return jdbc.sql(sql).param("user", userId).query(Long.class).single();
    }
}
