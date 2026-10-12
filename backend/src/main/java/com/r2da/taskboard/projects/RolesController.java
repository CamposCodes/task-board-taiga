package com.r2da.taskboard.projects;

import java.util.List;
import java.util.Set;

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
import com.r2da.taskboard.common.Problems;
import com.r2da.taskboard.projects.ProjectService.RoleDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RolesController {

    public record CreateRole(@NotBlank @Size(max = 60) String name, Set<Perm> permissions, Boolean followsAll) {
    }

    public record UpdateRole(@Size(min = 1, max = 60) String name, Set<Perm> permissions, Boolean followsAll) {
    }

    private final Access access;
    private final ProjectService service;
    private final RoleRepository roles;
    private final MembershipRepository memberships;

    @GetMapping("/projects/{key}/roles")
    List<RoleDto> list(@PathVariable String key, @AuthenticationPrincipal CurrentUser me) {
        return service.roles(access.require(key, Perm.view_project, me).project().getId());
    }

    @PostMapping("/projects/{key}/roles")
    @ResponseStatus(HttpStatus.CREATED)
    RoleDto create(@PathVariable String key, @Valid @RequestBody CreateRole body,
            @AuthenticationPrincipal CurrentUser me) {
        Long projectId = access.requireAdmin(key, me).project().getId();
        Role role = new Role();
        role.setProjectId(projectId);
        role.setName(name(projectId, body.name()));
        role.setPermissions(names(body.permissions() == null ? Perm.READ : body.permissions()));
        role.setFollowsAll(Boolean.TRUE.equals(body.followsAll()));
        role.setSortOrder(roles.findByProjectIdOrderBySortOrderAscIdAsc(projectId).stream()
                .mapToInt(Role::getSortOrder).max().orElse(0) + 1);
        return dto(roles.save(role));
    }

    @PatchMapping("/roles/{id}")
    RoleDto update(@PathVariable Long id, @Valid @RequestBody UpdateRole body,
            @AuthenticationPrincipal CurrentUser me) {
        Role role = find(id, me);
        if (body.name() != null && !body.name().trim().equals(role.getName())) {
            role.setName(name(role.getProjectId(), body.name()));
        }
        if (body.permissions() != null) {
            role.setPermissions(names(body.permissions()));
        }
        if (body.followsAll() != null) {
            role.setFollowsAll(body.followsAll());
        }
        return dto(roles.save(role));
    }

    @DeleteMapping("/roles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id, @AuthenticationPrincipal CurrentUser me) {
        Role role = find(id, me);
        long members = memberships.countByRoleId(id);
        if (members > 0) {
            throw Problems.conflict("Papel em uso por " + members + " membro(s). Troque o papel deles antes de excluir.");
        }
        if (roles.findByProjectIdOrderBySortOrderAscIdAsc(role.getProjectId()).size() == 1) {
            throw Problems.conflict("O projeto precisa de pelo menos um papel.");
        }
        roles.delete(role);
    }

    private Role find(Long id, CurrentUser me) {
        Role role = roles.findById(id).orElseThrow(() -> Problems.notFound("Papel não encontrado."));
        access.requireAdmin(role.getProjectId(), me);
        return role;
    }

    private String name(Long projectId, String name) {
        String clean = name.trim();
        if (roles.existsByProjectIdAndName(projectId, clean)) {
            throw Problems.conflict("Já existe um papel com esse nome no projeto.");
        }
        return clean;
    }

    private static String[] names(Set<Perm> perms) {
        return Perm.ALL.stream()
                .filter(p -> perms.contains(p) || p == Perm.view_project || p.isView() && perms.stream()
                        .anyMatch(other -> !other.isView() && subject(other).equals(subject(p))))
                .map(Perm::name).toArray(String[]::new);
    }

    private static String subject(Perm perm) {
        String name = perm.name();
        String subject = name.substring(name.indexOf('_') + 1);
        return subject.endsWith("s") ? subject.substring(0, subject.length() - 1) : subject;
    }

    private RoleDto dto(Role role) {
        return new RoleDto(role.getId(), role.getName(), role.getSortOrder(), Perm.parse(role.getPermissions()),
                role.isFollowsAll(),
                memberships.countByRoleId(role.getId()));
    }
}
