package com.r2da.taskboard.notifications;

import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import com.r2da.taskboard.auth.CurrentUser;
import com.r2da.taskboard.common.PageDto;
import com.r2da.taskboard.history.History;
import com.r2da.taskboard.users.UserRef;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

    public record ProjectRef(Long id, String slug, String name) {
    }

    public record Reader(Long id, String email, boolean followsAll) {
    }

    public record NotificationDto(Long id, String kind, UserRef actor, ProjectRef project, String itemType,
            int itemRef, String itemSubject, Instant createdAt, boolean read) {
    }

    public record NotificationPage(List<NotificationDto> items, int page, int size, long total, long unread) {
    }

    private static final Map<String, String> VIEW = Map.of("us", "view_us", "task", "view_tasks", "issue",
            "view_issues", "wiki", "view_wiki_pages");

    private final JdbcClient jdbc;

    public List<Reader> movement(History entry, Collection<Long> assigned, CurrentUser me) {
        String view = VIEW.get(entry.getTargetType());
        if (view == null) {
            return List.of();
        }
        Set<Long> involved = involved(entry);
        List<Reader> readers = jdbc.sql("""
                select u.id, u.email, coalesce(r.follows_all, false) as follows_all
                from memberships m
                join users u on u.id = m.user_id
                join projects p on p.id = m.project_id
                left join roles r on r.id = m.role_id
                where m.project_id = :project and u.is_active and u.id <> :actor
                  and (m.is_admin or p.owner_id = u.id or :perm = any(r.permissions))
                """)
                .param("project", entry.getProjectId()).param("actor", me.id()).param("perm", view)
                .query((rs, row) -> new Reader(rs.getLong("id"), rs.getString("email"), rs.getBoolean("follows_all")))
                .list().stream().filter(reader -> reader.followsAll() || involved.contains(reader.id())).toList();
        for (Reader reader : readers) {
            jdbc.sql("""
                    insert into notifications (user_id, project_id, actor_id, kind, item_type, item_ref, item_subject)
                    values (:user, :project, :actor, :kind, :type, :ref, :subject)
                    """)
                    .param("user", reader.id()).param("project", entry.getProjectId()).param("actor", me.id())
                    .param("kind", assigned.contains(reader.id()) ? "assigned" : entry.getKind().name())
                    .param("type", entry.getTargetType()).param("ref", entry.getTargetRef())
                    .param("subject", entry.getTargetSubject()).update();
        }
        return readers;
    }

    private Set<Long> involved(History entry) {
        Set<Long> ids = new HashSet<>();
        if (entry.getTargetType().equals("wiki")) {
            return ids;
        }
        jdbc.sql("select created_by, assigned_users, watchers from items where id = :id")
                .param("id", entry.getTargetId()).query(rs -> {
                    ids.add(rs.getObject("created_by", Long.class));
                    ids.addAll(longs(rs.getArray("assigned_users")));
                    ids.addAll(longs(rs.getArray("watchers")));
                });
        return ids;
    }

    private static List<Long> longs(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.asList((Long[]) array.getArray());
    }

    public NotificationPage list(Long userId, boolean onlyUnread, int page, int size) {
        int limit = Math.clamp(size, 1, PageDto.MAX_SIZE);
        int current = Math.max(page, 1);
        String filter = onlyUnread ? " and n.read_at is null" : "";
        List<NotificationDto> items = jdbc.sql("""
                select n.id, n.kind, n.item_type, n.item_ref, n.item_subject, n.created_at, n.read_at,
                       u.id as actor_id, u.username, u.full_name, p.id as project_id, p.slug, p.name
                from notifications n
                join projects p on p.id = n.project_id
                left join users u on u.id = n.actor_id
                where n.user_id = :user""" + filter + """

                order by n.created_at desc, n.id desc limit :limit offset :offset
                """)
                .param("user", userId).param("limit", limit).param("offset", (current - 1) * limit)
                .query((rs, row) -> new NotificationDto(rs.getLong("id"), rs.getString("kind"),
                        rs.getObject("actor_id") == null ? null
                                : new UserRef(rs.getLong("actor_id"), rs.getString("username"),
                                        rs.getString("full_name")),
                        new ProjectRef(rs.getLong("project_id"), rs.getString("slug"), rs.getString("name")),
                        rs.getString("item_type"), rs.getInt("item_ref"),
                        rs.getString("item_subject"), rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("read_at") != null))
                .list();
        long unread = jdbc.sql("select count(*) from notifications where user_id = :user and read_at is null")
                .param("user", userId).query(Long.class).single();
        long total = onlyUnread ? unread
                : jdbc.sql("select count(*) from notifications where user_id = :user").param("user", userId)
                        .query(Long.class).single();
        return new NotificationPage(items, current, limit, total, unread);
    }

    public void markRead(Long userId, List<Long> ids) {
        if (ids == null) {
            jdbc.sql("update notifications set read_at = now() where user_id = :user and read_at is null")
                    .param("user", userId).update();
        } else if (!ids.isEmpty()) {
            jdbc.sql("""
                    update notifications set read_at = now()
                    where user_id = :user and read_at is null and id in (:ids)
                    """).param("user", userId).param("ids", ids).update();
        }
    }

    public void forget(Long userId, Long projectId) {
        jdbc.sql("delete from notifications where user_id = :user and project_id = :project")
                .param("user", userId).param("project", projectId).update();
    }
}
