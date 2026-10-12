package com.r2da.taskboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

class ProjectsTest extends ApiTest {

    @Test
    void newProjectHasSlugOwnerRolesAndValueLists() throws Exception {
        Auth owner = newUser(unique("own"));
        Res project = post("/api/v1/projects", Map.of("name", "Migração Cartório 2026"), owner).expect(201);

        JsonNode body = project.body();
        assertThat(body.get("slug").asString()).startsWith("migracao-cartorio-2026");
        assertThat(body.get("isPrivate").asBoolean()).isTrue();
        assertThat(body.get("my").get("isOwner").asBoolean()).isTrue();
        assertThat(body.get("my").get("roleName").asString()).isEqualTo("Product Owner");
        assertThat(body.get("my").get("permissions")).hasSize(24);
        assertThat(body.get("statuses")).hasSize(30);
        assertThat(body.get("members")).hasSize(1);
        assertThat(body.get("roles")).hasSize(4);
        assertThat(body.get("points")).hasSize(12);
        assertThat(body.get("issueTypes")).hasSize(3);
        assertThat(body.get("severities")).hasSize(4);
        assertThat(body.get("priorities")).hasSize(3);
        get("/api/v1/projects/" + body.get("slug").asString(), owner).expect(200);

        assertThat(post("/api/v1/projects", Map.of("name", "New"), owner).expect(201).body().get("slug").asString())
                .isNotEqualTo("new");
    }

    @Test
    void templatesChooseTheModulesAndAdminsToggleThem() throws Exception {
        Auth owner = newUser(unique("own"));
        JsonNode scrum = post("/api/v1/projects", Map.of("name", unique("Scrum "), "template", "scrum"), owner)
                .expect(201).body();
        assertThat(scrum.get("isBacklogActivated").asBoolean()).isTrue();
        assertThat(scrum.get("isKanbanActivated").asBoolean()).isFalse();
        JsonNode kanban = post("/api/v1/projects", Map.of("name", unique("Kanban "), "template", "kanban"), owner)
                .expect(201).body();
        assertThat(kanban.get("isBacklogActivated").asBoolean()).isFalse();
        post("/api/v1/projects", Map.of("name", "x", "template", "waterfall"), owner).expect(400);

        long id = kanban.get("id").asLong();
        JsonNode changed = patch("/api/v1/projects/" + id, Map.of("isIssuesActivated", false, "isBacklogActivated",
                true), owner).expect(200).body();
        assertThat(changed.get("isIssuesActivated").asBoolean()).isFalse();
        assertThat(changed.get("isBacklogActivated").asBoolean()).isTrue();
    }

    @Test
    void privateProjectIsInvisibleToOutsidersAndPublicIsReadOnly() throws Exception {
        Auth owner = newUser(unique("own"));
        Auth outsider = newUser(unique("out"));
        long id = newProject(owner).id();

        get("/api/v1/projects/" + id, outsider).expect(404);
        get("/api/v1/projects/" + id + "/userstories", outsider).expect(404);

        patch("/api/v1/projects/" + id, Map.of("isPrivate", false), owner).expect(200);
        JsonNode my = get("/api/v1/projects/" + id, outsider).expect(200).body().get("my");
        assertThat(my.get("isMember").asBoolean()).isFalse();
        assertThat(names(my.get("permissions"))).contains("view_us", "view_issues").doesNotContain("add_us");
        post("/api/v1/projects/" + id + "/userstories", Map.of("subject", "x"), outsider).expect(403);
    }

    @Test
    void rolesLimitWhatEachMemberCanDo() throws Exception {
        Auth owner = newUser(unique("own"));
        String readerName = unique("read");
        String teamName = unique("team");
        Auth reader = newUser(readerName);
        Auth team = newUser(teamName);
        Res project = newProject(owner);
        long id = project.id();
        addMember(owner, id, readerName, "Consulta");
        addMember(owner, id, teamName, "R2DA");

        post("/api/v1/projects/" + id + "/userstories", Map.of("subject", "x"), reader).expect(403);
        long story = newUs(team, id, "História da equipe").id();
        get("/api/v1/items/" + story, reader).expect(200);
        post("/api/v1/items/" + story + "/comments", Map.of("body", "sem permissão"), reader).expect(403);

        Map<String, Object> sprint = Map.of("name", "Sprint 1", "estimatedStart", LocalDate.now().toString(),
                "estimatedFinish", LocalDate.now().plusDays(7).toString());
        post("/api/v1/projects/" + id + "/milestones", sprint, team).expect(403);
        Map<String, Object> status = Map.of("kind", "us", "name", "Revisão", "color", "#123456");
        post("/api/v1/projects/" + id + "/statuses", status, team).expect(403);
        post("/api/v1/projects/" + id + "/statuses", status, owner).expect(201);
        post("/api/v1/projects/" + id + "/members",
                Map.of("user", "admin", "roleId", roleId(project.body(), "R2DA")), team).expect(403);
        delete("/api/v1/projects/" + id, team).expect(403);

        patch("/api/v1/projects/" + id + "/members/" + userId(reader), Map.of("isAdmin", true), owner).expect(200);
        post("/api/v1/projects/" + id + "/milestones", sprint, reader).expect(201);
        delete("/api/v1/projects/" + id, reader).expect(403);
        delete("/api/v1/projects/" + id + "/members/" + userId(owner), reader).expect(409);
    }

