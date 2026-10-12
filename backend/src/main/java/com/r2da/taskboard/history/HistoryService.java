package com.r2da.taskboard.history;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import com.r2da.taskboard.auth.CurrentUser;
import com.r2da.taskboard.common.PageDto;
import com.r2da.taskboard.mail.MailService;
import com.r2da.taskboard.notifications.NotificationService;
import com.r2da.taskboard.projects.Project;
import com.r2da.taskboard.projects.ProjectRepository;
import com.r2da.taskboard.users.UserRef;
import com.r2da.taskboard.users.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HistoryService {

    public record Target(String type, Long id, int ref, String subject) {
    }

    public record ProjectRef(Long id, String slug, String name) {
    }

    public record CommentRef(Long id, String body) {
    }

    public record HistoryDto(Long id, Action kind, Target target, ProjectRef project, UserRef actor,
            boolean viaToken, Map<String, Object> diff, CommentRef comment, Instant createdAt) {
    }

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final HistoryRepository repository;
    private final UserRepository users;
    private final ProjectRepository projects;
    private final JdbcClient jdbc;
    private final MailService mail;
    private final NotificationService notifications;

    public void record(Long projectId, Target target, Action kind, Map<String, Object> diff, Long commentId,
            CurrentUser me) {
        record(projectId, target, kind, diff, commentId, List.of(), me);
    }

    public void record(Long projectId, Target target, Action kind, Map<String, Object> diff, Long commentId,
            Collection<Long> assigned, CurrentUser me) {
        History entry = new History();
        entry.setProjectId(projectId);
        entry.setTargetType(target.type());
        entry.setTargetId(target.id());
        entry.setTargetRef(target.ref());
        entry.setTargetSubject(target.subject());
        entry.setActorId(me.id());
        entry.setViaTokenId(me.tokenId());
        entry.setKind(kind);
        entry.setDiff(diff);
        entry.setCommentId(commentId);
        repository.save(entry);
        mail.movement(entry, notifications.movement(entry, assigned, me), me);
    }

    public List<HistoryDto> of(String type, Long targetId) {
        return toDtos(repository.findTop200ByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(type, targetId));
    }

    public PageDto<HistoryDto> activity(Long projectId, Collection<String> types, Instant since, int page, int size) {
        return PageDto.of(repository.findByProjectIdAndTargetTypeInAndCreatedAtGreaterThanEqual(projectId, types,
                since != null ? since : Instant.EPOCH, PageDto.request(page, size, NEWEST_FIRST)), this::toDtos);
    }

    public PageDto<HistoryDto> timeline(Long actorId, Collection<Long> projectIds, int page, int size) {
        return PageDto.of(repository.findByActorIdAndProjectIdIn(actorId, projectIds,
                PageDto.request(page, size, NEWEST_FIRST)), this::toDtos);
    }

    private List<HistoryDto> toDtos(List<History> entries) {
        if (entries.isEmpty()) {
            return List.of();
        }
        Map<Long, UserRef> actors = users
                .findAllById(entries.stream().map(History::getActorId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(u -> u.getId(), UserRef::of));
        Map<Long, ProjectRef> projectRefs = projects
                .findAllById(entries.stream().map(History::getProjectId).distinct().toList()).stream()
                .collect(Collectors.toMap(Project::getId, p -> new ProjectRef(p.getId(), p.getSlug(), p.getName())));
        Map<Long, String> comments = new HashMap<>();
        List<Long> commentIds = entries.stream().map(History::getCommentId).filter(Objects::nonNull).toList();
        if (!commentIds.isEmpty()) {
            jdbc.sql("select id, body from comments where id in (:ids) and deleted_at is null")
                    .param("ids", commentIds)
                    .query(rs -> {
                        comments.put(rs.getLong("id"), rs.getString("body"));
                    });
        }
        return entries.stream().map(e -> new HistoryDto(e.getId(), e.getKind(),
                new Target(e.getTargetType(), e.getTargetId(), e.getTargetRef(), e.getTargetSubject()),
                projectRefs.get(e.getProjectId()), actors.get(e.getActorId()), e.getViaTokenId() != null, e.getDiff(),
                comments.containsKey(e.getCommentId()) ? new CommentRef(e.getCommentId(), comments.get(e.getCommentId()))
                        : null,
                e.getCreatedAt())).toList();
    }
}
