package com.r2da.taskboard.projects;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.r2da.taskboard.auth.CurrentUser;
import com.r2da.taskboard.common.Problems;
import com.r2da.taskboard.projects.ProjectService.ChoiceDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ChoicesController {

    private static final String COLOR = "#[0-9A-Fa-f]{6}";

    public record CreateChoice(@NotNull Choice.Kind kind, @NotBlank @Size(max = 60) String name,
            @Pattern(regexp = COLOR) String color, @DecimalMin("0") @DecimalMax("9999.9") BigDecimal value) {
    }

    public record UpdateChoice(@Size(min = 1, max = 60) String name, @Pattern(regexp = COLOR) String color,
            @DecimalMin("0") @DecimalMax("9999.9") BigDecimal value, Boolean clearValue) {
    }

    public record ReorderChoices(@NotNull Choice.Kind kind, @NotEmpty List<Long> ids) {
    }

    private final Access access;
    private final ChoiceRepository choices;

    @PostMapping("/projects/{key}/choices")
    @ResponseStatus(HttpStatus.CREATED)
    ChoiceDto create(@PathVariable String key, @Valid @RequestBody CreateChoice body,
            @AuthenticationPrincipal CurrentUser me) {
        Long projectId = access.requireAdmin(key, me).project().getId();
        Choice choice = new Choice();
        choice.setProjectId(projectId);
        choice.setKind(body.kind());
        choice.setName(body.name().trim());
        if (body.color() != null) {
            choice.setColor(body.color());
        }
        choice.setValue(body.kind() == Choice.Kind.points ? body.value() : null);
        choice.setSortOrder(choices.findByProjectIdAndKindOrderBySortOrderAscIdAsc(projectId, body.kind()).stream()
                .mapToInt(Choice::getSortOrder).max().orElse(0) + 1);
        return ChoiceDto.of(choices.save(choice));
    }

    @PatchMapping("/choices/{id}")
    ChoiceDto update(@PathVariable Long id, @Valid @RequestBody UpdateChoice body,
            @AuthenticationPrincipal CurrentUser me) {
        Choice choice = find(id, me);
        if (body.name() != null) {
            choice.setName(body.name().trim());
        }
        if (body.color() != null) {
            choice.setColor(body.color());
        }
        if (choice.getKind() == Choice.Kind.points) {
            if (Boolean.TRUE.equals(body.clearValue())) {
                choice.setValue(null);
            } else if (body.value() != null) {
                choice.setValue(body.value());
            }
        }
        return ChoiceDto.of(choices.save(choice));
    }

    @DeleteMapping("/choices/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id, @AuthenticationPrincipal CurrentUser me) {
        Choice choice = find(id, me);
        if (choices.findByProjectIdAndKindOrderBySortOrderAscIdAsc(choice.getProjectId(), choice.getKind())
                .size() == 1) {
            throw Problems.conflict("A lista precisa de pelo menos um valor.");
        }
        choices.delete(choice);
    }

    @PostMapping("/projects/{key}/choices/reorder")
    @Transactional
    List<ChoiceDto> reorder(@PathVariable String key, @Valid @RequestBody ReorderChoices body,
            @AuthenticationPrincipal CurrentUser me) {
        Long projectId = access.requireAdmin(key, me).project().getId();
        List<Choice> current = choices.findByProjectIdAndKindOrderBySortOrderAscIdAsc(projectId, body.kind());
        Map<Long, Choice> byId = current.stream().collect(Collectors.toMap(Choice::getId, Function.identity()));
        if (body.ids().size() != current.size() || !new HashSet<>(body.ids()).equals(byId.keySet())) {
            throw Problems.badRequest("ids precisa conter exatamente os valores desta lista, na nova ordem.");
        }
        int order = 0;
        for (Long id : body.ids()) {
            byId.get(id).setSortOrder(++order);
        }
        choices.saveAll(current);
        return body.ids().stream().map(byId::get).map(ChoiceDto::of).toList();
    }

    private Choice find(Long id, CurrentUser me) {
        Choice choice = choices.findById(id).orElseThrow(() -> Problems.notFound("Valor não encontrado."));
        access.requireAdmin(choice.getProjectId(), me);
        return choice;
    }
}
