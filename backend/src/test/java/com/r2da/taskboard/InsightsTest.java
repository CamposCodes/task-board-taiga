package com.r2da.taskboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

class InsightsTest extends ApiTest {

    @Test
    void historyRecordsOneEntryPerEditWithEveryChangedField() throws Exception {
        Auth owner = newUser(unique("own"));
        Res project = newProject(owner);
        long doing = statusId(project.body(), "us", "Em andamento");
        Res story = newUs(owner, project.id(), "Título original");

        Map<String, Object> edit = new HashMap<>();
        edit.put("subject", "Título novo");
        edit.put("statusId", doing);
        edit.put("dueDate", "2026-11-20");
        edit.put("version", story.body().get("version").asInt());
        patch("/api/v1/items/" + story.id(), edit, owner).expect(200);

        JsonNode history = get("/api/v1/items/" + story.id() + "/history", owner).expect(200).body();
        assertThat(history).hasSize(2);
        JsonNode change = history.get(0);
        assertThat(change.get("kind").asString()).isEqualTo("change");
        assertThat(change.get("diff")).hasSize(3);
        assertThat(change.get("diff").get("subject").get(0).asString()).isEqualTo("Título original");
        assertThat(change.get("diff").get("status").get(0).asString()).isEqualTo("A fazer");
        assertThat(change.get("diff").get("status").get(1).asString()).isEqualTo("Em andamento");
        assertThat(change.get("diff").get("dueDate").get(1).asString()).isEqualTo("2026-11-20");
        assertThat(change.get("target").get("type").asString()).isEqualTo("us");
        assertThat(history.get(1).get("kind").asString()).isEqualTo("create");
    }

    @Test
    void commentsFeedTheActivityAndRecordTheToken() throws Exception {
        Auth owner = newUser(unique("own"));
        long project = newProject(owner).id();
        Res story = newUs(owner, project, "História comentada");
        Auth token = new Auth(null,
                post("/api/v1/tokens", Map.of("name", "bot", "write", true), owner).expect(201).body().get("token")
                        .asString());

        Res comment = post("/api/v1/items/" + story.id() + "/comments", Map.of("body", "Primeiro comentário"), token)
                .expect(201);
        post("/api/v1/items/" + story.id() + "/comments", Map.of("body", " "), owner).expect(400);

        JsonNode activity = get("/api/v1/projects/" + project + "/activity", owner).expect(200).body();
        assertThat(activity.get("total").asLong()).isEqualTo(2);
        JsonNode latest = activity.get("items").get(0);
        assertThat(latest.get("kind").asString()).isEqualTo("comment");
        assertThat(latest.get("viaToken").asBoolean()).isTrue();
        assertThat(latest.get("comment").get("body").asString()).isEqualTo("Primeiro comentário");
        assertThat(latest.get("target").get("ref").asInt()).isEqualTo(story.body().get("ref").asInt());
        assertThat(latest.get("project").get("id").asLong()).isEqualTo(project);
        assertThat(get("/api/v1/items/" + story.id(), owner).body().get("commentsCount").asLong()).isEqualTo(1);

        String otherName = unique("other");
        Auth other = newUser(otherName);
        addMember(owner, project, otherName, "R2DA");
        patch("/api/v1/comments/" + comment.id(), Map.of("body", "invadido"), other).expect(403);
        delete("/api/v1/comments/" + comment.id(), other).expect(403);
        delete("/api/v1/comments/" + comment.id(), owner).expect(204);
        assertThat(get("/api/v1/items/" + story.id() + "/comments", owner).expect(200).body()).isEmpty();
        assertThat(get("/api/v1/projects/" + project + "/activity", owner).body().get("items").get(0).get("comment")
                .isNull()).isTrue();
    }

