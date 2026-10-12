package com.r2da.taskboard.history;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.r2da.taskboard.auth.CurrentUser;
import com.r2da.taskboard.common.PageDto;
import com.r2da.taskboard.history.HistoryService.HistoryDto;
import com.r2da.taskboard.projects.Access;
import com.r2da.taskboard.projects.Kind;
import com.r2da.taskboard.projects.Perm;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ActivityController {

    private final Access access;
    private final HistoryService history;

    @GetMapping("/projects/{key}/activity")
    PageDto<HistoryDto> activity(@PathVariable String key, @RequestParam(required = false) Instant since,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal CurrentUser me) {
        Access.Ctx ctx = access.require(key, Perm.view_project, me);
        List<String> types = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            if (ctx.can(Perm.of("view", kind))) {
                types.add(kind.name());
            }
        }
        if (ctx.can(Perm.view_wiki_pages)) {
            types.add("wiki");
        }
        return history.activity(ctx.project().getId(), types, since, page, size);
    }
}
