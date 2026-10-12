package com.r2da.taskboard.users;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.r2da.taskboard.common.Problems;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OidcUsers {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public User provision(String subject, String preferredUsername, String email, String fullName) {
        if (subject == null || subject.isBlank()) {
            throw Problems.forbidden("O provedor de identidade não informou o identificador do usuário.");
        }
        User user = users.findByOidcSubject(subject).orElse(null);
        if (user == null) {
            String username = sanitize(preferredUsername != null ? preferredUsername : email != null ? email : subject);
            User existing = users.findByUsername(username).orElse(null);
            if (existing != null && existing.getOidcSubject() == null) {
                if (existing.isSuperuser()) {
                    throw Problems.forbidden("Já existe uma conta local de administrador chamada \"" + username
                            + "\". Ela não pode ser assumida pelo login externo.");
                }
                user = existing;
            } else {
                user = new User();
                user.setUsername(available(username));
                user.setFullName(user.getUsername());
                user.setEmail(user.getUsername() + "@sem-email.invalid");
                byte[] secret = new byte[32];
                random.nextBytes(secret);
                user.setPasswordHash(encoder.encode(Base64.getEncoder().encodeToString(secret)));
            }
            user.setOidcSubject(subject);
        }
        if (!user.isActive()) {
            throw Problems.forbidden("Este usuário está desativado.");
        }
        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName.trim());
        }
        if (email != null && !email.isBlank()) {
            String normalized = email.trim().toLowerCase(Locale.ROOT);
            if (!normalized.equals(user.getEmail()) && !users.existsByEmail(normalized)) {
                user.setEmail(normalized);
            }
        }
        return users.save(user);
    }

    private static String sanitize(String name) {
        String clean = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        return clean.isEmpty() ? "usuario" : clean.length() > 60 ? clean.substring(0, 60) : clean;
    }

    private String available(String username) {
        String candidate = username;
        for (int i = 2; users.existsByUsername(candidate); i++) {
            candidate = username + "-" + i;
        }
        return candidate;
    }
}
