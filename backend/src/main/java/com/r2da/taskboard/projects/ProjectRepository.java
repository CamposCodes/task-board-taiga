package com.r2da.taskboard.projects;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("""
            select p from Project p
            where p.isArchived = :archived
              and (:all = true or p.isPrivate = false
                   or exists (select 1 from Membership m where m.projectId = p.id and m.userId = :userId))
            order by p.name
            """)
    List<Project> visibleTo(Long userId, boolean all, boolean archived);
}