    @Test
    void customRoleGrantsOnlyItsPermissions() throws Exception {
        Auth owner = newUser(unique("own"));
        String name = unique("bugs");
        Auth reporter = newUser(name);
        long id = newProject(owner).id();

        JsonNode role = post("/api/v1/projects/" + id + "/roles",
                Map.of("name", "Só problemas", "permissions", List.of("add_issue")), owner).expect(201).body();
        assertThat(names(role.get("permissions"))).containsExactlyInAnyOrder("view_project", "view_issues",
                "add_issue");
        post("/api/v1/projects/" + id + "/members", Map.of("user", name, "roleId", role.get("id").asLong()), owner)
                .expect(201);

        post("/api/v1/projects/" + id + "/issues", Map.of("subject", "Erro na tela"), reporter).expect(201);
        post("/api/v1/projects/" + id + "/userstories", Map.of("subject", "x"), reporter).expect(403);
        get("/api/v1/projects/" + id + "/userstories", reporter).expect(403);

        delete("/api/v1/roles/" + role.get("id").asLong(), owner).expect(409);
        patch("/api/v1/roles/" + role.get("id").asLong(), Map.of("permissions", List.of("view_us")), owner)
                .expect(200);
        get("/api/v1/projects/" + id + "/userstories", reporter).expect(200);
        post("/api/v1/projects/" + id + "/issues", Map.of("subject", "Outro erro"), reporter).expect(403);
    }

    @Test
    void archivedProjectIsReadOnlyUntilUnarchived() throws Exception {
        Auth owner = newUser(unique("own"));
        long id = newProject(owner).id();

        post("/api/v1/projects/" + id + "/archive", null, owner).expect(200);
        post("/api/v1/projects/" + id + "/userstories", Map.of("subject", "x"), owner).expect(409);
        get("/api/v1/projects/" + id, owner).expect(200);
        assertThat(ids(get("/api/v1/projects", owner).expect(200).body())).doesNotContain(id);
        assertThat(ids(get("/api/v1/projects?archived=true", owner).expect(200).body())).contains(id);

        post("/api/v1/projects/" + id + "/unarchive", null, owner).expect(200);
        newUs(owner, id, "de volta");
    }

    @Test
    void statusInUseCannotBeDeletedAndReorderChangesTheBoard() throws Exception {
        Auth owner = newUser(unique("own"));
        Res project = newProject(owner);
        long id = project.id();
        long first = statusId(project.body(), "us", "A fazer");
        newUs(owner, id, "ocupa o primeiro status");

        delete("/api/v1/statuses/" + first, owner).expect(409);

        List<Long> order = new ArrayList<>();
        for (JsonNode status : project.body().get("statuses")) {
            if (status.get("kind").asString().equals("us")) {
                order.add(status.get("id").asLong());
            }
        }
        Collections.reverse(order);
        Res reordered = post("/api/v1/projects/" + id + "/statuses/reorder", Map.of("kind", "us", "ids", order), owner)
                .expect(200);
        assertThat(reordered.body().get(0).get("name").asString()).isEqualTo("Arquivado");
        assertThat(reordered.body().get(0).get("isArchived").asBoolean()).isTrue();
        assertThat(reordered.body().get(order.size() - 1).get("name").asString()).isEqualTo("A fazer");
    }

    @Test
    void valueListsAreEditedByAdmins() throws Exception {
        Auth owner = newUser(unique("own"));
        Res project = newProject(owner);
        long id = project.id();

        JsonNode added = post("/api/v1/projects/" + id + "/choices",
                Map.of("kind", "points", "name", "100", "value", 100), owner).expect(201).body();
        assertThat(added.get("value").asDecimal()).isEqualByComparingTo("100");
        patch("/api/v1/choices/" + added.get("id").asLong(), Map.of("name", "Enorme"), owner).expect(200);
        assertThat(get("/api/v1/projects/" + id, owner).body().get("points")).hasSize(13);
        delete("/api/v1/choices/" + added.get("id").asLong(), owner).expect(204);

        long question = named(project.body().get("issueTypes"), "Pergunta");
        long issue = post("/api/v1/projects/" + id + "/issues", Map.of("subject", "Dúvida", "typeId", question), owner)
                .expect(201).id();
        delete("/api/v1/choices/" + question, owner).expect(204);
        assertThat(get("/api/v1/items/" + issue, owner).expect(200).body().get("issueType").isNull()).isTrue();
    }

    @Test
    void likesWatchersAndPersonalOrder() throws Exception {
        Auth owner = newUser(unique("own"));
        long first = newProject(owner).id();
        long second = newProject(owner).id();

        JsonNode liked = post("/api/v1/projects/" + first + "/like", null, owner).expect(200).body();
        assertThat(liked.get("likes").asLong()).isEqualTo(1);
        assertThat(liked.get("my").get("liked").asBoolean()).isTrue();
        post("/api/v1/projects/" + first + "/like", null, owner).expect(200);
        assertThat(delete("/api/v1/projects/" + first + "/like", owner).expect(200).body().get("likes").asLong())
                .isZero();
        assertThat(post("/api/v1/projects/" + first + "/watch", null, owner).expect(200).body().get("watchers")
                .asLong()).isEqualTo(1);

        post("/api/v1/projects/order", Map.of("ids", List.of(second, first)), owner).expect(204);
        assertThat(ids(get("/api/v1/projects", owner).expect(200).body())).startsWith(second, first);
    }

    private static List<String> names(JsonNode array) {
        List<String> names = new ArrayList<>();
        array.forEach(node -> names.add(node.asString()));
        return names;
    }
}
