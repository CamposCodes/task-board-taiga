package com.r2da.taskboard.projects;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    Optional<Membership> findByProjectIdAndUserId(Long projectId, Long userId);

    List<Membership> findByProjectId(Long projectId);

    List<Membership> findByUserId(Long userId);

    long countByRoleId(Long roleId);
}
