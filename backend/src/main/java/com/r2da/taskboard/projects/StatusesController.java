package com.r2da.taskboard.projects;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
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
import com.r2da.taskboard.common.Problems;
import com.r2da.taskboard.projects.ProjectService.StatusDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StatusesController {

    private static final String COLOR = "#[0-9A-Fa-f]{6}";

    public record CreateStatus(@NotNull Kind kind, @NotBlank @Size(max = 60) String name,
            @NotNull @Pattern(regexp = COLOR) String color, Boolean isClosed, Boolean isArchived,
            @Min(0) Integer wipLimit) {
    }

    public record UpdateStatus(@Size(min = 1, max = 60) String name, @Pattern(regexp = COLOR) String color,
            Boolean isClosed, Boolean isArchived, @Min(0) Integer wipLimit) {
    }

    public record Reorder(@NotNull Kind kind, @NotEmpty List<Long> ids) {
    }

    private final Access access;
    private final ProjectService service;
    private final StatusRepository statuses;
    private final JdbcClient jdbc;

    @GetMapping("/projects/{key}/statuses")
    List<StatusDto> list(@PathVariable String key, @AuthenticationPrincipal CurrentUser me) {
        return service.statuses(access.require(key, Perm.view_project, me).project().getId());
    }

    @PostMapping("/projects/{key}/statuses")
    @ResponseStatus(HttpStatus.CREATED)
    StatusDto create(@PathVariable String key, @Valid @RequestBody CreateStatus body,
            @AuthenticationPrincipal CurrentUser me) {
        Long projectId = access.requireAdmin(key, me).project().getId();
        Status status = new Status();
        status.setProjectId(projectId);
        status.setKind(body.kind());
        status.setName(body.name().trim());
        status.setColor(body.color());
        status.setClosed(Boolean.TRUE.equals(body.isClosed()));
        status.setArchived(Boolean.TRUE.equals(body.isArchived()) && body.kind() == Kind.us);
        status.setWipLimit(limit(body.wipLimit()));
        status.setSortOrder(statuses.findByProjectIdAndKindOrderBySortOrder(projectId, body.kind()).stream()
                .mapToInt(Status::getSortOrder).max().orElse(-1) + 1);
        return StatusDto.of(statuses.save(status));
    }

    @PatchMapping("/statuses/{id}")
    StatusDto update(@PathVariable Long id, @Valid @RequestBody UpdateStatus body,
            @AuthenticationPrincipal CurrentUser me) {
        Status status = find(id, me);
        if (body.name() != null) {
            status.setName(body.name().trim());
        }
        if (body.color() != null) {
            status.setColor(body.color());
        }
        if (body.isClosed() != null) {
            status.setClosed(body.isClosed());
        }
        if (body.isArchived() != null) {
            status.setArchived(body.isArchived() && status.getKind() == Kind.us);
        }
        if (body.wipLimit() != null) {
            status.setWipLimit(limit(body.wipLimit()));
        }
        return StatusDto.of(statuses.save(status));
    }

    @DeleteMapping("/statuses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id, @AuthenticationPrincipal CurrentUser me) {
        Status status = find(id, me);
        long inUse = jdbc.sql("select count(*) from items where status_id = :id").param("id", id)
                .query(Long.class).single();
        if (inUse > 0) {
            throw Problems.conflict("Status em uso por " + inUse + " item(ns). Mova-os antes de excluir.");
        }
        if (statuses.findByProjectIdAndKindOrderBySortOrder(status.getProjectId(), status.getKind()).size() == 1) {
            throw Problems.conflict("O projeto precisa de pelo menos um status deste tipo.");
        }
        statuses.delete(status);
    }

    @PostMapping("/projects/{key}/statuses/reorder")
    @Transactional
    List<StatusDto> reorder(@PathVariable String key, @Valid @RequestBody Reorder body,
            @AuthenticationPrincipal CurrentUser me) {
        Long projectId = access.requireAdmin(key, me).project().getId();
        List<Status> current = statuses.findByProjectIdAndKindOrderBySortOrder(projectId, body.kind());
        Map<Long, Status> byId = current.stream().collect(Collectors.toMap(Status::getId, Function.identity()));
        if (body.ids().size() != current.size() || !new HashSet<>(body.ids()).equals(byId.keySet())) {
            throw Problems.badRequest("ids precisa conter exatamente os status deste tipo, na nova ordem.");
        }
        int order = 0;
        for (Long id : body.ids()) {
            byId.get(id).setSortOrder(order++);
        }
        statuses.saveAll(current);
        return service.statuses(projectId).stream().filter(s -> s.kind() == body.kind()).toList();
    }

    private Status find(Long id, CurrentUser me) {
        Status status = statuses.findById(id).orElseThrow(() -> Problems.notFound("Status não encontrado."));
        access.requireAdmin(status.getProjectId(), me);
        return status;
    }

    private static Integer limit(Integer wipLimit) {
        return wipLimit == null || wipLimit == 0 ? null : wipLimit;
    }
}
