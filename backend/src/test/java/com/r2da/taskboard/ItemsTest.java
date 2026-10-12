package com.r2da.taskboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.JsonNode;

class ItemsTest extends ApiTest {

    @Test
    void refsAreUniqueAndSequentialUnderConcurrency() throws Exception {
        Auth owner = newUser(unique("own"));
        long project = newProject(owner).id();
        int threads = 8;
        int perThread = 5;
        Set<Integer> refs = new ConcurrentSkipListSet<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < perThread; i++) {
                        refs.add(newUs(owner, project, "concorrente").body().get("ref").asInt());
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        }

        Set<Integer> expected = new TreeSet<>();
        for (int ref = 1; ref <= threads * perThread; ref++) {
            expected.add(ref);
        }
        assertThat(refs).isEqualTo(expected);
    }

    @Test
    void updateRequiresTheCurrentVersionAndKnownFields() throws Exception {
        Auth owner = newUser(unique("own"));
        long project = newProject(owner).id();
        Res story = newUs(owner, project, "Versão");
        String uri = "/api/v1/items/" + story.id();
        int version = story.body().get("version").asInt();

        patch(uri, Map.of("subject", "sem versão"), owner).expect(400);
        Res updated = patch(uri, Map.of("subject", "Primeira edição", "version", version), owner).expect(200);
        assertThat(updated.body().get("version").asInt()).isEqualTo(version + 1);
        patch(uri, Map.of("subject", "Edição atrasada", "version", version), owner).expect(409);
        patch(uri, Map.of("subjetc", "campo errado", "version", version + 1), owner).expect(400);
        patch(uri, Map.of("isIocaine", true, "version", version + 1), owner).expect(400);
        patch(uri, Map.of("teamRequirement", true, "version", version + 1), owner).expect(200);
    }

    @Test
    void nullClearsNullableFieldsAndAbsentFieldsStay() throws Exception {
        Auth owner = newUser(unique("own"));
        String otherName = unique("other");
        Auth other = newUser(otherName);
        long project = newProject(owner).id();
        addMember(owner, project, otherName, "R2DA");
        long ownerId = userId(owner);
        String stories = "/api/v1/projects/" + project + "/userstories";

        Map<String, Object> create = new HashMap<>();
        create.put("subject", "Com responsável");
        create.put("assignedTo", ownerId);
        create.put("dueDate", "2026-12-31");
        create.put("points", 3);
        create.put("tags", List.of("urgente", "backend"));
        Res story = post(stories, create, owner).expect(201);
        assertThat(story.body().get("assignedTo").get("id").asLong()).isEqualTo(ownerId);

        JsonNode shared = patch("/api/v1/items/" + story.id(), Map.of("assignedUsers", List.of(userId(other), ownerId),
                "version", story.body().get("version").asInt()), owner).expect(200).body();
        assertThat(ids(shared.get("assignedUsers"))).containsExactly(userId(other), ownerId);
        assertThat(get(stories + "?assignee=" + otherName, owner).body().get("total").asLong()).isEqualTo(1);
        assertThat(get(stories + "?assignee=me", owner).body().get("total").asLong()).isEqualTo(1);

        Map<String, Object> clear = new HashMap<>();
        clear.put("assignedTo", null);
        clear.put("version", shared.get("version").asInt());
        JsonNode updated = patch("/api/v1/items/" + story.id(), clear, owner).expect(200).body();
        assertThat(updated.get("assignedTo").isNull()).isTrue();
        assertThat(updated.get("assignedUsers")).isEmpty();
        assertThat(updated.get("dueDate").asString()).isEqualTo("2026-12-31");
        assertThat(updated.get("tags")).hasSize(2);

        assertThat(get(stories + "?tag=urgente", owner).expect(200).body().get("total").asLong()).isEqualTo(1);
        assertThat(get(stories + "?assignee=none", owner).body().get("total").asLong()).isEqualTo(1);
        Auth outsider = newUser(unique("out"));
        patch("/api/v1/items/" + story.id(), Map.of("assignedTo", userId(outsider), "version",
                updated.get("version").asInt()), owner).expect(400);
    }

    @Test
    void moveChangesColumnAndPosition() throws Exception {
        Auth owner = newUser(unique("own"));
        Res project = newProject(owner);
        long doing = statusId(project.body(), "us", "Em andamento");
        long done = statusId(project.body(), "us", "Concluído");
        long a = newUs(owner, project.id(), "A").id();
        long b = newUs(owner, project.id(), "B").id();
        long c = newUs(owner, project.id(), "C").id();

        post("/api/v1/items/" + a + "/move", Map.of("statusId", doing), owner).expect(200);
        post("/api/v1/items/" + b + "/move", Map.of("statusId", doing), owner).expect(200);
        post("/api/v1/items/" + c + "/move", Map.of("statusId", doing, "beforeId", a), owner).expect(200);
        assertThat(column(owner, project.id(), doing)).containsExactly(c, a, b);

        int versionBefore = get("/api/v1/items/" + b, owner).body().get("version").asInt();
        Res moved = post("/api/v1/items/" + b + "/move", Map.of("beforeId", c), owner).expect(200);
        assertThat(column(owner, project.id(), doing)).containsExactly(b, c, a);
        assertThat(moved.body().get("version").asInt()).isEqualTo(versionBefore);
        post("/api/v1/items/" + b + "/move", Map.of("beforeId", 999_999_999), owner).expect(400);

        JsonNode closed = post("/api/v1/items/" + a + "/move", Map.of("statusId", done), owner).expect(200).body();
        assertThat(closed.get("finishedAt").isNull()).isFalse();
        assertThat(closed.get("status").get("isClosed").asBoolean()).isTrue();
        String stories = "/api/v1/projects/" + project.id() + "/userstories";
        assertThat(get(stories + "?closed=true", owner).body().get("total").asLong()).isEqualTo(1);
        assertThat(get(stories + "?closed=false", owner).body().get("total").asLong()).isEqualTo(2);

        long archived = statusId(project.body(), "us", "Arquivado");
        post("/api/v1/items/" + a + "/move", Map.of("statusId", archived), owner).expect(200);
        assertThat(pageIds(get(stories + "?archived=false", owner))).containsExactlyInAnyOrder(b, c);
        assertThat(pageIds(get(stories + "?archived=true", owner))).containsExactly(a);
    }

    @Test
    void sprintsHoldStoriesAndTheBacklogKeepsItsOwnOrder() throws Exception {
        Auth owner = newUser(unique("own"));
        long project = newProject(owner).id();
        String stories = "/api/v1/projects/" + project + "/userstories";
        JsonNode sprint = post("/api/v1/projects/" + project + "/milestones", Map.of("name", "Sprint 1 - 09/10 a 16/10",
                "estimatedStart", LocalDate.now().toString(), "estimatedFinish", LocalDate.now().plusDays(7).toString()),
                owner).expect(201).body();
        long sprintId = sprint.get("id").asLong();
        assertThat(sprint.get("slug").asString()).isEqualTo("sprint-1-09-10-a-16-10");
        get("/api/v1/projects/" + project + "/milestones/by-slug/sprint-1-09-10-a-16-10", owner).expect(200);

        long first = newUs(owner, project, "Na sprint").id();
        long second = newUs(owner, project, "Também na sprint").id();
        long third = newUs(owner, project, "No backlog").id();
        long fourth = newUs(owner, project, "No backlog, depois no topo").id();

        Res bulk = post(stories + "/bulk-milestone", Map.of("ids", List.of(first, second), "milestoneId", sprintId),
                owner).expect(200);
        assertThat(bulk.body().get("changed").asInt()).isEqualTo(2);
        assertThat(pageIds(get(stories + "?milestone=" + sprintId + "&sort=list", owner))).containsExactly(first, second);

        post("/api/v1/items/" + fourth + "/move", Map.of("order", "list", "beforeId", third), owner).expect(200);
        assertThat(pageIds(get(stories + "?milestone=none&sort=list", owner))).containsExactly(fourth, third);

        Map<String, Object> drop = new HashMap<>();
        drop.put("order", "list");
        drop.put("milestoneId", sprintId);
        drop.put("beforeId", first);
        post("/api/v1/items/" + third + "/move", drop, owner).expect(200);
        assertThat(pageIds(get(stories + "?milestone=" + sprintId + "&sort=list", owner)))
                .containsExactly(third, first, second);

        JsonNode listed = get("/api/v1/projects/" + project + "/milestones", owner).expect(200).body();
        assertThat(listed.get(0).get("usOpen").asLong()).isEqualTo(3);

        post("/api/v1/milestones/" + sprintId + "/close", null, owner).expect(200);
        Map<String, Object> intoClosed = new HashMap<>();
        intoClosed.put("milestoneId", sprintId);
        intoClosed.put("version", 0);
        patch("/api/v1/items/" + fourth, intoClosed, owner).expect(400);
        assertThat(get("/api/v1/items/" + first, owner).body().get("milestone").get("id").asLong())
                .isEqualTo(sprintId);
    }

    @Test
    void tasksBelongToAStoryOrToASprint() throws Exception {
        Auth owner = newUser(unique("own"));
        Res project = newProject(owner);
        long id = project.id();
        long done = statusId(project.body(), "task", "Concluído");
        String tasks = "/api/v1/projects/" + id + "/tasks";
        Res story = newUs(owner, id, "História com tarefas");

        Res first = post(tasks, Map.of("subject", "Primeira", "parentId", story.id()), owner).expect(201);
        Res second = post(tasks, Map.of("subject", "Segunda", "parentId", story.id()), owner).expect(201);
        assertThat(first.body().get("ref").asInt()).isEqualTo(story.body().get("ref").asInt() + 1);
        assertThat(first.body().get("parent").get("ref").asInt()).isEqualTo(story.body().get("ref").asInt());
        post(tasks, Map.of("subject", "x", "parentId", story.id(), "points", 3), owner).expect(400);
        post(tasks, Map.of("subject", "x", "parentId", first.id()), owner).expect(400);
        post(tasks, Map.of("subject", "sem história nem sprint"), owner).expect(400);

        post("/api/v1/items/" + second.id() + "/move", Map.of("order", "list", "beforeId", first.id()), owner)
                .expect(200);
        patch("/api/v1/items/" + first.id(), Map.of("statusId", done, "isIocaine", true, "version", 0), owner)
                .expect(200);

        JsonNode detail = get("/api/v1/items/" + story.id(), owner).expect(200).body();
        assertThat(detail.get("tasksTotal").asLong()).isEqualTo(2);
        assertThat(detail.get("tasksDone").asLong()).isEqualTo(1);
        assertThat(detail.get("tasks").get(0).get("subject").asString()).isEqualTo("Segunda");
        assertThat(get(tasks + "?closed=false", owner).body().get("total").asLong()).isEqualTo(1);

        long sprint = post("/api/v1/projects/" + id + "/milestones", Map.of("name", "Sprint 1", "estimatedStart",
                LocalDate.now().toString(), "estimatedFinish", LocalDate.now().plusDays(7).toString()), owner)
                .expect(201).id();
        post("/api/v1/projects/" + id + "/userstories/bulk-milestone",
                Map.of("ids", List.of(story.id()), "milestoneId", sprint), owner).expect(200);
        long loose = post(tasks, Map.of("subject", "Sem história", "milestoneId", sprint), owner).expect(201).id();
        assertThat(pageIds(get(tasks + "?milestone=" + sprint, owner)))
                .containsExactlyInAnyOrder(first.id(), second.id(), loose);
        JsonNode sprintStats = get("/api/v1/projects/" + id + "/stats?milestone=" + sprint, owner).expect(200).body();
        assertThat(sprintStats.get("tasks").get("open").asLong()).isEqualTo(2);
        assertThat(sprintStats.get("tasks").get("closed").asLong()).isEqualTo(1);
        assertThat(sprintStats.get("tasks").get("iocaine").asLong()).isEqualTo(1);

        long otherStory = newUs(owner, id, "Outra história").id();
        Map<String, Object> drag = Map.of("statusId", done, "parentId", otherStory);
        JsonNode dragged = post("/api/v1/items/" + loose + "/move", drag, owner).expect(200).body();
        assertThat(dragged.get("parent").get("id").asLong()).isEqualTo(otherStory);
        assertThat(dragged.get("milestone").isNull()).isTrue();

        delete("/api/v1/items/" + story.id(), owner).expect(204);
        get("/api/v1/items/" + first.id(), owner).expect(404);
    }

    @Test
    void issuesHaveTypeSeverityAndPriority() throws Exception {
        Auth owner = newUser(unique("own"));
        Res project = newProject(owner);
        long id = project.id();
        String issues = "/api/v1/projects/" + id + "/issues";

        JsonNode issue = post(issues, Map.of("subject", "Erro ao salvar"), owner).expect(201).body();
        assertThat(issue.get("type").asString()).isEqualTo("issue");
        assertThat(issue.get("issueType").get("name").asString()).isEqualTo("Bug");
        assertThat(issue.get("severity").get("name").asString()).isEqualTo("Normal");
        assertThat(issue.get("priority").get("name").asString()).isEqualTo("Normal");
        assertThat(issue.get("status").get("name").asString()).isEqualTo("A fazer");

        long critical = named(project.body().get("severities"), "Crítica");
        JsonNode updated = patch("/api/v1/items/" + issue.get("id").asLong(),
                Map.of("severityId", critical, "version", issue.get("version").asInt()), owner).expect(200).body();
        assertThat(updated.get("severity").get("name").asString()).isEqualTo("Crítica");
        patch("/api/v1/items/" + issue.get("id").asLong(), Map.of("severityId",
                named(project.body().get("priorities"), "Alta"), "version", updated.get("version").asInt()), owner)
                .expect(400);

        JsonNode history = get("/api/v1/items/" + issue.get("id").asLong() + "/history", owner).expect(200).body();
        assertThat(history.get(0).get("diff").get("severity").get(1).asString()).isEqualTo("Crítica");

        Res story = newUs(owner, id, "História");
        assertThat(get("/api/v1/projects/" + id + "/items/by-ref/" + issue.get("ref").asInt(), owner).expect(200).body()
                .get("type").asString()).isEqualTo("issue");
        assertThat(get("/api/v1/projects/" + id + "/items/by-ref/" + story.body().get("ref").asInt(), owner)
                .expect(200).body().get("type").asString()).isEqualTo("us");
        get("/api/v1/projects/" + id + "/items/by-ref/9999", owner).expect(404);
        assertThat(get(issues, owner).expect(200).body().get("total").asLong()).isEqualTo(1);
    }

    @Test
    void bulkInsertCreatesOneItemPerLine() throws Exception {
        Auth owner = newUser(unique("own"));
        Res project = newProject(owner);
        long doing = statusId(project.body(), "us", "Em andamento");
        String stories = "/api/v1/projects/" + project.id() + "/userstories";
        long existing = post(stories, Map.of("subject", "Já estava", "statusId", doing), owner).expect(201).id();

        JsonNode created = post(stories + "/bulk",
                Map.of("text", "Primeira\n\n  Segunda  \nTerceira", "statusId", doing, "position", "top"), owner)
                .expect(201).body();
        assertThat(created).hasSize(3);
        assertThat(created.get(1).get("subject").asString()).isEqualTo("Segunda");
        List<Long> column = column(owner, project.id(), doing);
        assertThat(column).hasSize(4);
        assertThat(column.subList(0, 3)).isEqualTo(ids(created));
        assertThat(column.get(3)).isEqualTo(existing);

        post(stories + "/bulk", Map.of("text", "  \n "), owner).expect(400);
        post(stories + "/bulk", Map.of("statusId", doing), owner).expect(400);
    }

    @Test
    void watchersAndAssigneesAreNotified() throws Exception {
        Auth owner = newUser(unique("own"));
        String devName = unique("dev");
        Auth dev = newUser(devName);
        long project = newProject(owner).id();
        addMember(owner, project, devName, "R2DA");

        Res story = post("/api/v1/projects/" + project + "/userstories",
                Map.of("subject", "Atribuída na criação", "assignedTo", userId(dev)), owner).expect(201);
        JsonNode first = get("/api/v1/notifications", dev).expect(200).body();
        assertThat(first.get("unread").asLong()).isEqualTo(1);
        assertThat(first.get("items").get(0).get("kind").asString()).isEqualTo("assigned");
        assertThat(first.get("items").get(0).get("itemRef").asInt()).isEqualTo(story.body().get("ref").asInt());
        assertThat(first.get("items").get(0).get("project").get("id").asLong()).isEqualTo(project);
        assertThat(get("/api/v1/notifications", owner).body().get("total").asLong()).isZero();

        JsonNode watched = post("/api/v1/items/" + story.id() + "/watch", null, owner).expect(200).body();
        assertThat(ids(watched.get("watchers"))).containsExactly(userId(owner));
        assertThat(watched.get("version").asInt()).isEqualTo(story.body().get("version").asInt());

        post("/api/v1/items/" + story.id() + "/comments", Map.of("body", "Alguma novidade?"), dev).expect(201);
        JsonNode forOwner = get("/api/v1/notifications?unread=true", owner).expect(200).body();
        assertThat(forOwner.get("total").asLong()).isEqualTo(1);
        assertThat(forOwner.get("items").get(0).get("kind").asString()).isEqualTo("comment");
        assertThat(forOwner.get("items").get(0).get("actor").get("username").asString()).isEqualTo(devName);

        post("/api/v1/notifications/read", null, owner).expect(204);
        assertThat(get("/api/v1/notifications", owner).body().get("unread").asLong()).isZero();
        assertThat(get("/api/v1/notifications", owner).body().get("total").asLong()).isEqualTo(1);

        JsonNode dashboard = get("/api/v1/me/dashboard", dev).expect(200).body();
        assertThat(ids(dashboard.get("workingOn"))).containsExactly(story.id());
        assertThat(ids(get("/api/v1/me/dashboard", owner).body().get("watching"))).containsExactly(story.id());

        assertThat(delete("/api/v1/items/" + story.id() + "/watch", owner).expect(200).body().get("watchers")).isEmpty();
        delete("/api/v1/projects/" + project + "/members/" + userId(dev), dev).expect(204);
        assertThat(get("/api/v1/notifications", dev).body().get("total").asLong()).isZero();
        assertThat(get("/api/v1/items/" + story.id(), owner).expect(200).body().get("assignedUsers")).isEmpty();
    }

    @Test
    void attachmentsAreStoredAndServedBack() throws Exception {
        Auth owner = newUser(unique("own"));
        String readerName = unique("read");
        Auth reader = newUser(readerName);
        Auth outsider = newUser(unique("out"));
        long project = newProject(owner).id();
        addMember(owner, project, readerName, "Consulta");
        long story = newUs(owner, project, "Com anexo").id();
        byte[] content = "conteúdo do anexo".getBytes(StandardCharsets.UTF_8);

        upload(story, "notas.txt", "text/plain", content, reader).expect(403);
        Res uploaded = upload(story, "../../relatório final.html", "text/html", content, owner).expect(201);
        assertThat(uploaded.body().get("name").asString()).isEqualTo("relatório final.html");
        assertThat(uploaded.body().get("size").asLong()).isEqualTo(content.length);
        assertThat(get("/api/v1/items/" + story, owner).body().get("attachmentsCount").asLong()).isEqualTo(1);
        assertThat(get("/api/v1/items/" + story + "/attachments", reader).expect(200).body()).hasSize(1);

        MockHttpServletResponse file = download(uploaded.id(), reader);
        assertThat(file.getStatus()).isEqualTo(200);
        assertThat(file.getContentAsByteArray()).isEqualTo(content);
        assertThat(file.getHeader("Content-Disposition")).startsWith("attachment");
        assertThat(file.getContentType()).isEqualTo("application/octet-stream");
        assertThat(download(uploaded.id(), outsider).getStatus()).isEqualTo(404);

        delete("/api/v1/attachments/" + uploaded.id(), reader).expect(403);
        delete("/api/v1/attachments/" + uploaded.id(), owner).expect(204);
        assertThat(download(uploaded.id(), owner).getStatus()).isEqualTo(404);
        JsonNode history = get("/api/v1/items/" + story + "/history", owner).expect(200).body();
        assertThat(history.get(0).get("diff").get("attachment").get(0).asString()).isEqualTo("relatório final.html");
    }

    private List<Long> column(Auth auth, long project, long status) throws Exception {
        return pageIds(get("/api/v1/projects/" + project + "/userstories?status=" + status, auth).expect(200));
    }

    private static List<Long> pageIds(Res page) {
        return ids(page.body().get("items"));
    }

    @Test
    void newTagsTakeTheirColourAndNeighboursFollowTheNumber() throws Exception {
        Auth owner = newUser(unique("own"));
        long project = newProject(owner).id();
        String stories = "/api/v1/projects/" + project + "/userstories";

        Res first = post(stories, Map.of("subject", "Primeira", "tags", List.of("api"), "tagsColors",
                Map.of("api", "#D35163")), owner).expect(201);
        post(stories, Map.of("subject", "Segunda", "tags", List.of("api"), "tagsColors", Map.of("api", "#000000")),
                owner).expect(201);
        Res third = newUs(owner, project, "Terceira");
        assertThat(get("/api/v1/projects/" + project, owner).body().get("tagsColors").get("api").asString())
                .isEqualTo("#d35163");
        post(stories, Map.of("subject", "Cor errada", "tags", List.of("x"), "tagsColors", Map.of("x", "vermelho")),
                owner).expect(400);

        JsonNode around = get("/api/v1/items/" + first.id() + "/neighbors", owner).expect(200).body();
        assertThat(around.get("previous").isNull()).isTrue();
        assertThat(around.get("next").asInt()).isEqualTo(2);
        assertThat(get("/api/v1/items/" + third.id() + "/neighbors", owner).body().get("previous").asInt()).isEqualTo(2);
    }
}
