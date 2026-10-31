package com.ltz.mail.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ltz.mail.application.port.MailDeliveryException;
import com.ltz.mail.application.port.OutgoingMail;
import com.ltz.mail.infrastructure.config.SmtpProperties;
import com.ltz.mail.infrastructure.config.SmtpSecurity;

/**
 * {@link SmtpMailSender}'ı gerçek SMTP konuşmasıyla sınar: yerel, minimal bir SMTP sunucusu (loopback) alıcıya ve
 * veri aşamasına istenen yanıt kodunu verir. Sahte gönderici yoktur; MIME üretimi ve hata sınıflandırması gerçek
 * Jakarta Mail yığınından geçer.
 */
class SmtpMailSenderTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private FakeSmtpServer server;

    @BeforeEach
    void start() throws IOException {
        server = new FakeSmtpServer();
    }

    @AfterEach
    void stop() throws IOException {
        server.close();
    }

    @Test
    void epostaIkiBicimliVeLogoyuGomuluGider() {
        sender(server.port()).send(mail("kullanici@example.test"));

        assertThat(server.sessions()).singleElement().satisfies(session -> {
            assertThat(session.recipient()).isEqualTo("<kullanici@example.test>");
            assertThat(session.data())
                    .contains("multipart/alternative", "text/plain", "text/html", "Auto-Submitted: auto-generated",
                            "Content-ID: <" + SmtpMailSender.LOGO_CONTENT_ID + ">")
                    .contains("no-reply@ltz.local");
        });
    }

    @Test
    void gecersizAliciKaliciHatadir() {
        assertThatThrownBy(() -> sender(server.port()).send(mail("bu bir adres degil")))
                .isInstanceOfSatisfying(MailDeliveryException.class, e -> {
                    assertThat(e.isPermanent()).isTrue();
                    assertThat(e.errorCode()).isEqualTo("INVALID_RECIPIENT");
                });
        assertThat(server.sessions()).isEmpty();
    }

    @Test
    void sunucu5xxVerirseKaliciHatadirVeKodTasir() {
        server.rejectRecipientWith("550 5.1.1 no such user");

        assertThatThrownBy(() -> sender(server.port()).send(mail("yok@example.test")))
                .isInstanceOfSatisfying(MailDeliveryException.class, e -> {
                    assertThat(e.isPermanent()).isTrue();
                    assertThat(e.smtpCode()).isEqualTo(550);
                    assertThat(e.errorCode()).isEqualTo("SMTP_550");
                    assertThat(e.getMessage()).doesNotContain("yok@example.test");
                });
    }

    @Test
    void sunucu4xxVerirseGeciciHatadir() {
        server.rejectRecipientWith("450 4.2.0 mailbox busy");

        assertThatThrownBy(() -> sender(server.port()).send(mail("mesgul@example.test")))
                .isInstanceOfSatisfying(MailDeliveryException.class, e -> {
                    assertThat(e.isPermanent()).isFalse();
                    assertThat(e.smtpCode()).isEqualTo(450);
                });
    }

    @Test
    void ulasilamayanSunucuGeciciHatadir() throws IOException {
        int closedPort = server.port();
        server.close();

        assertThatThrownBy(() -> sender(closedPort).send(mail("kullanici@example.test")))
                .isInstanceOfSatisfying(MailDeliveryException.class, e -> {
                    assertThat(e.isPermanent()).isFalse();
                    assertThat(e.errorCode()).isEqualTo("SMTP_SEND_FAILED");
                });
    }

    @Test
    void yanlisKimlikBilgisiGeciciYapilandirmaHatasidir() {
        SmtpProperties properties = new SmtpProperties("127.0.0.1", server.port(), "kullanici", "parola",
                SmtpSecurity.NONE, null, TIMEOUT, TIMEOUT, TIMEOUT);

        assertThatThrownBy(() -> new SmtpMailSender(properties).send(mail("kullanici@example.test")))
                .isInstanceOfSatisfying(MailDeliveryException.class, e -> {
                    assertThat(e.isPermanent()).isFalse();
                    assertThat(e.errorCode()).isEqualTo("SMTP_AUTH_FAILED");
                });
    }

    private static SmtpMailSender sender(int port) {
        return new SmtpMailSender(new SmtpProperties("127.0.0.1", port, null, null, SmtpSecurity.NONE, null,
                TIMEOUT, TIMEOUT, TIMEOUT));
    }

    private static OutgoingMail mail(String to) {
        return new OutgoingMail(UUID.randomUUID(), to, "Doğrulama kodunuz", "Kod: 123456",
                "<p>Kod: <b>123456</b></p><img src=\"cid:" + SmtpMailSender.LOGO_CONTENT_ID + "\">");
    }

    /** Tek bağlantılı döngüyle HELO/MAIL/RCPT/DATA/QUIT konuşan minimal SMTP sunucusu. */
    private static final class FakeSmtpServer implements AutoCloseable {

        record Session(String recipient, String data) {
        }

        private final ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        private final List<Session> sessions = new CopyOnWriteArrayList<>();
        private final Thread acceptor;
        private volatile String recipientReply = "250 OK";
        private volatile IOException lastError;

        FakeSmtpServer() throws IOException {
            acceptor = Thread.ofVirtual().start(this::acceptLoop);
        }

        int port() {
            return socket.getLocalPort();
        }

        List<Session> sessions() {
            return List.copyOf(sessions);
        }

        void rejectRecipientWith(String reply) {
            recipientReply = reply;
        }

        private void acceptLoop() {
            while (!socket.isClosed()) {
                try (Socket client = socket.accept()) {
                    serve(client);
                } catch (IOException exception) {
                    lastError = exception; // sunucu kapandı ya da istemci bağlantıyı attı
                }
            }
        }

        private void serve(Socket client) throws IOException {
            try (BufferedReader in = new BufferedReader(
                         new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter out = new BufferedWriter(
                         new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8))) {
                converse(in, out);
            }
        }

        private void converse(BufferedReader in, BufferedWriter out) throws IOException {
            reply(out, "220 fake ESMTP");
            String recipient = null;
            String line;
            while ((line = in.readLine()) != null) {
                String command = line.toUpperCase(java.util.Locale.ROOT);
                if (command.startsWith("EHLO") || command.startsWith("HELO")) {
                    out.write("250-fake\r\n250 AUTH PLAIN LOGIN\r\n");
                    out.flush();
                } else if (command.startsWith("RCPT TO:")) {
                    recipient = line.substring("RCPT TO:".length()).strip();
                    reply(out, recipientReply);
                } else if (command.startsWith("DATA")) {
                    reply(out, "354 go ahead");
                    StringBuilder data = new StringBuilder();
                    while ((line = in.readLine()) != null && !".".equals(line)) {
                        data.append(line).append('\n');
                    }
                    sessions.add(new Session(recipient, data.toString()));
                    reply(out, "250 queued");
                } else if (command.startsWith("QUIT")) {
                    reply(out, "221 bye");
                    return;
                } else {
                    reply(out, command.startsWith("AUTH") ? "535 5.7.8 bad credentials" : "250 OK");
                }
            }
        }

        private static void reply(BufferedWriter out, String text) throws IOException {
            out.write(text + "\r\n");
            out.flush();
        }

        @Override
        public void close() throws IOException {
            socket.close();
            acceptor.interrupt();
        }
    }
}
