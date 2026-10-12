package com.r2da.taskboard.notifications;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.r2da.taskboard.auth.CurrentUser;
import com.r2da.taskboard.notifications.NotificationService.NotificationPage;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationsController {

    public record MarkRead(List<Long> ids) {
    }

    private final NotificationService service;

    @GetMapping
    @Operation(summary = "Notificações de quem chama, mais recentes primeiro")
    NotificationPage list(@RequestParam(defaultValue = "false") boolean unread,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "30") int size,
            @AuthenticationPrincipal CurrentUser me) {
        return service.list(me.id(), unread, page, size);
    }

    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(@RequestBody(required = false) MarkRead body, @AuthenticationPrincipal CurrentUser me) {
        service.markRead(me.id(), body == null ? null : body.ids());
    }
}
