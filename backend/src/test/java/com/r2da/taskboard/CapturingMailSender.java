package com.r2da.taskboard;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import jakarta.mail.internet.MimeMessage;

class CapturingMailSender extends JavaMailSenderImpl {

    final List<MimeMessage> sent = new CopyOnWriteArrayList<>();
    volatile boolean down;

    @Override
    protected void doSend(MimeMessage[] messages, Object[] originals) {
        if (down) {
            throw new MailSendException("servidor de e-mail fora do ar");
        }
        sent.addAll(List.of(messages));
    }
}
