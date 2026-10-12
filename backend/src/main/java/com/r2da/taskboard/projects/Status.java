package com.r2da.taskboard.projects;

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
@Table(name = "statuses")
@Getter
@Setter
public class Status {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long projectId;

    @Enumerated(EnumType.STRING)
    private Kind kind;

    private String name;

    private String color;

    private int sortOrder;

    private boolean isClosed;

    private Integer wipLimit;

    private boolean isArchived;
}