    @Test
    void searchFindsEveryKindByStemPieceAndRef() throws Exception {
        Auth owner = newUser(unique("own"));
        long project = newProject(owner).id();
        Res story = post("/api/v1/projects/" + project + "/userstories", Map.of("subject", "Planejar entrega",
                "description", "Precisamos revisar as migrações do banco antes da homologação."), owner).expect(201);
        newUs(owner, project, "Outro assunto");
        post("/api/v1/projects/" + project + "/tasks",
                Map.of("subject", "Validar migração em homologação", "parentId", story.id()), owner).expect(201);
        post("/api/v1/projects/" + project + "/issues", Map.of("subject", "Erro na homologação do selo"), owner)
                .expect(201);

        JsonNode byStem = get("/api/v1/projects/" + project + "/search?q={q}", owner, "homologações").expect(200)
                .body();
        assertThat(byStem.get("userstories")).hasSize(1);
        assertThat(byStem.get("userstories").get(0).get("id").asLong()).isEqualTo(story.id());
        assertThat(byStem.get("tasks")).hasSize(1);
        assertThat(byStem.get("issues")).hasSize(1);

        JsonNode byPiece = get("/api/v1/projects/" + project + "/userstories?q={q}", owner, "planej").expect(200)
                .body();
        assertThat(byPiece.get("total").asLong()).isEqualTo(1);

        JsonNode byRef = get("/api/v1/projects/" + project + "/search?q={q}", owner, "#" + story.body().get("ref").asInt())
                .expect(200).body();
        assertThat(byRef.get("userstories").get(0).get("id").asLong()).isEqualTo(story.id());

        JsonNode everything = get("/api/v1/projects/" + project + "/search", owner).expect(200).body();
        assertThat(everything.get("userstories")).hasSize(2);
        assertThat(everything.get("issues")).hasSize(1);
    }

    @Test
    void statsCountOpenClosedBlockedOverdueAndPoints() throws Exception {
        Auth owner = newUser(unique("own"));
        Res project = newProject(owner);
        long done = statusId(project.body(), "us", "Concluído");
        String stories = "/api/v1/projects/" + project.id() + "/userstories";

        post(stories, Map.of("subject", "Aberto", "points", 2), owner).expect(201);
        post(stories, Map.of("subject", "Atrasado", "dueDate", LocalDate.now().minusDays(3).toString(), "points", 3), owner)
                .expect(201);
        post(stories, Map.of("subject", "Bloqueado", "isBlocked", true, "blockedNote", "aguardando acesso"), owner)
                .expect(201);
        post(stories, Map.of("subject", "Fechado", "statusId", done, "points", 5), owner).expect(201);
        post("/api/v1/projects/" + project.id() + "/issues", Map.of("subject", "Um problema"), owner).expect(201);

        JsonNode stats = get("/api/v1/projects/" + project.id() + "/stats", owner).expect(200).body();
        JsonNode totals = stats.get("totals");
        assertThat(totals.get("open").asLong()).isEqualTo(3);
        assertThat(totals.get("closed").asLong()).isEqualTo(1);
        assertThat(totals.get("blocked").asLong()).isEqualTo(1);
        assertThat(totals.get("overdue").asLong()).isEqualTo(1);
        assertThat(totals.get("created7d").asLong()).isEqualTo(4);
        assertThat(totals.get("closed7d").asLong()).isEqualTo(1);
        assertThat(totals.get("pointsOpen").asDecimal()).isEqualByComparingTo("5");
        assertThat(totals.get("pointsClosed").asDecimal()).isEqualByComparingTo("5");
        assertThat(stats.get("byStatus")).hasSize(10);
        assertThat(stats.get("byAssignee").get(0).get("user").isNull()).isTrue();

        JsonNode backlog = get("/api/v1/projects/" + project.id() + "/stats?milestone=none", owner).expect(200).body();
        assertThat(backlog.get("totals").get("open").asLong()).isEqualTo(3);
        get("/api/v1/projects/" + project.id() + "/stats?milestone=abc", owner).expect(400);

        JsonNode overdue = get(stories + "?dueBefore=" + LocalDate.now() + "&closed=false&sort=-due", owner).expect(200)
                .body();
        assertThat(overdue.get("total").asLong()).isEqualTo(1);
        assertThat(overdue.get("items").get(0).get("subject").asString()).isEqualTo("Atrasado");
        assertThat(List.of(overdue.get("page").asInt(), overdue.get("size").asInt())).containsExactly(1, 50);
    }

