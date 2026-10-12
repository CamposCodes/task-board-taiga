package com.r2da.taskboard.items;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.r2da.taskboard.auth.CurrentUser;
import com.r2da.taskboard.common.FileStore;
import com.r2da.taskboard.common.Problems;
import com.r2da.taskboard.history.Action;
import com.r2da.taskboard.history.HistoryService;
import com.r2da.taskboard.users.UserRef;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AttachmentsController {

    public record AttachmentDto(Long id, String name, long size, String contentType, UserRef createdBy,
            Instant createdAt) {
    }

    private record Stored(Long id, Long itemId, Long projectId, String name, String contentType, String key) {
    }

    private static final Set<String> INLINE = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");

    private final ItemService items;
    private final HistoryService history;
    private final FileStore files;
    private final JdbcClient jdbc;

    @GetMapping("/items/{id}/attachments")
    List<AttachmentDto> list(@PathVariable Long id, @AuthenticationPrincipal CurrentUser me) {
        items.require(id, "view", me);
        return jdbc.sql("""
                select a.id, a.name, a.size, a.content_type, a.created_at, u.id as user_id, u.username, u.full_name
                from attachments a left join users u on u.id = a.created_by
                where a.item_id = :item order by a.id
                """).param("item", id)
                .query((rs, row) -> new AttachmentDto(rs.getLong("id"), rs.getString("name"), rs.getLong("size"),
                        rs.getString("content_type"),
                        rs.getObject("user_id") == null ? null
                                : new UserRef(rs.getLong("user_id"), rs.getString("username"), rs.getString("full_name")),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    @PostMapping(path = "/items/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Anexa um arquivo (campo \"file\")")
    @Transactional
    public AttachmentDto upload(@PathVariable Long id, @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CurrentUser me) throws IOException {
        Item item = items.require(id, "modify", me);
        String name = fileName(file.getOriginalFilename());
        if (file.isEmpty()) {
            throw Problems.badRequest("O arquivo está vazio.");
        }
        String contentType = file.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : file.getContentType();
        String key;
        try (InputStream in = file.getInputStream()) {
            key = files.save(item.getProjectId(), in);
        }
        Long attachmentId = jdbc.sql("""
                insert into attachments (project_id, item_id, name, size, content_type, storage_key, created_by)
                values (:project, :item, :name, :size, :type, :key, :me) returning id
                """)
                .param("project", item.getProjectId()).param("item", id).param("name", name)
                .param("size", file.getSize()).param("type", contentType).param("key", key).param("me", me.id())
                .query(Long.class).single();
        history.record(item.getProjectId(), ItemService.target(item), Action.change,
                Map.of("attachment", Arrays.asList(null, name)), null, me);
        return list(id, me).stream().filter(a -> a.id().equals(attachmentId)).findFirst().orElseThrow();
    }

    @GetMapping("/attachments/{id}/download")
    ResponseEntity<Resource> download(@PathVariable Long id, @AuthenticationPrincipal CurrentUser me) {
        Stored stored = find(id);
        items.require(stored.itemId(), "view", me);
        Resource resource = new FileSystemResource(files.path(stored.projectId(), stored.key()));
        if (!resource.exists()) {
            throw Problems.notFound("O arquivo deste anexo não está mais disponível.");
        }
        boolean inline = INLINE.contains(stored.contentType());
        return ResponseEntity.ok()
                .contentType(inline ? MediaType.parseMediaType(stored.contentType()) : MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                        .filename(stored.name(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(resource);
    }

    @DeleteMapping("/attachments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Long id, @AuthenticationPrincipal CurrentUser me) {
        Stored stored = find(id);
        Item item = items.require(stored.itemId(), "modify", me);
        jdbc.sql("delete from attachments where id = :id").param("id", id).update();
        files.delete(stored.projectId(), stored.key());
        history.record(item.getProjectId(), ItemService.target(item), Action.change,
                Map.of("attachment", Arrays.asList(stored.name(), null)), null, me);
    }

    private Stored find(Long id) {
        return jdbc.sql("select id, item_id, project_id, name, content_type, storage_key from attachments where id = :id")
                .param("id", id)
                .query((rs, row) -> new Stored(rs.getLong("id"), rs.getLong("item_id"), rs.getLong("project_id"),
                        rs.getString("name"), rs.getString("content_type"), rs.getString("storage_key")))
                .optional().orElseThrow(() -> Problems.notFound("Anexo não encontrado."));
    }

    private static String fileName(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty()) {
            return "arquivo";
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }
}
