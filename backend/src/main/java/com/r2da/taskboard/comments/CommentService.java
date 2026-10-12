package com.r2da.taskboard.comments;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.r2da.taskboard.auth.CurrentUser;
import com.r2da.taskboard.common.Problems;
import com.r2da.taskboard.history.Action;
import com.r2da.taskboard.history.HistoryService;
import com.r2da.taskboard.history.HistoryService.Target;
import com.r2da.taskboard.items.Item;
import com.r2da.taskboard.items.ItemService;
import com.r2da.taskboard.projects.Access;
import com.r2da.taskboard.users.User;
import com.r2da.taskboard.users.UserRef;
import com.r2da.taskboard.users.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommentService {

    public record CommentDto(Long id, UserRef author, String body, Instant createdAt, Instant editedAt) {
    }

    private static final int MAX_LENGTH = 20_000;

    private final CommentRepository comments;
    private final ItemService items;
    private final UserRepository users;
    private final Access access;
    private final HistoryService history;

    @Transactional(readOnly = true)
    public List<CommentDto> list(Long itemId, CurrentUser me) {
        items.require(itemId, "view", me);
        List<Comment> list = comments.findByItemIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(itemId);
        Map<Long, UserRef> authors = users
                .findAllById(list.stream().map(Comment::getAuthorId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, UserRef::of));
        return list.stream().map(c -> dto(c, authors.get(c.getAuthorId()))).toList();
    }

    @Transactional
    public CommentDto add(Long itemId, String body, CurrentUser me) {
        Item item = items.require(itemId, "comment", me);
        Comment comment = new Comment();
        comment.setProjectId(item.getProjectId());
        comment.setItemId(itemId);
        comment.setAuthorId(me.id());
        comment.setBody(text(body));
        comments.save(comment);
        history.record(item.getProjectId(), target(item), Action.comment, Map.of(), comment.getId(), me);
        return dto(comment, author(me));
    }

    @Transactional
    public CommentDto edit(Long id, String body, CurrentUser me) {
        Comment comment = find(id);
        items.require(comment.getItemId(), "comment", me);
        if (!me.id().equals(comment.getAuthorId())) {
            throw Problems.forbidden("Só o autor pode editar o comentário.");
        }
        comment.setBody(text(body));
        comment.setEditedAt(Instant.now());
        comments.save(comment);
        return dto(comment, author(me));
    }

    @Transactional
    public void delete(Long id, CurrentUser me) {
        Comment comment = find(id);
        if (me.id().equals(comment.getAuthorId())) {
            items.require(comment.getItemId(), "comment", me);
        } else {
            access.requireAdmin(comment.getProjectId(), me);
        }
        comment.setDeletedAt(Instant.now());
        comments.save(comment);
    }

    private Comment find(Long id) {
        return comments.findById(id).filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> Problems.notFound("Comentário não encontrado."));
    }

    private static Target target(Item item) {
        return new Target(item.getType().name(), item.getId(), item.getRef(), item.getSubject());
    }

    private UserRef author(CurrentUser me) {
        return users.findById(me.id()).map(UserRef::of).orElse(null);
    }

    private static String text(String body) {
        if (body == null || body.isBlank()) {
            throw Problems.badRequest("body é obrigatório.");
        }
        if (body.length() > MAX_LENGTH) {
            throw Problems.badRequest("body pode ter no máximo " + MAX_LENGTH + " caracteres.");
        }
        return body.strip();
    }

    private static CommentDto dto(Comment c, UserRef author) {
        return new CommentDto(c.getId(), author, c.getBody(), c.getCreatedAt(), c.getEditedAt());
    }
}
