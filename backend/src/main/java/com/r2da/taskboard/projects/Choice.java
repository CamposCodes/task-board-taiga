package com.r2da.taskboard.projects;

import java.math.BigDecimal;

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
@Table(name = "choices")
@Getter
@Setter
public class Choice {

    public enum Kind {
        points, issue_type, severity, priority
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long projectId;

    @Enumerated(EnumType.STRING)
    private Kind kind;

    private String name;

    private String color = "#70728f";

    private BigDecimal value;

    private int sortOrder;
}
