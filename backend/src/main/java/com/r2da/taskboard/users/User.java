package com.r2da.taskboard.users;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    private String email;

    private String fullName;

    private String passwordHash;

    private String bio = "";

    private boolean isSuperuser;

    private boolean isActive = true;

    private String oidcSubject;

    private Instant passwordChangedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private Instant createdAt = Instant.now();
}
