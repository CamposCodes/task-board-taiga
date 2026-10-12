package com.r2da.taskboard.projects;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.r2da.taskboard.auth.CurrentUser;
import com.r2da.taskboard.common.Problems;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class Access {

    public record Ctx(Project project, Set<Perm> perms, boolean member, boolean admin, boolean owner, Role role) {

        public boolean can(Perm perm) {
            return perms.contains(perm);
        }
    }

    private final ProjectRepository projects;
    private final MembershipRepository memberships;
    private final RoleRepository roles;

    public Ctx require(String key, Perm perm, CurrentUser me) {
        return check(resolve(find(key), me), perm);
    }

    public Ctx require(Long projectId, Perm perm, CurrentUser me) {
        return check(resolve(find(projectId), me), perm);
    }

    public Ctx requireAdmin(String key, CurrentUser me) {
        return admin(resolve(find(key), me));
    }

    public Ctx requireAdmin(Long projectId, CurrentUser me) {
        return admin(resolve(find(projectId), me));
    }

    public Ctx requireOwner(String key, CurrentUser me) {
        Ctx ctx = resolve(find(key), me);
        if (!ctx.owner()) {
            throw Problems.forbidden("Só o dono do projeto pode fazer isto.");
        }
        return ctx;
    }

    public Ctx resolve(Project project, CurrentUser me) {
        Optional<Membership> membership = memberships.findByProjectIdAndUserId(project.getId(), me.id());
        Role role = membership.flatMap(m -> roles.findById(m.getRoleId())).orElse(null);
        if (me.superuser()) {
            return new Ctx(project, Perm.ALL, membership.isPresent(), true, true, role);
        }
        if (membership.isEmpty()) {
            if (project.isPrivate()) {
                throw notFound();
            }
            return new Ctx(project, Perm.READ, false, false, false, null);
        }
        boolean owner = project.getOwnerId().equals(me.id());
        boolean admin = owner || membership.get().isAdmin();
        Set<Perm> perms = admin ? Perm.ALL : role == null ? Set.of() : Perm.parse(role.getPermissions());
        return new Ctx(project, perms, true, admin, owner, role);
    }

    private Project find(String key) {
        boolean numeric = !key.isEmpty() && key.length() <= 18 && key.chars().allMatch(Character::isDigit);
        return (numeric ? projects.findById(Long.valueOf(key)) : projects.findBySlug(key))
                .orElseThrow(Access::notFound);
    }

    private Project find(Long projectId) {
        return projects.findById(projectId).orElseThrow(Access::notFound);
    }

    private static Ctx check(Ctx ctx, Perm perm) {
        if (!ctx.can(perm)) {
            throw Problems.forbidden("Seu papel neste projeto não permite esta operação.");
        }
        if (ctx.project().isArchived() && !perm.isView()) {
            throw archived();
        }
        return ctx;
    }

    private static Ctx admin(Ctx ctx) {
        if (!ctx.admin()) {
            throw Problems.forbidden("Só administradores do projeto podem fazer isto.");
        }
        if (ctx.project().isArchived()) {
            throw archived();
        }
        return ctx;
    }

    private static RuntimeException archived() {
        return Problems.conflict("Projeto arquivado: somente leitura.");
    }

    private static RuntimeException notFound() {
        return Problems.notFound("Projeto não encontrado.");
    }
}
