package com.r2da.taskboard.projects;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "milestones")
@Getter
@Setter
public class Milestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long projectId;

    private String name;

    private String slug;

    private LocalDate estimatedStart;

    private LocalDate estimatedFinish;

    private boolean isClosed;

    private Instant createdAt = Instant.now();

    private Instant modifiedAt = Instant.now();

    @PreUpdate
    void touch() {
        modifiedAt = Instant.now();
    }
}
