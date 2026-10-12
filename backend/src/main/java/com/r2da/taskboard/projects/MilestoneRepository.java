package com.r2da.taskboard.projects;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {

    List<Milestone> findByProjectIdOrderByEstimatedStartDescIdDesc(Long projectId);

    Optional<Milestone> findByProjectIdAndSlug(Long projectId, String slug);

    boolean existsByProjectIdAndName(Long projectId, String name);

    boolean existsByProjectIdAndSlug(Long projectId, String slug);
}
