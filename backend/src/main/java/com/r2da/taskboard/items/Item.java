package com.r2da.taskboard.items;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.r2da.taskboard.projects.Kind;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "items")
@Getter
@Setter
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long projectId;

    @Enumerated(EnumType.STRING)
    private Kind type;

    private int ref;

    private String subject;

    private String description = "";

    private Long statusId;

    private Long milestoneId;

    private Long parentId;

    private BigDecimal points;

    @JdbcTypeCode(SqlTypes.ARRAY)
    private Long[] assignedUsers = {};

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(updatable = false)
    private Long[] watchers = {};

    private Long createdBy;

    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] tags = {};

    private LocalDate dueDate;

    private Long typeId;

    private Long severityId;

    private Long priorityId;

    @Column(updatable = false)
    private int boardOrder;

    @Column(updatable = false)
    private int listOrder;

    private boolean isBlocked;

    private String blockedNote = "";

    private boolean teamRequirement;

    private boolean clientRequirement;

    private boolean isIocaine;

    @Version
    private int version;

    private Instant createdAt = Instant.now();

    private Instant modifiedAt = Instant.now();

    private Instant finishedAt;

    @PreUpdate
    void touch() {
        modifiedAt = Instant.now();
    }
}
