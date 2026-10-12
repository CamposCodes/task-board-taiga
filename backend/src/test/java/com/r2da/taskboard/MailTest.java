package com.r2da.taskboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.r2da.taskboard.mail.MailService;

import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import tools.jackson.databind.JsonNode;

class MailTest extends ApiTest {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private MailService mail;

    @Autowired
    private CapturingMailSender sender;

    private long mark() {
        return jdbc.sql("select coalesce(max(id), 0) from mail_outbox").query(Long.class).single();
    }

    private List<String> told(long mark, String project) {
        return column("recipient", mark, project).stream().map(address -> address.replace("@example.com", "")).toList();
    }

    private List<String> column(String column, long mark, String project) {
        return jdbc.sql("select " + column + " from mail_outbox where id > :mark and subject like :prefix order by id")
                .param("mark", mark).param("prefix", "[" + project + "]%").query(String.class).list();
    }

    @Test
    void leadersGetEveryMovementAndTheOthersOnlyWhatInvolvesThem() throws Exception {
        String ownerName = unique("po");
        String leaderName = unique("lid");
        String devName = unique("dev");
        String otherName = unique("out");
        String viewerName = unique("con");
        Auth owner = newUser(ownerName);
        Auth leader = newUser(leaderName);
        Auth dev = newUser(devName);
        Auth other = newUser(otherName);
        Auth viewer = newUser(viewerName);
        Res project = newProject(owner);
        long id = project.id();
        String name = project.body().get("name").asString();
        addMember(owner, id, leaderName, "Líder de Projeto");
        addMember(owner, id, devName, "R2DA");
        addMember(owner, id, otherName, "R2DA");
        addMember(owner, id, viewerName, "Consulta");

        long mark = mark();
        Res issue = post("/api/v1/projects/" + id + "/issues", Map.of("subject", "Erro ao salvar"), dev).expect(201);
        assertThat(told(mark, name)).containsExactlyInAnyOrder(ownerName, leaderName);
        assertThat(column("subject", mark, name)).containsOnly("[" + name + "] Problema #1 criado: Erro ao salvar");

        mark = mark();
        patch("/api/v1/items/" + issue.id(), Map.of("assignedTo", userId(other), "version",
                issue.body().get("version").asInt()), owner).expect(200);
        assertThat(told(mark, name)).containsExactlyInAnyOrder(leaderName, otherName, devName);
        assertThat(column("body_text", mark, name).getFirst())
                .contains("alterou o problema #1 Erro ao salvar no projeto " + name)
                .contains("- Atribuído a: de vazio para Pessoa " + otherName)
                .contains("/issue/1");

        post("/api/v1/items/" + issue.id() + "/watch", null, viewer).expect(200);
        mark = mark();
        post("/api/v1/items/" + issue.id() + "/comments", Map.of("body", "Reproduzi <aqui>."), leader).expect(201);
        assertThat(told(mark, name)).containsExactlyInAnyOrder(ownerName, otherName, devName, viewerName);
        assertThat(column("body_html", mark, name).getFirst()).contains("Reproduzi &lt;aqui&gt;.");

        JsonNode roles = get("/api/v1/projects/" + id, owner).expect(200).body();
        patch("/api/v1/roles/" + roleId(roles, "Consulta"), Map.of("followsAll", true), owner).expect(200);
        mark = mark();
        newUs(owner, id, "História nova");
        assertThat(told(mark, name)).containsExactlyInAnyOrder(leaderName, viewerName);

        delete("/api/v1/projects/" + id + "/members/" + userId(dev), owner).expect(204);
        mark = mark();
        delete("/api/v1/items/" + issue.id(), owner).expect(204);
        assertThat(told(mark, name)).containsExactlyInAnyOrder(leaderName, otherName, viewerName);
        assertThat(column("subject", mark, name)).containsOnly("[" + name + "] Problema #1 excluído: Erro ao salvar");
    }

    private Map<String, String> rung(Map<String, Auth> people) throws Exception {
        Map<String, String> kinds = new TreeMap<>();
        for (Map.Entry<String, Auth> person : people.entrySet()) {
            get("/api/v1/notifications?unread=true", person.getValue()).expect(200).body().get("items")
                    .forEach(entry -> kinds.merge(person.getKey(), entry.get("kind").asString(), (a, b) -> a + "," + b));
            post("/api/v1/notifications/read", null, person.getValue()).expect(204);
        }
        return kinds;
    }

