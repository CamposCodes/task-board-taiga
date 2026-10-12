package com.r2da.taskboard.projects;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusRepository extends JpaRepository<Status, Long> {

    List<Status> findByProjectIdOrderByKindAscSortOrderAsc(Long projectId);

    List<Status> findByProjectIdAndKindOrderBySortOrder(Long projectId, Kind kind);
}
