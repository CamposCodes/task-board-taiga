package com.r2da.taskboard.tokens;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ApiTokenRepository extends JpaRepository<ApiToken, Long> {

    Optional<ApiToken> findByTokenHash(String tokenHash);

    List<ApiToken> findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Query("""
            update ApiToken t set t.lastUsedAt = :now
            where t.id = :id and (t.lastUsedAt is null or t.lastUsedAt < :stale)
            """)
    void touch(Long id, Instant now, Instant stale);
}
