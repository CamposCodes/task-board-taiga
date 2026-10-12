package com.r2da.taskboard.users;

import java.time.Instant;
import java.util.Locale;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.r2da.taskboard.auth.AuthService;
import com.r2da.taskboard.auth.CurrentUser;
import com.r2da.taskboard.common.PageDto;
import com.r2da.taskboard.common.Problems;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UsersController {

    public record UserDto(Long id, String username, String email, String fullName, boolean isSuperuser,
            boolean isActive, Instant createdAt) {

        static UserDto of(User u) {
            return new UserDto(u.getId(), u.getUsername(), u.getEmail(), u.getFullName(), u.isSuperuser(),
                    u.isActive(), u.getCreatedAt());
        }
    }

    public record CreateUser(
            @NotBlank @Size(max = 60) @Pattern(regexp = "[A-Za-z0-9._-]+", message = "use letras, números, ponto, hífen ou sublinhado") String username,
            @NotBlank @Email @Size(max = 200) String email,
            @NotBlank @Size(max = 200) String fullName,
            @NotBlank String password,
            Boolean isSuperuser) {
    }

    public record UpdateUser(@Email @Size(max = 200) String email, @Size(min = 1, max = 200) String fullName,
            String password, Boolean isSuperuser, Boolean isActive) {
    }

    private final UserRepository users;
    private final AuthService auth;

    @GetMapping
    PageDto<UserDto> list(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size, @AuthenticationPrincipal CurrentUser me) {
        me.requireSuperuser();
        String pattern = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return PageDto.of(users.search(pattern, PageDto.request(page, size, Sort.by("username"))),
                list -> list.stream().map(UserDto::of).toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserDto create(@Valid @RequestBody CreateUser body, @AuthenticationPrincipal CurrentUser me) {
        me.requireSuperuser();
        String username = body.username().toLowerCase(Locale.ROOT);
        String email = body.email().toLowerCase(Locale.ROOT);
        if (users.existsByUsername(username)) {
            throw Problems.conflict("Já existe um usuário com esse nome de usuário.");
        }
        if (users.existsByEmail(email)) {
            throw Problems.conflict("Já existe um usuário com esse e-mail.");
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(body.fullName().trim());
        user.setSuperuser(Boolean.TRUE.equals(body.isSuperuser()));
        auth.setPassword(user, body.password());
        return UserDto.of(users.save(user));
    }

    @PatchMapping("/{id}")
    UserDto update(@PathVariable Long id, @Valid @RequestBody UpdateUser body,
            @AuthenticationPrincipal CurrentUser me) {
        me.requireSuperuser();
        User user = users.findById(id).orElseThrow(() -> Problems.notFound("Usuário não encontrado."));
        boolean self = user.getId().equals(me.id());
        if (self && (Boolean.FALSE.equals(body.isActive()) || Boolean.FALSE.equals(body.isSuperuser()))) {
            throw Problems.conflict("Você não pode desativar nem rebaixar a própria conta.");
        }
        if (body.email() != null) {
            String email = body.email().toLowerCase(Locale.ROOT);
            if (!email.equals(user.getEmail()) && users.existsByEmail(email)) {
                throw Problems.conflict("Já existe um usuário com esse e-mail.");
            }
            user.setEmail(email);
        }
        if (body.fullName() != null) {
            user.setFullName(body.fullName().trim());
        }
        if (body.password() != null) {
            auth.setPassword(user, body.password());
        }
        if (body.isSuperuser() != null) {
            user.setSuperuser(body.isSuperuser());
        }
        if (body.isActive() != null) {
            user.setActive(body.isActive());
        }
        return UserDto.of(users.save(user));
    }
}
