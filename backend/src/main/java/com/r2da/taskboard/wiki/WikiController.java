package com.r2da.taskboard.wiki;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.r2da.taskboard.auth.CurrentUser;
import com.r2da.taskboard.common.Problems;
import com.r2da.taskboard.history.Action;
import com.r2da.taskboard.history.HistoryService;
import com.r2da.taskboard.history.HistoryService.Target;
import com.r2da.taskboard.projects.Access;
import com.r2da.taskboard.projects.Perm;
import com.r2da.taskboard.users.UserRef;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/projects/{key}/wiki")
@RequiredArgsConstructor
public class WikiController {

    public record PageRef(String slug, Instant modifiedAt) {
    }

    public record WikiPage(Long id, String slug, String content, int version, UserRef lastModifier,
            Instant createdAt, Instant modifiedAt) {
    }

    public record WikiInput(@NotNull @Size(max = 200_000) String content, Integer version) {
    }

    private static final String SLUG = "[a-z0-9][a-z0-9-]{0,79}";

    private final Access access;
    private final HistoryService history;
    private final JdbcClient jdbc;

    @GetMapping
    List<PageRef> list(@PathVariable String key, @AuthenticationPrincipal CurrentUser me) {
        Long projectId = access.require(key, Perm.view_wiki_pages, me).project().getId();
        return jdbc.sql("select slug, modified_at from wiki_pages where project_id = :project order by slug")
                .param("project", projectId)
                .query((rs, row) -> new PageRef(rs.getString("slug"), rs.getTimestamp("modified_at").toInstant()))
                .list();
    }

    @GetMapping("/{slug}")
    WikiPage get(@PathVariable String key, @PathVariable String slug, @AuthenticationPrincipal CurrentUser me) {
        Long projectId = access.require(key, Perm.view_wiki_pages, me).project().getId();
        return find(projectId, slug).orElseThrow(() -> Problems.notFound("Página não encontrada."));
    }

    @PutMapping("/{slug}")
    @Operation(summary = "Cria a página ou grava um novo conteúdo")
    @Transactional
    public WikiPage save(@PathVariable String key, @PathVariable String slug, @Valid @RequestBody WikiInput body,
            @AuthenticationPrincipal CurrentUser me) {
        Access.Ctx ctx = access.require(key, Perm.view_wiki_pages, me);
        Long projectId = ctx.project().getId();
        if (!slug.matches(SLUG)) {
            throw Problems.badRequest("O endereço da página aceita letras minúsculas, números e hífen.");
        }
        Optional<WikiPage> current = find(projectId, slug);
        access.require(projectId, current.isPresent() ? Perm.modify_wiki_page : Perm.add_wiki_page, me);
        if (current.isEmpty()) {
            jdbc.sql("""
                    insert into wiki_pages (project_id, slug, content, last_modifier)
                    values (:project, :slug, :content, :me)
                    """).param("project", projectId).param("slug", slug).param("content", body.content())
                    .param("me", me.id()).update();
        } else {
            if (body.version() == null) {
                throw Problems.badRequest("version é obrigatório: envie a versão lida da página.");
            }
            int updated = jdbc.sql("""
                    update wiki_pages set content = :content, version = version + 1, last_modifier = :me,
                           modified_at = now()
                    where id = :id and version = :version
                    """).param("content", body.content()).param("me", me.id()).param("id", current.get().id())
                    .param("version", body.version()).update();
            if (updated == 0) {
                throw Problems.conflict("A página foi alterada por outra pessoa. Recarregue e tente de novo.");
            }
        }
        WikiPage page = find(projectId, slug).orElseThrow();
        history.record(projectId, new Target("wiki", page.id(), 0, slug),
                current.isEmpty() ? Action.create : Action.change, Map.of(), null, me);
        return page;
    }

    @DeleteMapping("/{slug}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable String key, @PathVariable String slug,
            @AuthenticationPrincipal CurrentUser me) {
        Long projectId = access.require(key, Perm.delete_wiki_page, me).project().getId();
        WikiPage page = find(projectId, slug).orElseThrow(() -> Problems.notFound("Página não encontrada."));
        jdbc.sql("delete from wiki_pages where id = :id").param("id", page.id()).update();
        history.record(projectId, new Target("wiki", page.id(), 0, slug), Action.delete, Map.of(), null, me);
    }

    private Optional<WikiPage> find(Long projectId, String slug) {
        return jdbc.sql("""
                select w.id, w.slug, w.content, w.version, w.created_at, w.modified_at,
                       u.id as user_id, u.username, u.full_name
                from wiki_pages w left join users u on u.id = w.last_modifier
                where w.project_id = :project and w.slug = :slug
                """).param("project", projectId).param("slug", slug)
                .query((rs, row) -> new WikiPage(rs.getLong("id"), rs.getString("slug"), rs.getString("content"),
                        rs.getInt("version"),
                        rs.getObject("user_id") == null ? null
                                : new UserRef(rs.getLong("user_id"), rs.getString("username"), rs.getString("full_name")),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("modified_at").toInstant()))
                .optional();
    }
}
