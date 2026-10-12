package com.r2da.taskboard.projects;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "projects")
@Getter
@Setter
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String slug;

    private String name;

    private String description = "";

    private Long ownerId;

    private boolean isPrivate = true;

    private boolean isArchived;

    private boolean isBacklogActivated = true;

    private boolean isKanbanActivated = true;

    private boolean isIssuesActivated = true;

    private boolean isWikiActivated = true;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> tagsColors = new HashMap<>();

    private Instant createdAt = Instant.now();

    private Instant modifiedAt = Instant.now();

    @PreUpdate
    void touch() {
        modifiedAt = Instant.now();
    }
}
