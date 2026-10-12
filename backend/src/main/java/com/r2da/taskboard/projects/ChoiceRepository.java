package com.r2da.taskboard.projects;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChoiceRepository extends JpaRepository<Choice, Long> {

    List<Choice> findByProjectIdOrderByKindAscSortOrderAscIdAsc(Long projectId);

    List<Choice> findByProjectIdAndKindOrderBySortOrderAscIdAsc(Long projectId, Choice.Kind kind);
}
