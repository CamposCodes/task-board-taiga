package com.r2da.taskboard.tokens;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.r2da.taskboard.auth.AuthService;
import com.r2da.taskboard.auth.CurrentUser;
import com.r2da.taskboard.common.Problems;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tokens")
@RequiredArgsConstructor
public class TokensController {

    public record TokenDto(Long id, String name, String prefix, List<String> scopes, Instant expiresAt,
            Instant lastUsedAt, Instant createdAt) {

        static TokenDto of(ApiToken t) {
            return new TokenDto(t.getId(), t.getName(), t.getPrefix(), List.of(t.getScopes()), t.getExpiresAt(),
                    t.getLastUsedAt(), t.getCreatedAt());
        }
    }

    public record CreatedToken(String token, TokenDto info) {
    }

    public record CreateToken(@NotBlank @Size(max = 100) String name, Boolean write,
            @Min(1) @Max(3650) Integer expiresInDays) {
    }

    private final ApiTokenRepository tokens;
    private final SecureRandom random = new SecureRandom();

    @GetMapping
    List<TokenDto> list(@AuthenticationPrincipal CurrentUser me) {
        me.requireSession();
        return tokens.findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(me.id()).stream().map(TokenDto::of)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CreatedToken create(@Valid @RequestBody CreateToken body, @AuthenticationPrincipal CurrentUser me) {
        me.requireSession();
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        String token = AuthService.TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);

        ApiToken entity = new ApiToken();
        entity.setUserId(me.id());
        entity.setName(body.name().trim());
        entity.setTokenHash(AuthService.sha256(token));
        entity.setPrefix(token.substring(0, AuthService.TOKEN_PREFIX.length() + 8));
        entity.setScopes(Boolean.TRUE.equals(body.write()) ? new String[] { "read", "write" } : new String[] { "read" });
        if (body.expiresInDays() != null) {
            entity.setExpiresAt(Instant.now().plus(body.expiresInDays(), ChronoUnit.DAYS));
        }
        return new CreatedToken(token, TokenDto.of(tokens.save(entity)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable Long id, @AuthenticationPrincipal CurrentUser me) {
        me.requireSession();
        ApiToken token = tokens.findById(id).filter(t -> t.getUserId().equals(me.id()))
                .orElseThrow(() -> Problems.notFound("Token não encontrado."));
        token.setRevokedAt(Instant.now());
        tokens.save(token);
    }
}
