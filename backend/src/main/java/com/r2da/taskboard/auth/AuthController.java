package com.r2da.taskboard.auth;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.r2da.taskboard.common.Problems;
import com.r2da.taskboard.users.User;
import com.r2da.taskboard.users.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record ProfileRequest(@Size(min = 1, max = 200) String fullName, @Size(max = 210) String bio) {
    }

    public record PasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    public record Me(Long id, String username, String email, String fullName, String bio, boolean isSuperuser,
            boolean isSso,
            boolean viaToken, Set<String> scopes) {
    }

    public record AuthConfig(boolean oidcEnabled, String oidcLabel, String oidcLoginUrl) {
    }

    private final AuthService auth;
    private final UserRepository users;

    @Value("${app.oidc.issuer}")
    private String oidcIssuer;

    @Value("${app.oidc.label}")
    private String oidcLabel;

    @GetMapping("/auth/config")
    AuthConfig config() {
        return new AuthConfig(!oidcIssuer.isBlank(), oidcLabel, OidcLogin.START_PATH);
    }

    @PostMapping("/auth/login")
    ResponseEntity<Me> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        User user = auth.login(body.username(), body.password(), request.getRemoteAddr());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, auth.sessionCookie(user.getId()).toString())
                .body(toMe(user, null));
    }

    @PostMapping("/auth/logout")
    ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, auth.clearCookie().toString()).build();
    }

    @GetMapping("/me")
    Me me(@AuthenticationPrincipal CurrentUser me) {
        return toMe(users.findById(me.id()).orElseThrow(), me);
    }

    @PatchMapping("/me")
    Me updateMe(@Valid @RequestBody ProfileRequest body, @AuthenticationPrincipal CurrentUser me) {
        me.requireSession();
        User user = users.findById(me.id()).orElseThrow();
        if (body.fullName() != null) {
            user.setFullName(body.fullName().trim());
        }
        if (body.bio() != null) {
            user.setBio(body.bio().trim());
        }
        return toMe(users.save(user), me);
    }

    @PostMapping("/me/password")
    ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordRequest body,
            @AuthenticationPrincipal CurrentUser me) {
        me.requireSession();
        User user = users.findById(me.id()).orElseThrow();
        if (!auth.matches(body.currentPassword(), user)) {
            throw Problems.badRequest("A senha atual não confere.");
        }
        auth.setPassword(user, body.newPassword());
        users.save(user);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, auth.sessionCookie(user.getId()).toString())
                .build();
    }

    static Me toMe(User user, CurrentUser me) {
        return new Me(user.getId(), user.getUsername(), user.getEmail(), user.getFullName(), user.getBio(),
                user.isSuperuser(),
                user.getOidcSubject() != null, me != null && me.viaToken(), me != null ? me.scopes() : Set.of());
    }
}
