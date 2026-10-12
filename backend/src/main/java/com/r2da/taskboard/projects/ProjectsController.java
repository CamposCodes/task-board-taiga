package com.r2da.taskboard.projects;

import java.util.List;
import java.util.Locale;
import java.util.Map;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.r2da.taskboard.auth.CurrentUser;
import com.r2da.taskboard.common.FileStore;
import com.r2da.taskboard.common.Problems;
import com.r2da.taskboard.notifications.NotificationService;
import com.r2da.taskboard.projects.ProjectService.MemberDto;
import com.r2da.taskboard.projects.ProjectService.ProjectDto;
import com.r2da.taskboard.users.User;
import com.r2da.taskboard.users.UserRepository;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectsController {

    public record CreateProject(@NotBlank @Size(max = 120) String name, @Size(max = 5000) String description,
            Boolean isPrivate, String template) {
    }

    public record UpdateProject(@Size(min = 1, max = 120) String name, @Size(max = 5000) String description,
            Boolean isPrivate, Map<String, String> tagsColors, Boolean isBacklogActivated,
            Boolean isKanbanActivated, Boolean isIssuesActivated, Boolean isWikiActivated) {
    }

    public record AddMember(@NotBlank String user, @NotNull Long roleId, Boolean isAdmin) {
    }

    public record UpdateMember(Long roleId, Boolean isAdmin) {
    }

    public record Order(@NotEmpty List<Long> ids) {
    }

    private final Access access;
    private final ProjectService service;
    private final ProjectRepository projects;
    private final MembershipRepository memberships;
    private final RoleRepository roles;
    private final UserRepository users;
    private final FileStore files;
    private final NotificationService notifications;
    private final JdbcClient jdbc;

    @GetMapping
    List<ProjectDto> list(@RequestParam(defaultValue = "false") boolean archived,
            @AuthenticationPrincipal CurrentUser me) {
        return service.visibleTo(me, archived);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ProjectDto create(@Valid @RequestBody CreateProject body, @AuthenticationPrincipal CurrentUser me) {
        Project project = service.create(body.name(), body.description(), body.isPrivate(), body.template(), me);
        return service.detail(access.resolve(project, me), me);
    }

    @PostMapping("/order")
    @Operation(summary = "Grava a ordem dos projetos na lista de quem chama")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void order(@Valid @RequestBody Order body, @AuthenticationPrincipal CurrentUser me) {
        int position = 0;
        for (Long projectId : body.ids()) {
            jdbc.sql("update memberships set user_order = :position where project_id = :project and user_id = :me")
                    .param("position", ++position).param("project", projectId).param("me", me.id()).update();
        }
    }

    @GetMapping("/{key}")
    ProjectDto get(@PathVariable String key, @AuthenticationPrincipal CurrentUser me) {
        return service.detail(access.require(key, Perm.view_project, me), me);
    }

    @PatchMapping("/{key}")
    ProjectDto update(@PathVariable String key, @Valid @RequestBody UpdateProject body,
            @AuthenticationPrincipal CurrentUser me) {
        Access.Ctx ctx = access.requireAdmin(key, me);
        Project project = ctx.project();
        if (body.name() != null) {
            project.setName(body.name().trim());
        }
        if (body.description() != null) {
            project.setDescription(body.description());
        }
        if (body.isPrivate() != null) {
            project.setPrivate(body.isPrivate());
        }
        if (body.tagsColors() != null) {
            body.tagsColors().forEach((tag, color) -> {
                if (tag.isBlank() || tag.length() > 50 || color == null || !color.matches("#[0-9A-Fa-f]{6}")) {
                    throw Problems.badRequest("tagsColors: use {\"tag\": \"#RRGGBB\"}.");
                }
            });
            project.setTagsColors(body.tagsColors());
        }
        if (body.isBacklogActivated() != null) {
            project.setBacklogActivated(body.isBacklogActivated());
        }
        if (body.isKanbanActivated() != null) {
            project.setKanbanActivated(body.isKanbanActivated());
        }
        if (body.isIssuesActivated() != null) {
            project.setIssuesActivated(body.isIssuesActivated());
        }
        if (body.isWikiActivated() != null) {
            project.setWikiActivated(body.isWikiActivated());
        }
        projects.save(project);
        return service.detail(ctx, me);
    }

    @DeleteMapping("/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String key, @AuthenticationPrincipal CurrentUser me) {
        Project project = access.requireOwner(key, me).project();
        projects.delete(project);
        files.deleteProject(project.getId());
    }

    @PostMapping("/{key}/archive")
    ProjectDto archive(@PathVariable String key, @AuthenticationPrincipal CurrentUser me) {
        return setArchived(key, true, me);
    }

    @PostMapping("/{key}/unarchive")
    ProjectDto unarchive(@PathVariable String key, @AuthenticationPrincipal CurrentUser me) {
        return setArchived(key, false, me);
    }

    private ProjectDto setArchived(String key, boolean archived, CurrentUser me) {
        Access.Ctx ctx = access.requireOwner(key, me);
        ctx.project().setArchived(archived);
        projects.save(ctx.project());
        return service.summary(ctx, me);
    }

    @PostMapping("/{key}/{mark:like|watch}")
    @Operation(summary = "Curtir ou observar o projeto")
    ProjectDto mark(@PathVariable String key, @PathVariable String mark, @AuthenticationPrincipal CurrentUser me) {
        Access.Ctx ctx = access.require(key, Perm.view_project, me);
        jdbc.sql("""
                insert into project_fans (project_id, user_id, kind) values (:project, :me, :kind)
                on conflict do nothing
                """).param("project", ctx.project().getId()).param("me", me.id()).param("kind", mark).update();
        return service.summary(ctx, me);
    }

    @DeleteMapping("/{key}/{mark:like|watch}")
    ProjectDto unmark(@PathVariable String key, @PathVariable String mark,
            @AuthenticationPrincipal CurrentUser me) {
        Access.Ctx ctx = access.require(key, Perm.view_project, me);
        jdbc.sql("delete from project_fans where project_id = :project and user_id = :me and kind = :kind")
                .param("project", ctx.project().getId()).param("me", me.id()).param("kind", mark).update();
        return service.summary(ctx, me);
    }

    @GetMapping("/{key}/members")
    List<MemberDto> members(@PathVariable String key, @AuthenticationPrincipal CurrentUser me) {
        return service.members(access.require(key, Perm.view_project, me).project());
    }

    @PostMapping("/{key}/members")
    @ResponseStatus(HttpStatus.CREATED)
    MemberDto addMember(@PathVariable String key, @Valid @RequestBody AddMember body,
            @AuthenticationPrincipal CurrentUser me) {
        Project project = access.requireAdmin(key, me).project();
        Role role = role(project.getId(), body.roleId());
        String login = body.user().trim().toLowerCase(Locale.ROOT);
        User user = users.findByUsername(login).or(() -> users.findByEmail(login)).filter(User::isActive)
                .orElseThrow(() -> Problems.notFound("Usuário não encontrado: informe o nome de usuário ou o e-mail."));
        if (memberships.findByProjectIdAndUserId(project.getId(), user.getId()).isPresent()) {
            throw Problems.conflict("Este usuário já é membro do projeto.");
        }
        Membership membership = new Membership();
        membership.setProjectId(project.getId());
        membership.setUserId(user.getId());
        membership.setRoleId(role.getId());
        membership.setAdmin(Boolean.TRUE.equals(body.isAdmin()));
        return ProjectService.member(project, memberships.save(membership), user, role.getName());
    }

    @PatchMapping("/{key}/members/{userId}")
    MemberDto updateMember(@PathVariable String key, @PathVariable Long userId,
            @Valid @RequestBody UpdateMember body, @AuthenticationPrincipal CurrentUser me) {
        Project project = access.requireAdmin(key, me).project();
        Membership membership = membership(project.getId(), userId);
        if (body.roleId() != null) {
            membership.setRoleId(role(project.getId(), body.roleId()).getId());
        }
        if (body.isAdmin() != null) {
            if (project.getOwnerId().equals(userId) && !body.isAdmin()) {
                throw Problems.conflict("O dono do projeto é sempre administrador.");
            }
            membership.setAdmin(body.isAdmin());
        }
        memberships.save(membership);
        return ProjectService.member(project, membership, users.findById(userId).orElseThrow(),
                roles.findById(membership.getRoleId()).map(Role::getName).orElse(null));
    }

    @DeleteMapping("/{key}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void removeMember(@PathVariable String key, @PathVariable Long userId, @AuthenticationPrincipal CurrentUser me) {
        Project project = (userId.equals(me.id()) ? access.require(key, Perm.view_project, me)
                : access.requireAdmin(key, me)).project();
        if (project.getOwnerId().equals(userId)) {
            throw Problems.conflict("O dono do projeto não pode ser removido.");
        }
        memberships.delete(membership(project.getId(), userId));
        jdbc.sql("""
                update items set assigned_users = array_remove(assigned_users, cast(:user as bigint)),
                                 watchers = array_remove(watchers, cast(:user as bigint))
                where project_id = :project and (:user = any (assigned_users) or :user = any (watchers))
                """).param("user", userId).param("project", project.getId()).update();
        notifications.forget(userId, project.getId());
    }

    private Membership membership(Long projectId, Long userId) {
        return memberships.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> Problems.notFound("Membro não encontrado."));
    }

    private Role role(Long projectId, Long roleId) {
        return roles.findById(roleId).filter(r -> r.getProjectId().equals(projectId))
                .orElseThrow(() -> Problems.badRequest("roleId não é um papel deste projeto."));
    }
}
