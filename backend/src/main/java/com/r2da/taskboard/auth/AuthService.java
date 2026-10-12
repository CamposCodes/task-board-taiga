package com.r2da.taskboard.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.r2da.taskboard.common.Problems;
import com.r2da.taskboard.tokens.ApiToken;
import com.r2da.taskboard.tokens.ApiTokenRepository;
import com.r2da.taskboard.users.User;
import com.r2da.taskboard.users.UserRepository;

@Service
public class AuthService {

    public static final String COOKIE = "access_token";
    public static final String TOKEN_PREFIX = "tb_";

    private static final Duration RENEW_AFTER = Duration.ofHours(1);
    private static final int MAX_FAILURES = 5;
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);

    public record Resolved(CurrentUser user, Instant issuedAt) {
    }

    private record Failures(int count, Instant since) {
    }

    private final UserRepository users;
    private final ApiTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final Duration sessionTtl;
    private final boolean cookieSecure;
    private final String dummyHash;
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();

    public AuthService(UserRepository users, ApiTokenRepository tokens, PasswordEncoder encoder,
            JwtEncoder jwtEncoder, JwtDecoder jwtDecoder, @Value("${app.session-ttl}") Duration sessionTtl,
            @Value("${app.cookie-secure}") boolean cookieSecure) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.sessionTtl = sessionTtl;
        this.cookieSecure = cookieSecure;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    @Transactional
    public Resolved resolve(String token) {
        return token.startsWith(TOKEN_PREFIX) ? resolveApiToken(token) : resolveSession(token);
    }

    private Resolved resolveSession(String token) {
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException e) {
            return null;
        }
        User user = users.findById(Long.valueOf(jwt.getSubject())).orElse(null);
        Instant issuedAt = jwt.getIssuedAt();
        if (user == null || !user.isActive() || issuedAt == null || issuedAt.isBefore(user.getPasswordChangedAt())) {
            return null;
        }
        return new Resolved(new CurrentUser(user.getId(), user.getUsername(), user.isSuperuser(), null, Set.of()),
                issuedAt);
    }

    private Resolved resolveApiToken(String token) {
        ApiToken apiToken = tokens.findByTokenHash(sha256(token)).orElse(null);
        Instant now = Instant.now();
        if (apiToken == null || apiToken.getRevokedAt() != null
                || (apiToken.getExpiresAt() != null && apiToken.getExpiresAt().isBefore(now))) {
            return null;
        }
        User user = users.findById(apiToken.getUserId()).orElse(null);
        if (user == null || !user.isActive()) {
            return null;
        }
        tokens.touch(apiToken.getId(), now, now.minus(5, ChronoUnit.MINUTES));
        return new Resolved(new CurrentUser(user.getId(), user.getUsername(), user.isSuperuser(), apiToken.getId(),
                Set.of(apiToken.getScopes())), null);
    }

    @Transactional(readOnly = true)
    public User login(String login, String password, String clientIp) {
        String normalized = login.trim().toLowerCase(Locale.ROOT);
        String key = normalized + "|" + clientIp;
        Instant now = Instant.now();
        Failures previous = failures.get(key);
        if (previous != null && previous.since().plus(FAILURE_WINDOW).isBefore(now)) {
            failures.remove(key);
            previous = null;
        }
        if (previous != null && previous.count() >= MAX_FAILURES) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas de login. Aguarde 15 minutos.");
        }

        User user = users.findByUsername(normalized).or(() -> users.findByEmail(normalized)).orElse(null);
        boolean matches = fitsBcrypt(password)
                && encoder.matches(password, user != null ? user.getPasswordHash() : dummyHash);
        if (!matches || user == null || !user.isActive()) {
            if (failures.size() > 10_000) {
                failures.values().removeIf(f -> f.since().plus(FAILURE_WINDOW).isBefore(now));
            }
            failures.merge(key, new Failures(1, now), (old, one) -> new Failures(old.count() + 1, old.since()));
            throw Problems.unauthorized("Usuário ou senha inválidos.");
        }
        failures.remove(key);
        return user;
    }

    public boolean matches(String rawPassword, User user) {
        return fitsBcrypt(rawPassword) && encoder.matches(rawPassword, user.getPasswordHash());
    }

    public void setPassword(User user, String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 8) {
            throw Problems.badRequest("A senha precisa ter pelo menos 8 caracteres.");
        }
        if (!fitsBcrypt(rawPassword)) {
            throw Problems.badRequest("A senha pode ter no máximo 72 bytes.");
        }
        user.setPasswordHash(encoder.encode(rawPassword));
        user.setPasswordChangedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS));
    }

    private static boolean fitsBcrypt(String rawPassword) {
        return rawPassword.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    public ResponseCookie sessionCookie(Long userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(sessionTtl))
                .build();
        String jwt = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return cookie(jwt, sessionTtl);
    }

    public ResponseCookie clearCookie() {
        return cookie("", Duration.ZERO);
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    public boolean shouldRenew(Instant issuedAt) {
        return issuedAt != null && issuedAt.isBefore(Instant.now().minus(RENEW_AFTER));
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
