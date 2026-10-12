package com.r2da.taskboard.tokens;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "api_tokens")
@Getter
@Setter
public class ApiToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String name;

    private String tokenHash;

    private String prefix;

    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] scopes;

    private Instant expiresAt;

    private Instant lastUsedAt;

    private Instant revokedAt;

    private Instant createdAt = Instant.now();
}
