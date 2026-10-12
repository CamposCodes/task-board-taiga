package com.r2da.taskboard.mail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import com.r2da.taskboard.auth.CurrentUser;
import com.r2da.taskboard.history.Action;
import com.r2da.taskboard.history.History;
import com.r2da.taskboard.notifications.NotificationService.Reader;

import jakarta.mail.internet.MimeMessage;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private record Words(String the, String in, String title, String ending) {
    }

    private record Pending(Long id, String recipient, String subject, String text, String html) {
    }

    private static final Map<String, Words> WORDS = Map.of(
            "us", new Words("a história de usuário", "na história de usuário", "História de usuário", "a"),
            "task", new Words("a tarefa", "na tarefa", "Tarefa", "a"),
            "issue", new Words("o problema", "no problema", "Problema", "o"),
            "wiki", new Words("a página da wiki", "na página da wiki", "Página da wiki", "a"));

    private static final Map<String, String> LABELS = Map.ofEntries(Map.entry("subject", "Assunto"),
            Map.entry("description", "Descrição"), Map.entry("status", "Status"), Map.entry("milestone", "Sprint"),
            Map.entry("parent", "História de usuário"), Map.entry("points", "Pontos"),
            Map.entry("assignedTo", "Atribuído a"), Map.entry("tags", "Tags"),
            Map.entry("dueDate", "Data de vencimento"), Map.entry("isBlocked", "Bloqueado"),
            Map.entry("blockedNote", "Motivo do bloqueio"), Map.entry("type", "Tipo"),
            Map.entry("severity", "Gravidade"), Map.entry("priority", "Prioridade"),
            Map.entry("teamRequirement", "Requisito de equipe"),
            Map.entry("clientRequirement", "Requisito de cliente"), Map.entry("isIocaine", "Iocaína"));

    private static final int MAX_ATTEMPTS = 12;

    private final JdbcClient jdbc;
    private final ObjectProvider<JavaMailSender> sender;
    private final String host;
    private final String from;
    private final String publicUrl;

    public MailService(JdbcClient jdbc, ObjectProvider<JavaMailSender> sender,
            @Value("${spring.mail.host:}") String host, @Value("${app.mail.from}") String from,
            @Value("${app.public-url}") String publicUrl) {
        this.jdbc = jdbc;
        this.sender = sender;
        this.host = host;
        this.from = from;
        this.publicUrl = publicUrl;
    }

    public boolean enabled() {
        return !host.isBlank();
    }

    public void movement(History entry, List<Reader> readers, CurrentUser me) {
        Words words = WORDS.get(entry.getTargetType());
        if (!enabled() || words == null || readers.isEmpty()) {
            return;
        }

        String[] project = jdbc.sql("select slug, name from projects where id = :id").param("id", entry.getProjectId())
                .query((rs, row) -> new String[] { rs.getString("slug"), rs.getString("name") }).single();
        String actor = jdbc.sql("select full_name from users where id = :id").param("id", me.id())
                .query(String.class).optional().orElse("Alguém");
        boolean wiki = entry.getTargetType().equals("wiki");
        String name = wiki ? entry.getTargetSubject() : "#" + entry.getTargetRef() + " " + entry.getTargetSubject();
        String link = entry.getKind() == Action.delete ? null
                : publicUrl + "/project/" + project[0] + "/" + entry.getTargetType() + "/"
                        + (wiki ? entry.getTargetSubject() : entry.getTargetRef());

        String participle = switch (entry.getKind()) {
            case create -> "criad";
            case delete -> "excluíd";
            default -> "alterad";
        } + words.ending();
        String headline = entry.getKind() == Action.comment ? "Novo comentário " + words.in() + " " + name
                : wiki ? words.title() + " " + name + " " + participle
                        : words.title() + " #" + entry.getTargetRef() + " " + participle + ": "
                                + entry.getTargetSubject();
        String subject = cut(("[" + project[1] + "] " + headline).replaceAll("\\s+", " "), 200);
        String lead = actor + " " + switch (entry.getKind()) {
            case create -> "criou " + words.the();
            case delete -> "excluiu " + words.the();
            case comment -> "comentou " + words.in();
            default -> "alterou " + words.the();
        };

        List<String> changes = entry.getKind() != Action.change || entry.getDiff() == null ? List.of()
                : entry.getDiff().entrySet().stream().map(change -> line(change.getKey(), change.getValue())).toList();
        String comment = entry.getCommentId() == null ? null
                : jdbc.sql("select body from comments where id = :id").param("id", entry.getCommentId())
                        .query(String.class).optional().orElse(null);

        for (Reader reader : readers) {
            String reason = reader.followsAll()
                    ? "Você recebe este e-mail porque seu cargo no projeto acompanha todas as movimentações."
                    : "Você recebe este e-mail porque está atribuído a este item, o observa ou o criou.";
            jdbc.sql("""
                    insert into mail_outbox (recipient, subject, body_text, body_html)
                    values (:recipient, :subject, :text, :html)
                    """)
                    .param("recipient", reader.email()).param("subject", subject)
                    .param("text", text(lead, name, project[1], changes, comment, link, reason))
                    .param("html", html(lead, name, project[1], changes, comment, link, reason)).update();
        }
    }

    private static String line(String field, Object change) {
        List<?> pair = change instanceof List<?> list && list.size() == 2 ? list : Arrays.asList(null, change);
        if (field.equals("description")) {
            return "Descrição alterada";
        }
        if (field.equals("attachment")) {
            return pair.get(0) == null ? "Anexo adicionado: " + pair.get(1) : "Anexo removido: " + pair.get(0);
        }
        return LABELS.getOrDefault(field, field) + ": de " + show(pair.get(0)) + " para " + show(pair.get(1));
    }

    private static String show(Object value) {
        if (value instanceof Boolean flag) {
            return flag ? "sim" : "não";
        }
        if (value instanceof Collection<?> many) {
            return many.isEmpty() ? "vazio" : many.stream().map(String::valueOf).collect(Collectors.joining(", "));
        }
        return value == null || value.toString().isBlank() ? "vazio" : value.toString();
    }

    private static String text(String lead, String name, String project, List<String> changes, String comment,
            String link, String reason) {
        List<String> parts = new ArrayList<>();
        parts.add(lead + " " + name + " no projeto " + project + ".");
        if (!changes.isEmpty()) {
            parts.add(changes.stream().map(change -> "- " + change).collect(Collectors.joining("\n")));
        }
        if (comment != null) {
            parts.add(comment);
        }
        if (link != null) {
            parts.add("Abrir: " + link);
        }
        parts.add("--\n" + reason);
        return String.join("\n\n", parts);
    }

    private static String html(String lead, String name, String project, List<String> changes, String comment,
            String link, String reason) {
        String item = link == null ? "<b>" + esc(name) + "</b>"
                : "<a href=\"" + esc(link) + "\" style=\"color:#008aa8;font-weight:bold\">" + esc(name) + "</a>";
        StringBuilder page = new StringBuilder(
                "<div style=\"font-family:Ubuntu,Arial,Helvetica,sans-serif;font-size:14px;line-height:1.5;color:#2e3440\">");
        page.append("<p>").append(esc(lead)).append(' ').append(item).append(" no projeto <b>").append(esc(project))
                .append("</b>.</p>");
        if (!changes.isEmpty()) {
            page.append("<ul>");
            changes.forEach(change -> page.append("<li>").append(esc(change)).append("</li>"));
            page.append("</ul>");
        }
        if (comment != null) {
            page.append("<blockquote style=\"margin:0 0 16px;padding:8px 16px;border-left:3px solid #83eede;background:#f9f9fb\">")
                    .append(esc(comment).replace("\n", "<br>")).append("</blockquote>");
        }
        page.append("<p style=\"font-size:12px;color:#70728f\">").append(esc(reason)).append("</p></div>");
        return page.toString();
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    @Scheduled(fixedDelayString = "${app.mail.poll}", initialDelayString = "${app.mail.poll}")
    public void deliver() {
        JavaMailSender mail = enabled() ? sender.getIfAvailable() : null;
        if (mail == null) {
            return;
        }
        List<Pending> waiting = jdbc.sql("""
                select id, recipient, subject, body_text, body_html from mail_outbox
                where sent_at is null and attempts < :max
                  and (last_try is null or last_try < now() - attempts * interval '5 minutes')
                order by id limit 100
                """).param("max", MAX_ATTEMPTS)
                .query((rs, row) -> new Pending(rs.getLong("id"), rs.getString("recipient"), rs.getString("subject"),
                        rs.getString("body_text"), rs.getString("body_html")))
                .list();
        for (Pending pending : waiting) {
            try {
                MimeMessage message = mail.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(from);
                helper.setTo(pending.recipient());
                helper.setSubject(pending.subject());
                helper.setText(pending.text(), pending.html());
                mail.send(message);
                jdbc.sql("update mail_outbox set sent_at = now(), last_error = null where id = :id")
                        .param("id", pending.id()).update();
            } catch (Exception failure) {
                jdbc.sql("""
                        update mail_outbox set attempts = attempts + 1, last_try = now(), last_error = :error
                        where id = :id
                        """).param("error", cut(String.valueOf(failure.getMessage()), 500)).param("id", pending.id())
                        .update();
                log.warn("E-mail {} para {} não saiu: {}", pending.id(), pending.recipient(), failure.getMessage());
                break;
            }
        }
        jdbc.sql("delete from mail_outbox where sent_at < now() - interval '30 days'").update();
    }
}
