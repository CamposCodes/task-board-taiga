package com.r2da.taskboard.items;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.r2da.taskboard.projects.Kind;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ItemInput")
public record ItemInput(
        @Schema(description = "Obrigatório na criação") String subject,
        @Schema(description = "Markdown") String description,
        @Schema(description = "Padrão: primeiro status aberto do tipo") Long statusId,
        @Schema(description = "Sprint; null = backlog. A tarefa de uma história segue a sprint dela") Long milestoneId,
        @Schema(description = "Só tarefa: id da história de usuário") Long parentId,
        @Schema(description = "Só história de usuário") BigDecimal points,
        @Schema(description = "Id de um membro; null tira todos") Long assignedTo,
        @Schema(description = "Só história de usuário: vários responsáveis") List<Long> assignedUsers,
        @Schema(description = "Ids dos observadores") List<Long> watchers,
        List<String> tags,
        @Schema(description = "Cor para tags que o projeto ainda não coloriu: {\"tag\": \"#RRGGBB\"}") Map<String, String> tagsColors,
        LocalDate dueDate,
        @Schema(description = "Só problema: id do tipo") Long typeId,
        @Schema(description = "Só problema: id da gravidade") Long severityId,
        @Schema(description = "Só problema: id da prioridade") Long priorityId,
        Boolean isBlocked,
        String blockedNote,
        @Schema(description = "Só história de usuário") Boolean teamRequirement,
        @Schema(description = "Só história de usuário") Boolean clientRequirement,
        @Schema(description = "Só tarefa") Boolean isIocaine,
        @Schema(description = "Só na criação: \"top\" ou \"bottom\" (padrão)") String position,
        @Schema(description = "Obrigatório na atualização: a versão lida do item") Integer version) {

    private static final Set<String> COMMON = Set.of("subject", "description", "statusId", "milestoneId",
            "assignedTo", "watchers", "tags", "tagsColors", "dueDate", "isBlocked", "blockedNote", "position",
            "version");

    static final Map<Kind, Set<String>> FIELDS = Map.of(
            Kind.us, with("points", "assignedUsers", "teamRequirement", "clientRequirement"),
            Kind.task, with("parentId", "isIocaine"),
            Kind.issue, with("typeId", "severityId", "priorityId"));

    private static Set<String> with(String... own) {
        return Stream.concat(COMMON.stream(), Stream.of(own)).collect(Collectors.toUnmodifiableSet());
    }
}
