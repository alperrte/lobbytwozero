package com.ltz.mail;

import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

import com.ltz.mail.application.port.MailDeliveryException;
import com.ltz.mail.application.port.MailSenderPort;
import com.ltz.mail.application.port.OutgoingMail;

/** Testlerde SMTP yerine geçen gönderici: gönderilenleri kaydeder, sıradaki çağrılar için hata programlanabilir. */
public class RecordingMailSender implements MailSenderPort {

    private final List<OutgoingMail> sent = new CopyOnWriteArrayList<>();
    private final Deque<MailDeliveryException> failures = new ConcurrentLinkedDeque<>();

    @Override
    public void send(OutgoingMail mail) {
        MailDeliveryException failure = failures.pollFirst();
        if (failure != null) {
            throw failure;
        }
        sent.add(mail);
    }

    public void failNext(MailDeliveryException failure) {
        failures.addLast(failure);
    }

    public List<OutgoingMail> sent() {
        return List.copyOf(sent);
    }

    public void reset() {
        sent.clear();
        failures.clear();
    }
}
