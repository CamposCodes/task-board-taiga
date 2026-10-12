package com.r2da.taskboard.history;

import java.time.Instant;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "history")
@Getter
@Setter
public class History {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long projectId;

    private String targetType;

    private Long targetId;

    private int targetRef;

    private String targetSubject;

    private Long actorId;

    private Long viaTokenId;

    @Enumerated(EnumType.STRING)
    private Action kind;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> diff;

    private Long commentId;

    private Instant createdAt = Instant.now();
}
