package com.r2da.taskboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = { "app.admin-password=admin12345",
        "app.jwt-secret=test-secret-0123456789abcdef0123456789", "app.cookie-secure=false",
        "app.files-dir=build/test-files", "spring.mail.host=test", "app.mail.poll=1d" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
abstract class ApiTest {

    protected static final String PASSWORD = "password123";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JsonMapper json;

    protected record Auth(Cookie cookie, String bearer) {
    }

    protected record Res(int status, JsonNode body) {

        Res expect(int expected) {
            assertThat(status).as("%s", body).isEqualTo(expected);
            return this;
        }

        long id() {
            return body.get("id").asLong();
        }
    }

    protected Res call(String method, String uri, Object body, Auth auth, Object... uriVariables) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), uri,
                uriVariables);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        if (auth != null && auth.cookie() != null) {
            request.cookie(auth.cookie()).header("X-Requested-With", "test");
        }
        if (auth != null && auth.bearer() != null) {
            request.header("Authorization", "Bearer " + auth.bearer());
        }
        MockHttpServletResponse response = mvc.perform(request).andReturn().getResponse();
        String content = response.getContentAsString(StandardCharsets.UTF_8);
        return new Res(response.getStatus(), content.isEmpty() ? json.nullNode() : json.readTree(content));
    }

    protected Res get(String uri, Auth auth, Object... uriVariables) throws Exception {
        return call("GET", uri, null, auth, uriVariables);
    }

    protected Res post(String uri, Object body, Auth auth) throws Exception {
        return call("POST", uri, body, auth);
    }

    protected Res patch(String uri, Object body, Auth auth) throws Exception {
        return call("PATCH", uri, body, auth);
    }

    protected Res delete(String uri, Auth auth) throws Exception {
        return call("DELETE", uri, null, auth);
    }

    protected Auth login(String username, String password) throws Exception {
        MockHttpServletResponse response = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        return new Auth(response.getCookie("access_token"), null);
    }

    protected Auth admin() throws Exception {
        return login("admin", "admin12345");
    }

    protected static String unique(String prefix) {
        return prefix + SEQUENCE.incrementAndGet() + "x" + System.nanoTime() % 1_000_000;
    }

    protected Auth newUser(String username) throws Exception {
        post("/api/v1/users", Map.of("username", username, "email", username + "@example.com", "fullName",
                "Pessoa " + username, "password", PASSWORD), admin()).expect(201);
        return login(username, PASSWORD);
    }

    protected Res newProject(Auth owner) throws Exception {
        return post("/api/v1/projects", Map.of("name", unique("Projeto ")), owner).expect(201);
    }

    protected Res newUs(Auth auth, long projectId, String subject) throws Exception {
        return post("/api/v1/projects/" + projectId + "/userstories", Map.of("subject", subject), auth).expect(201);
    }

    protected void addMember(Auth admin, long projectId, String username, String role) throws Exception {
        JsonNode project = get("/api/v1/projects/" + projectId, admin).expect(200).body();
        post("/api/v1/projects/" + projectId + "/members", Map.of("user", username, "roleId", roleId(project, role)),
                admin).expect(201);
    }

    protected static long roleId(JsonNode project, String name) {
        return named(project.get("roles"), name);
    }

    protected static long named(JsonNode list, String name) {
        for (JsonNode entry : list) {
            if (entry.get("name").asString().equals(name)) {
                return entry.get("id").asLong();
            }
        }
        throw new AssertionError(name + " not found in " + list);
    }

    protected long userId(Auth auth) throws Exception {
        return get("/api/v1/me", auth).expect(200).body().get("id").asLong();
    }

    protected Res upload(long itemId, String fileName, String contentType, byte[] content, Auth auth)
            throws Exception {
        MockHttpServletResponse response = mvc.perform(MockMvcRequestBuilders
                .multipart("/api/v1/items/" + itemId + "/attachments")
                .file(new MockMultipartFile("file", fileName, contentType, content))
                .cookie(auth.cookie()).header("X-Requested-With", "test")).andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        return new Res(response.getStatus(), body.isEmpty() ? json.nullNode() : json.readTree(body));
    }

    protected MockHttpServletResponse download(long attachmentId, Auth auth) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get("/api/v1/attachments/" + attachmentId + "/download")
                .cookie(auth.cookie())).andReturn().getResponse();
    }

    protected static List<Long> ids(JsonNode array) {
        List<Long> ids = new ArrayList<>();
        array.forEach(node -> ids.add(node.get("id").asLong()));
        return ids;
    }

    protected static long statusId(JsonNode project, String kind, String name) {
        for (JsonNode status : project.get("statuses")) {
            if (status.get("kind").asString().equals(kind) && status.get("name").asString().equals(name)) {
                return status.get("id").asLong();
            }
        }
        throw new AssertionError("status " + kind + "/" + name + " not found in " + project);
    }
}
