package com.r2da.taskboard.auth;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.WebUtils;

import com.r2da.taskboard.users.OidcUsers;
import com.r2da.taskboard.users.User;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Configuration
public class OidcLogin {

    static final String BASE_PATH = "/api/v1/auth/oidc";
    static final String START_BASE_PATH = BASE_PATH + "/start";
    static final String REGISTRATION_ID = "sso";
    public static final String START_PATH = START_BASE_PATH + "/" + REGISTRATION_ID;
    static final String CALLBACK_PATH = BASE_PATH + "/callback";
    static final String NEXT_COOKIE = "login_next";

    private static final Logger log = LoggerFactory.getLogger(OidcLogin.class);

    private final OidcUsers oidcUsers;
    private final AuthService auth;
    private final String publicUrl;

    OidcLogin(OidcUsers oidcUsers, AuthService auth, @Value("${app.public-url}") String publicUrl) {
        this.oidcUsers = oidcUsers;
        this.auth = auth;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    @Bean
    @ConditionalOnExpression("'${app.oidc.issuer:}' != ''")
    ClientRegistrationRepository clientRegistrationRepository(@Value("${app.oidc.issuer}") String issuer,
            @Value("${app.oidc.client-id}") String clientId, @Value("${app.oidc.client-secret}") String clientSecret) {
        ClientRegistration registration = ClientRegistrations.fromIssuerLocation(issuer)
                .registrationId(REGISTRATION_ID)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .scope("openid", "profile", "email")
                .redirectUri(publicUrl + CALLBACK_PATH)
                .build();
        log.info("Login OIDC habilitado: emissor {}, retorno em {}", issuer, registration.getRedirectUri());
        return new InMemoryClientRegistrationRepository(registration);
    }

    void onSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        OidcUser identity = (OidcUser) authentication.getPrincipal();
        endHandshakeSession(request);
        User user;
        try {
            user = oidcUsers.provision(identity.getSubject(), identity.getPreferredUsername(), identity.getEmail(),
                    identity.getFullName());
        } catch (ResponseStatusException e) {
            backToLogin(response, e.getReason());
            return;
        }
        response.addHeader(HttpHeaders.SET_COOKIE, auth.sessionCookie(user.getId()).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                ResponseCookie.from(NEXT_COOKIE, "").path("/").maxAge(0).build().toString());
        response.sendRedirect(publicUrl + nextPath(request));
    }

    void onFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        log.warn("Falha no login OIDC: {}", exception.getMessage());
        endHandshakeSession(request);
        backToLogin(response, "Não foi possível entrar pelo provedor de identidade. Tente de novo.");
    }

    private static void endHandshakeSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    private void backToLogin(HttpServletResponse response, String message) throws IOException {
        response.sendRedirect(publicUrl + "/login?erro=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
    }

    private static String nextPath(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, NEXT_COOKIE);
        String next = "";
        try {
            next = cookie == null ? "" : URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
        }
        boolean local = next.startsWith("/") && !next.startsWith("//") && !next.contains("\\")
                && next.chars().noneMatch(Character::isISOControl);
        return local ? next : "/projetos";
    }
}