    @Test
    void theBellHoldsEveryMovementTheMailTellsForTheSamePeople() throws Exception {
        String ownerName = unique("po");
        String leaderName = unique("lid");
        String devName = unique("dev");
        String otherName = unique("out");
        Auth owner = newUser(ownerName);
        Auth leader = newUser(leaderName);
        Auth dev = newUser(devName);
        Auth other = newUser(otherName);
        Map<String, Auth> people = Map.of(ownerName, owner, leaderName, leader, devName, dev, otherName, other);
        Res project = newProject(owner);
        long id = project.id();
        String name = project.body().get("name").asString();
        addMember(owner, id, leaderName, "Líder de Projeto");
        addMember(owner, id, devName, "R2DA");
        addMember(owner, id, otherName, "R2DA");

        long mark = mark();
        Res issue = post("/api/v1/projects/" + id + "/issues", Map.of("subject", "Erro ao salvar"), dev).expect(201);
        JsonNode entry = get("/api/v1/notifications", leader).expect(200).body().get("items").get(0);
        assertThat(entry.get("actor").get("username").asString()).isEqualTo(devName);
        assertThat(entry.get("itemType").asString()).isEqualTo("issue");
        assertThat(entry.get("itemRef").asInt()).isEqualTo(1);
        assertThat(entry.get("itemSubject").asString()).isEqualTo("Erro ao salvar");
        assertThat(entry.get("project").get("name").asString()).isEqualTo(name);
        Map<String, String> rung = rung(people);
        assertThat(rung).isEqualTo(Map.of(ownerName, "create", leaderName, "create"));
        assertThat(told(mark, name)).containsExactlyInAnyOrderElementsOf(rung.keySet());

        mark = mark();
        patch("/api/v1/items/" + issue.id(), Map.of("assignedTo", userId(other), "version",
                issue.body().get("version").asInt()), owner).expect(200);
        rung = rung(people);
        assertThat(rung).isEqualTo(Map.of(leaderName, "change", otherName, "assigned", devName, "change"));
        assertThat(told(mark, name)).containsExactlyInAnyOrderElementsOf(rung.keySet());

        mark = mark();
        post("/api/v1/items/" + issue.id() + "/comments", Map.of("body", "Reproduzi."), leader).expect(201);
        rung = rung(people);
        assertThat(rung).isEqualTo(Map.of(ownerName, "comment", otherName, "comment", devName, "comment"));
        assertThat(told(mark, name)).containsExactlyInAnyOrderElementsOf(rung.keySet());

        mark = mark();
        call("PUT", "/api/v1/projects/" + id + "/wiki/home", Map.of("content", "# Início"), owner);
        assertThat(get("/api/v1/notifications?unread=true", leader).expect(200).body().get("items").get(0)
                .get("itemType").asString()).isEqualTo("wiki");
        rung = rung(people);
        assertThat(rung).isEqualTo(Map.of(leaderName, "create"));
        assertThat(told(mark, name)).containsExactlyInAnyOrderElementsOf(rung.keySet());

        mark = mark();
        delete("/api/v1/items/" + issue.id(), owner).expect(204);
        rung = rung(people);
        assertThat(rung).isEqualTo(Map.of(leaderName, "delete", otherName, "delete", devName, "delete"));
        assertThat(told(mark, name)).containsExactlyInAnyOrderElementsOf(rung.keySet());
    }

    @Test
    void theQueueSurvivesAMailServerThatIsDown() throws Exception {
        get("/actuator/health", null).expect(200);

        String leaderName = unique("lid");
        Auth owner = newUser(unique("po"));
        newUser(leaderName);
        Res project = newProject(owner);
        String name = project.body().get("name").asString();
        addMember(owner, project.id(), leaderName, "Líder de Projeto");
        mail.deliver();
        sender.sent.clear();

        long mark = mark();
        newUs(owner, project.id(), "Cobrança recorrente");
        long id = mark();
        assertThat(told(mark, name)).containsExactly(leaderName);

        sender.down = true;
        try {
            mail.deliver();
        } finally {
            sender.down = false;
        }
        assertThat(jdbc.sql("select attempts from mail_outbox where id = :id and sent_at is null and last_error is not null")
                .param("id", id).query(Integer.class).single()).isEqualTo(1);

        mail.deliver();
        assertThat(sender.sent).isEmpty();
        jdbc.sql("update mail_outbox set last_try = now() - interval '10 minutes' where id = :id").param("id", id).update();
        mail.deliver();
        assertThat(sender.sent).hasSize(1);
        MimeMessage message = sender.sent.getFirst();
        assertThat(message.getRecipients(Message.RecipientType.TO)[0]).hasToString(leaderName + "@example.com");
        assertThat(message.getSubject()).isEqualTo("[" + name + "] História de usuário #1 criada: Cobrança recorrente");
        assertThat(message.getFrom()[0]).hasToString("taskboard@localhost");
        assertThat(jdbc.sql("select sent_at is not null from mail_outbox where id = :id").param("id", id)
                .query(Boolean.class).single()).isTrue();
    }
}
