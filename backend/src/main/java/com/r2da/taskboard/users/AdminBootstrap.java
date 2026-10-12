package com.r2da.taskboard.users;

import java.security.SecureRandom;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.r2da.taskboard.auth.AuthService;

@Component
class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final AuthService auth;
    private final String configuredPassword;

    AdminBootstrap(UserRepository users, AuthService auth, @Value("${app.admin-password}") String password) {
        this.users = users;
        this.auth = auth;
        this.configuredPassword = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        String password = configuredPassword;
        if (password.isBlank()) {
            byte[] random = new byte[12];
            new SecureRandom().nextBytes(random);
            password = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            log.warn("Usuário inicial criado: admin / {} (troque a senha no primeiro acesso)", password);
        } else {
            log.info("Usuário inicial criado: admin (senha de APP_ADMIN_PASSWORD)");
        }
        User admin = new User();
        admin.setUsername("admin");
        admin.setEmail("admin@localhost");
        admin.setFullName("Administrador");
        admin.setSuperuser(true);
        auth.setPassword(admin, password);
        users.save(admin);
    }
}
