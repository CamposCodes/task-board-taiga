package com.r2da.taskboard.comments;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.r2da.taskboard.auth.CurrentUser;
import com.r2da.taskboard.comments.CommentService.CommentDto;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CommentsController {

    public record CommentInput(String body) {
    }

    private final CommentService service;

    @GetMapping("/items/{id}/comments")
    List<CommentDto> list(@PathVariable Long id, @AuthenticationPrincipal CurrentUser me) {
        return service.list(id, me);
    }

    @PostMapping("/items/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    CommentDto add(@PathVariable Long id, @RequestBody CommentInput input, @AuthenticationPrincipal CurrentUser me) {
        return service.add(id, input.body(), me);
    }

    @PatchMapping("/comments/{id}")
    CommentDto edit(@PathVariable Long id, @RequestBody CommentInput input,
            @AuthenticationPrincipal CurrentUser me) {
        return service.edit(id, input.body(), me);
    }

    @DeleteMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id, @AuthenticationPrincipal CurrentUser me) {
        service.delete(id, me);
    }
}
