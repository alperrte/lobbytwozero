package com.ltz.social.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.ltz.social.IntegrationTestSupport;
import com.ltz.social.api.controller.SocialApiPaths;
import com.ltz.social.domain.event.FriendRequestSentEvent;

/** SOC-13: iş verisi ile olay aynı transaction'da kalıcı olur; olay yükü yalnız kimlik/enum taşır. */
class OutboxEventsTest extends IntegrationTestSupport {

    @Autowired
    private OutboxEventRecorder recorder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void istekGonderilinceOlayYazilir_yukYalnizKimlikTasir() throws Exception {
        UUID sender = newUser();
        UUID receiver = newUser();

        UUID requestId = sendRequestAndGetId(sender, receiver);

        assertThat(eventTypes(requestId)).containsExactly("FriendRequestSent");
        var row = jdbc.queryForMap("select event_version, aggregate_type, status, attempts, payload::text as payload, "
                + "next_attempt_at is not null as due from outbox_events where aggregate_id = ?", requestId);
        assertThat(row.get("event_version")).isEqualTo(1);
        assertThat(row.get("aggregate_type")).isEqualTo("FriendRequest");
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("attempts")).isEqualTo(0);
        assertThat(row.get("due")).isEqualTo(true);
        String payload = (String) row.get("payload");
        assertThat(JsonPath.<String>read(payload, "$.requestId")).isEqualTo(requestId.toString());
        assertThat(JsonPath.<String>read(payload, "$.senderId")).isEqualTo(sender.toString());
        assertThat(JsonPath.<String>read(payload, "$.receiverId")).isEqualTo(receiver.toString());
        assertThat(JsonPath.<Map<String, Object>>read(payload, "$")).containsOnlyKeys("requestId",
                "senderId", "receiverId");
    }

    @Test
    void kabulEdilinceKabulOlayiYazilir_redVeIptalOlayYazmaz() throws Exception {
        UUID accepted = sendAndAccept();
        UUID rejectedSender = newUser();
        UUID rejectedReceiver = newUser();
        UUID rejected = sendRequestAndGetId(rejectedSender, rejectedReceiver);
        act(SocialApiPaths.FRIEND_REQUEST_REJECT, rejectedReceiver, rejected).andExpect(status().isOk());
        UUID cancelledSender = newUser();
        UUID cancelled = sendRequestAndGetId(cancelledSender, newUser());
        act(SocialApiPaths.FRIEND_REQUEST_CANCEL, cancelledSender, cancelled).andExpect(status().isOk());

        assertThat(eventTypes(accepted)).containsExactly("FriendRequestSent", "FriendRequestAccepted");
        // Red ve iptal bildirim üretmez (reddedilen kişiye "reddedildin" bildirimi gitmez).
        assertThat(eventTypes(rejected)).containsExactly("FriendRequestSent");
        assertThat(eventTypes(cancelled)).containsExactly("FriendRequestSent");
    }

    @Test
    void basarisizIslemler_olayBirakmaz() throws Exception {
        UUID sender = newUser();
        UUID receiver = newUser();
        UUID requestId = sendRequestAndGetId(sender, receiver);

        sendRequest(sender, receiver).andExpect(status().isConflict());
        act(SocialApiPaths.FRIEND_REQUEST_ACCEPT, sender, requestId).andExpect(status().isNotFound());

        assertThat(eventTypes(requestId)).containsExactly("FriendRequestSent");
    }

    @Test
    void olayIsVerisiylebirlikteGeriAlinir() {
        UUID aggregate = UUID.randomUUID();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            recorder.record(new FriendRequestSentEvent(aggregate, UUID.randomUUID(), UUID.randomUUID()));
            status.setRollbackOnly();
        });

        assertThat(eventTypes(aggregate)).isEmpty();
    }

    @Test
    void olayYalnizTransactionIcindeYazilabilir() {
        assertThatThrownBy(() -> recorder.record(new FriendRequestSentEvent(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID()))).isInstanceOf(IllegalTransactionStateException.class);
    }

    private UUID sendAndAccept() throws Exception {
        return befriend(newUser(), newUser());
    }
}
