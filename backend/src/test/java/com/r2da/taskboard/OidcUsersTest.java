package com.r2da.taskboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

import com.r2da.taskboard.users.OidcUsers;
import com.r2da.taskboard.users.User;

class OidcUsersTest extends ApiTest {

    @Autowired
    OidcUsers oidcUsers;

    @Test
    void firstLoginCreatesTheAccountAndLaterLoginsReuseIt() {
        String username = unique("sso");
        User created = oidcUsers.provision("sub-" + username, username, username + "@empresa.com", "Maria SSO");

        assertThat(created.getUsername()).isEqualTo(username);
        assertThat(created.getEmail()).isEqualTo(username + "@empresa.com");
        assertThat(created.isSuperuser()).isFalse();

        User again = oidcUsers.provision("sub-" + username, "renamed-at-provider", null, "Maria da Silva");
        assertThat(again.getId()).isEqualTo(created.getId());
        assertThat(again.getUsername()).isEqualTo(username);
        assertThat(again.getFullName()).isEqualTo("Maria da Silva");
    }

    @Test
    void accountCreatedInAdvanceIsAdoptedAndKeepsItsProjects() throws Exception {
        String username = unique("pre");
        Auth local = newUser(username);
        long id = get("/api/v1/me", local).body().get("id").asLong();

        User adopted = oidcUsers.provision("sub-" + username, username.toUpperCase(), null, null);

        assertThat(adopted.getId()).isEqualTo(id);
        assertThat(get("/api/v1/me", local).expect(200).body().get("isSso").asBoolean()).isTrue();
        User other = oidcUsers.provision("other-sub-" + username, username, null, null);
        assertThat(other.getId()).isNotEqualTo(id);
        assertThat(other.getUsername()).isEqualTo(username + "-2");
    }

    @Test
    void localAdministratorIsNeverAdopted() {
        assertThatThrownBy(() -> oidcUsers.provision(unique("sub"), "admin", "x@empresa.com", "Impostor"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("administrador");
    }

    @Test
    void deactivatedAccountCannotLogInThroughTheProvider() throws Exception {
        String username = unique("off");
        User user = oidcUsers.provision("sub-" + username, username, null, null);
        patch("/api/v1/users/" + user.getId(), Map.of("isActive", false), admin()).expect(200);

        assertThatThrownBy(() -> oidcUsers.provision("sub-" + username, username, null, null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("desativado");
    }

    @Test
    void emailAddressesAsUsernamesAreMadeSafe() {
        String name = unique("mail");
        User user = oidcUsers.provision("sub-" + name, name + "@Empresa.com", null, null);
        assertThat(user.getUsername()).isEqualTo(name + "-empresa.com");
        assertThat(user.getEmail()).endsWith("@sem-email.invalid");
    }

    @Test
    void loginPageLearnsThatOidcIsOffWithoutAnIssuer() throws Exception {
        Res config = get("/api/v1/auth/config", null).expect(200);
        assertThat(config.body().get("oidcEnabled").asBoolean()).isFalse();
        assertThat(get("/api/v1/auth/oidc/start/sso", null).status()).isIn(401, 404);
    }
}