    @Test
    void wikiPagesAreVersionedAndCountForTheTeam() throws Exception {
        Auth owner = newUser(unique("own"));
        String readerName = unique("read");
        Auth reader = newUser(readerName);
        long project = newProject(owner).id();
        addMember(owner, project, readerName, "Consulta");
        String home = "/api/v1/projects/" + project + "/wiki/home";

        get(home, owner).expect(404);
        call("PUT", home, Map.of("content", "# Início"), reader).expect(403);
        JsonNode created = call("PUT", home, Map.of("content", "# Início\nComo publicar em homologação."), owner)
                .expect(200).body();
        assertThat(created.get("version").asInt()).isZero();
        call("PUT", home, Map.of("content", "sem versão"), owner).expect(400);
        JsonNode saved = call("PUT", home, Map.of("content", "# Início\nNovo texto.", "version", 0), owner).expect(200)
                .body();
        assertThat(saved.get("version").asInt()).isEqualTo(1);
        call("PUT", home, Map.of("content", "edição atrasada", "version", 0), owner).expect(409);
        call("PUT", "/api/v1/projects/" + project + "/wiki/Página Inválida", Map.of("content", "x"), owner).expect(400);

        assertThat(get(home, reader).expect(200).body().get("content").asString()).contains("Novo texto");
        assertThat(get("/api/v1/projects/" + project + "/wiki", reader).expect(200).body()).hasSize(1);
        assertThat(get("/api/v1/projects/" + project + "/search?q={q}", owner, "novo texto").expect(200).body()
                .get("wikiPages").get(0).asString()).isEqualTo("home");

        for (JsonNode member : get("/api/v1/projects/" + project + "/member-stats", owner).expect(200).body()) {
            assertThat(member.get("wikiChanges").asLong())
                    .isEqualTo(member.get("userId").asLong() == userId(owner) ? 2 : 0);
        }
        delete(home, reader).expect(403);
        delete(home, owner).expect(204);
        get(home, owner).expect(404);
    }

    @Test
    void profileAndTimelineShowOnlyWhatTheCallerCanSee() throws Exception {
        String ownerName = unique("own");
        Auth owner = newUser(ownerName);
        Auth stranger = newUser(unique("out"));
        long hidden = newProject(owner).id();
        long open = newProject(owner).id();
        patch("/api/v1/projects/" + open, Map.of("isPrivate", false), owner).expect(200);
        newUs(owner, hidden, "Em projeto privado");
        newUs(owner, open, "Em projeto público");
        patch("/api/v1/me", Map.of("bio", "Cuida das migrações."), owner).expect(200);

        JsonNode profile = get("/api/v1/users/" + ownerName + "/profile", stranger).expect(200).body();
        assertThat(profile.get("bio").asString()).isEqualTo("Cuida das migrações.");
        assertThat(profile.get("projects").asLong()).isEqualTo(2);
        assertThat(profile.get("roles").get(0).asString()).isEqualTo("Product Owner");

        JsonNode own = get("/api/v1/users/" + ownerName + "/timeline", owner).expect(200).body();
        assertThat(own.get("total").asLong()).isEqualTo(2);
        JsonNode seen = get("/api/v1/users/" + ownerName + "/timeline", stranger).expect(200).body();
        assertThat(seen.get("total").asLong()).isEqualTo(1);
        assertThat(seen.get("items").get(0).get("target").get("subject").asString()).isEqualTo("Em projeto público");
        get("/api/v1/users/ninguem-com-esse-nome/profile", stranger).expect(404);
    }
}
