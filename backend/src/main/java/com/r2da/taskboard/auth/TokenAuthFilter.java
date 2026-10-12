package com.r2da.taskboard.auth;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.util.WebUtils;

import com.r2da.taskboard.common.Problems;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class TokenAuthFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final String BEARER = "Bearer ";

    private final AuthService auth;
    private final HandlerExceptionResolver resolver;

    TokenAuthFilter(AuthService auth, HandlerExceptionResolver resolver) {
        this.auth = auth;
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        Cookie cookie = WebUtils.getCookie(request, AuthService.COOKIE);
        boolean unsafe = !SAFE_METHODS.contains(request.getMethod());

        String token = null;
        if (header != null) {
            if (header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
                token = header.substring(BEARER.length()).trim();
            }
        } else if (cookie != null) {
            if (unsafe && request.getHeader("X-Requested-With") == null) {
                reject(request, response, "Requisição sem o cabeçalho X-Requested-With.");
                return;
            }
            token = cookie.getValue();
        }

        AuthService.Resolved resolved = token == null || token.isEmpty() ? null : auth.resolve(token);
        if (resolved != null) {
            CurrentUser user = resolved.user();
            if (user.viaToken() && unsafe && !user.scopes().contains("write")) {
                reject(request, response, "Este token de API é somente leitura.");
                return;
            }
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new PreAuthenticatedAuthenticationToken(user, null, List.of()));
            SecurityContextHolder.setContext(context);
            if (header == null && auth.shouldRenew(resolved.issuedAt())) {
                response.addHeader(HttpHeaders.SET_COOKIE, auth.sessionCookie(user.id()).toString());
            }
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String detail) {
        resolver.resolveException(request, response, null, Problems.forbidden(detail));
    }
}
