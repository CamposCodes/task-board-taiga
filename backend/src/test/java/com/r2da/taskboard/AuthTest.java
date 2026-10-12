package com.r2da.taskboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class AuthTest extends ApiTest {

    @Test
    void loginSetsHttpOnlyCookieAndMeAnswers() throws Exception {
        Auth admin = admin();
        assertThat(admin.cookie().isHttpOnly()).isTrue();
        assertThat(admin.cookie().getAttribute("SameSite")).isEqualTo("Lax");

        Res me = get("/api/v1/me", admin).expect(200);
        assertThat(me.body().get("username").asString()).isEqualTo("admin");
        assertThat(me.body().get("isSuperuser").asBoolean()).isTrue();
    }

    @Test
    void anonymousAndWrongPasswordGet401() throws Exception {
        get("/api/v1/me", null).expect(401);
        post("/api/v1/auth/login", Map.of("username", "admin", "password", "wrong-password"), null).expect(401);
    }

    @Test
    void loginLocksAfterFiveFailures() throws Exception {
        String username = unique("lock");
        newUser(username);
        for (int i = 0; i < 5; i++) {
            post("/api/v1/auth/login", Map.of("username", username, "password", "nope-nope"), null).expect(401);
        }
        post("/api/v1/auth/login", Map.of("username", username, "password", PASSWORD), null).expect(429);
    }

    @Test
    void cookieWriteWithoutCustomHeaderIsRejected() throws Exception {
        Auth admin = admin();
        int status = mvc.perform(MockMvcRequestBuilders.post("/api/v1/projects").cookie(admin.cookie())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"csrf\"}")).andReturn().getResponse()
                .getStatus();
        assertThat(status).isEqualTo(403);
    }

    @Test
    void readOnlyTokenCannotWriteAndRevokedTokenGets401() throws Exception {
        Auth user = newUser(unique("tok"));
        Res created = post("/api/v1/tokens", Map.of("name", "leitura"), user).expect(201);
        Auth token = new Auth(null, created.body().get("token").asString());
        assertThat(token.bearer()).startsWith("tb_");

        Res me = get("/api/v1/me", token).expect(200);
        assertThat(me.body().get("viaToken").asBoolean()).isTrue();
        post("/api/v1/projects", Map.of("name", "x"), token).expect(403);
        get("/api/v1/tokens", token).expect(403);

        delete("/api/v1/tokens/" + created.body().get("info").get("id").asLong(), user).expect(204);
        get("/api/v1/me", token).expect(401);
    }

    @Test
    void writeTokenActsAsItsUser() throws Exception {
        Auth user = newUser(unique("wtok"));
        Auth token = new Auth(null,
                post("/api/v1/tokens", Map.of("name", "escrita", "write", true), user).expect(201).body().get("token")
                        .asString());
        Res project = post("/api/v1/projects", Map.of("name", unique("Via token ")), token).expect(201);
        assertThat(project.body().get("my").get("isOwner").asBoolean()).isTrue();
    }

    @Test
    void passwordChangeEndsOtherSessions() throws Exception {
        String username = unique("pwd");
        Auth first = newUser(username);
        Auth second = login(username, PASSWORD);
        Thread.sleep(1100);
        post("/api/v1/me/password", Map.of("currentPassword", PASSWORD, "newPassword", "another-password"), first)
                .expect(204);
        get("/api/v1/me", second).expect(401);
        get("/api/v1/me", login(username, "another-password")).expect(200);
    }

    @Test
    void onlySuperuserManagesUsers() throws Exception {
        Auth user = newUser(unique("plain"));
        get("/api/v1/users", user).expect(403);
        post("/api/v1/users", Map.of("username", "bad name", "email", "not-an-email", "fullName", "", "password", "x"),
                admin()).expect(400);
    }
}
