package com.r2da.taskboard.items;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import com.r2da.taskboard.common.PageDto;
import com.r2da.taskboard.common.Problems;
import com.r2da.taskboard.history.HistoryService.HistoryDto;
import com.r2da.taskboard.projects.Kind;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ItemsController {

    private static final String KIND = "{kind:userstories|tasks|issues}";

    public record BulkMilestone(@NotEmpty List<Long> ids,
            @Schema(description = "Sprint de destino; null = backlog") Long milestoneId) {
    }

    public record BulkResult(int changed) {
    }

    private final ItemService service;

    @GetMapping("/projects/{key}/" + KIND)
    @Operation(summary = "Lista histórias de usuário, tarefas ou problemas do projeto")
    PageDto<ItemDto> list(@PathVariable String key, @PathVariable String kind,
            @Parameter(description = "Id do status") @RequestParam(required = false) Long status,
            @Parameter(description = "Id da sprint, ou \"none\" para o que está fora de sprint") @RequestParam(required = false) String milestone,
            @Parameter(description = "Só tarefas: id da história de usuário") @RequestParam(required = false) Long parent,
            @Parameter(description = "Id ou nome de usuário, \"me\" ou \"none\"") @RequestParam(required = false) String assignee,
            @RequestParam(required = false) String tag,
            @Parameter(description = "Texto: busca no assunto e na descrição, ou #ref") @RequestParam(required = false) String q,
            @Parameter(description = "Prazo anterior a esta data (AAAA-MM-DD)") @RequestParam(required = false) LocalDate dueBefore,
            @Parameter(description = "Alterados a partir deste instante (ISO-8601)") @RequestParam(required = false) Instant modifiedSince,
            @Parameter(description = "true = só em status fechado; false = só abertos") @RequestParam(required = false) Boolean closed,
            @RequestParam(required = false) Boolean blocked,
            @Parameter(description = "true = só o que quem chama observa") @RequestParam(required = false) Boolean watching,
            @Parameter(description = "false = fora os status arquivados do kanban; true = só eles") @RequestParam(required = false) Boolean archived,
            @Parameter(description = "order (quadro), list (backlog e sprint), ref, created, modified, due ou points; prefixo \"-\" inverte") @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal CurrentUser me) {
        return service.list(key, Kind.ofPath(kind), new ItemService.Filter(status, milestone, parent, assignee, tag,
                q, dueBefore, modifiedSince, closed, blocked, watching, archived, sort, page, size), me);
    }

    @PostMapping("/projects/{key}/" + KIND)
    @ResponseStatus(HttpStatus.CREATED)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = ItemInput.class)))
    ItemDto create(@PathVariable String key, @PathVariable String kind, @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal CurrentUser me) {
        return service.create(key, Kind.ofPath(kind), body, me);
    }

    @PostMapping("/projects/{key}/" + KIND + "/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria um item por linha de \"text\"; os outros campos valem para todos")
    List<ItemDto> bulk(@PathVariable String key, @PathVariable String kind, @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal CurrentUser me) {
        Map<String, Object> fields = new HashMap<>(body);
        Object text = fields.remove("text");
        if (!(text instanceof String lines)) {
            throw Problems.badRequest("text é obrigatório: um item por linha.");
        }
        return service.bulkCreate(key, Kind.ofPath(kind), lines, fields, me);
    }

    @PostMapping("/projects/{key}/userstories/bulk-milestone")
    @Operation(summary = "Envia várias histórias de usuário para uma sprint ou para o backlog")
    BulkResult bulkMilestone(@PathVariable String key, @Valid @RequestBody BulkMilestone body,
            @AuthenticationPrincipal CurrentUser me) {
        return new BulkResult(service.bulkMilestone(key, body.ids(), body.milestoneId(), me));
    }

    @GetMapping("/projects/{key}/items/by-ref/{ref}")
    @Operation(summary = "Item pelo número (#ref), seja história, tarefa ou problema")
    ItemDto getByRef(@PathVariable String key, @PathVariable int ref, @AuthenticationPrincipal CurrentUser me) {
        return service.getByRef(key, ref, me);
    }

    @GetMapping("/items/{id}")
    @Operation(summary = "Item completo; uma história de usuário vem com suas tarefas")
    ItemDto get(@PathVariable Long id, @AuthenticationPrincipal CurrentUser me) {
        return service.get(id, me);
    }

    @PatchMapping("/items/{id}")
    @Operation(summary = "Altera só os campos enviados; exige version")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = ItemInput.class)))
    ItemDto update(@PathVariable Long id, @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal CurrentUser me) {
        return service.update(id, body, me);
    }

    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id, @AuthenticationPrincipal CurrentUser me) {
        service.delete(id, me);
    }

    @PostMapping("/items/{id}/move")
    @Operation(summary = "Arrastar e soltar: muda status, sprint ou história e a posição",
            description = "Campos: order (\"board\" ou \"list\"), statusId, milestoneId, parentId e beforeId (o item que fica logo depois; null = fim).")
    ItemDto move(@PathVariable Long id, @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal CurrentUser me) {
        return service.move(id, body, me);
    }

    @PostMapping("/items/{id}/watch")
    ItemDto watch(@PathVariable Long id, @AuthenticationPrincipal CurrentUser me) {
        return service.watch(id, true, me);
    }

    @DeleteMapping("/items/{id}/watch")
    ItemDto unwatch(@PathVariable Long id, @AuthenticationPrincipal CurrentUser me) {
        return service.watch(id, false, me);
    }

    @GetMapping("/items/{id}/neighbors")
    @Operation(summary = "Números do item anterior e do próximo, do mesmo tipo e da mesma sprint")
    Map<String, Object> neighbors(@PathVariable Long id, @AuthenticationPrincipal CurrentUser me) {
        return service.neighbors(id, me);
    }

    @GetMapping("/items/{id}/history")
    @Operation(summary = "Histórico do item, do mais recente para o mais antigo")
    List<HistoryDto> history(@PathVariable Long id, @AuthenticationPrincipal CurrentUser me) {
        return service.history(id, me);
    }
}
