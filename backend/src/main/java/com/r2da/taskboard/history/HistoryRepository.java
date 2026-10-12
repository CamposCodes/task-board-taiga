package com.r2da.taskboard.history;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoryRepository extends JpaRepository<History, Long> {

    List<History> findTop200ByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(String targetType, Long targetId);

    Page<History> findByProjectIdAndTargetTypeInAndCreatedAtGreaterThanEqual(Long projectId,
            Collection<String> targetTypes, Instant since, Pageable pageable);

    Page<History> findByActorIdAndProjectIdIn(Long actorId, Collection<Long> projectIds, Pageable pageable);
}
