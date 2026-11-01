package com.ltz.messaging.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.ltz.messaging.IntegrationTestSupport;
import com.ltz.messaging.application.port.BlockCheckPort;
import com.ltz.messaging.infrastructure.client.SocialBlockCheckClient;

/**
 * Üretim yapılandırması: engel adaptörü gerçek Social istemcisidir. Auth gizli anahtarı tanımlı değilken token
 * alınamaz, Social'a gidilmez ve engel kontrolü gereken her işlem fail-closed 503 olur; engel yokmuş gibi devam edilmez.
 * Engel gerektirmeyen işlemler (görüntüleme, ayrılma) etkilenmez.
 */
@TestPropertySource(properties = {
    "ltz.messaging.blocks.adapter=social",
    "ltz.messaging.clients.client-secret="
})
class SocialAdapterFailClosedTest extends IntegrationTestSupport {

    @Autowired
    private BlockCheckPort blockCheck;

    @Test
    void varsayilanAdaptorSocialIstemcisi() {
        assertThat(blockCheck).isInstanceOf(SocialBlockCheckClient.class);
        assertThat(blocks).isNull();
    }

    @Test
    void gizliAnahtarYokkenSohbetVeGrupAcilmaz503() throws Exception {
        UUID alice = newUser();
        int before = conversationCount();

        openDirect(alice, newUser()).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("BLOCK_CHECK_UNAVAILABLE"));
        createGroup(alice, "Takım", newUser()).andExpect(status().isServiceUnavailable());

        assertThat(conversationCount()).isEqualTo(before);
    }
}
