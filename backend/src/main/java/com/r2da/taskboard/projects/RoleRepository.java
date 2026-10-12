package com.r2da.taskboard.projects;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {

    List<Role> findByProjectIdOrderBySortOrderAscIdAsc(Long projectId);

    boolean existsByProjectIdAndName(Long projectId, String name);
}
