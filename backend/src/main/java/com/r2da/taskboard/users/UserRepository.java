package com.r2da.taskboard.users;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByOidcSubject(String oidcSubject);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query("""
            select u from User u
            where lower(u.username) like :pattern or lower(u.fullName) like :pattern or lower(u.email) like :pattern
            """)
    Page<User> search(String pattern, Pageable pageable);
}
