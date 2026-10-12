package com.r2da.taskboard.projects;

import java.time.LocalDate;
import java.util.List;

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
import com.r2da.taskboard.common.Slugs;
import com.r2da.taskboard.projects.ProjectService.MilestoneDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MilestonesController {

    public record CreateMilestone(@NotBlank @Size(max = 120) String name, @NotNull LocalDate estimatedStart,
            @NotNull LocalDate estimatedFinish) {
    }

    public record UpdateMilestone(@Size(min = 1, max = 120) String name, LocalDate estimatedStart,
            LocalDate estimatedFinish, Boolean isClosed) {
    }

    private final Access access;
    private final ProjectService service;
    private final MilestoneRepository milestones;

    @GetMapping("/projects/{key}/milestones")
    List<MilestoneDto> list(@PathVariable String key, @AuthenticationPrincipal CurrentUser me) {
        return service.milestones(access.require(key, Perm.view_milestones, me).project().getId());
    }

    @GetMapping("/projects/{key}/milestones/by-slug/{slug}")
    MilestoneDto getBySlug(@PathVariable String key, @PathVariable String slug,
            @AuthenticationPrincipal CurrentUser me) {
        Long projectId = access.require(key, Perm.view_milestones, me).project().getId();
        return service.milestone(milestones.findByProjectIdAndSlug(projectId, slug)
                .orElseThrow(() -> Problems.notFound("Sprint não encontrada.")));
    }

    @PostMapping("/projects/{key}/milestones")
    @ResponseStatus(HttpStatus.CREATED)
    MilestoneDto create(@PathVariable String key, @Valid @RequestBody CreateMilestone body,
            @AuthenticationPrincipal CurrentUser me) {
        Long projectId = access.require(key, Perm.add_milestone, me).project().getId();
        Milestone milestone = new Milestone();
        milestone.setProjectId(projectId);
        milestone.setName(body.name().trim());
        milestone.setEstimatedStart(body.estimatedStart());
        milestone.setEstimatedFinish(body.estimatedFinish());
        return service.milestone(save(milestone, true));
    }

    @GetMapping("/milestones/{id}")
    MilestoneDto get(@PathVariable Long id, @AuthenticationPrincipal CurrentUser me) {
        return service.milestone(find(id, Perm.view_milestones, me));
    }

    @PatchMapping("/milestones/{id}")
    MilestoneDto update(@PathVariable Long id, @Valid @RequestBody UpdateMilestone body,
            @AuthenticationPrincipal CurrentUser me) {
        Milestone milestone = find(id, Perm.modify_milestone, me);
        boolean renamed = body.name() != null && !body.name().trim().equals(milestone.getName());
        if (body.name() != null) {
            milestone.setName(body.name().trim());
        }
        if (body.estimatedStart() != null) {
            milestone.setEstimatedStart(body.estimatedStart());
        }
        if (body.estimatedFinish() != null) {
            milestone.setEstimatedFinish(body.estimatedFinish());
        }
        if (body.isClosed() != null) {
            milestone.setClosed(body.isClosed());
        }
        return service.milestone(save(milestone, renamed));
    }

    @PostMapping("/milestones/{id}/close")
    MilestoneDto close(@PathVariable Long id, @AuthenticationPrincipal CurrentUser me) {
        Milestone milestone = find(id, Perm.modify_milestone, me);
        milestone.setClosed(true);
        return service.milestone(milestones.save(milestone));
    }

    @DeleteMapping("/milestones/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id, @AuthenticationPrincipal CurrentUser me) {
        milestones.delete(find(id, Perm.delete_milestone, me));
    }

    private Milestone save(Milestone milestone, boolean nameChanged) {
        if (milestone.getEstimatedFinish().isBefore(milestone.getEstimatedStart())) {
            throw Problems.badRequest("O fim da sprint não pode ser anterior ao início.");
        }
        if (nameChanged) {
            Long projectId = milestone.getProjectId();
            if (milestones.existsByProjectIdAndName(projectId, milestone.getName())) {
                throw Problems.conflict("Já existe uma sprint com esse nome no projeto.");
            }
            String base = Slugs.slugify(milestone.getName());
            String slug = base;
            for (int i = 2; !slug.equals(milestone.getSlug())
                    && milestones.existsByProjectIdAndSlug(projectId, slug); i++) {
                slug = base + "-" + i;
            }
            milestone.setSlug(slug);
        }
        return milestones.save(milestone);
    }

    private Milestone find(Long id, Perm perm, CurrentUser me) {
        Milestone milestone = milestones.findById(id).orElseThrow(() -> Problems.notFound("Sprint não encontrada."));
        access.require(milestone.getProjectId(), perm, me);
        return milestone;
    }
}
