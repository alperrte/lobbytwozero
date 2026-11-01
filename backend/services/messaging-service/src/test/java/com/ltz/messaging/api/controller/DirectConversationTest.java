package com.ltz.messaging.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.ltz.messaging.IntegrationTestSupport;
import com.ltz.messaging.application.service.ConversationService;

/** MSG-01 birebir sohbet ve MSG-15 engel kontrolü. */
class DirectConversationTest extends IntegrationTestSupport {

    @Autowired
    private ConversationService conversations;

    @Test
    void yeniBirebirSohbet201IkiTarafUyeBaslikYok() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();

        openDirect(alice, bob)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DIRECT"))
                .andExpect(jsonPath("$.title").doesNotExist())
                .andExpect(jsonPath("$.createdBy").value(alice.toString()))
                .andExpect(jsonPath("$.myRole").value("MEMBER"))
                .andExpect(jsonPath("$.members.length()").value(2));
    }

    @Test
    void ayniIkiKisiIcinTekSohbetHerIkiYondenAyniKimlik200() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID first = openDirectAndGetId(alice, bob);

        openDirect(alice, bob).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(first.toString()));
        openDirect(bob, alice).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first.toString()))
                .andExpect(jsonPath("$.createdBy").value(alice.toString()));
    }

    @Test
    void kendisiyleSohbet400() throws Exception {
        UUID alice = newUser();

        openDirect(alice, alice).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_MESSAGE_SELF"));
    }

    @Test
    void engelHerIkiYondeSohbetAcmayiEngellerKayitOlusmaz() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID carol = newUser();
        blocks.block(alice, bob);
        blocks.block(carol, alice);
        int before = conversationCount();

        openDirect(alice, bob).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MESSAGING_NOT_ALLOWED"));
        openDirect(bob, alice).andExpect(status().isForbidden());
        openDirect(alice, carol).andExpect(status().isForbidden());

        assertThat(conversationCount()).isEqualTo(before);
    }

    @Test
    void sonradanKonanEngelMevcutSohbetiDeAcmaz() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        openDirectAndGetId(alice, bob);
        blocks.block(bob, alice);

        openDirect(alice, bob).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MESSAGING_NOT_ALLOWED"));

        blocks.unblock(bob, alice);
        openDirect(alice, bob).andExpect(status().isOk());
    }

    @Test
    void engelDurumuOgrenilemezseFailClosed503() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        blocks.setUnavailable(true);
        int before = conversationCount();

        openDirect(alice, bob).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("BLOCK_CHECK_UNAVAILABLE"));

        assertThat(conversationCount()).isEqualTo(before);
    }

    @Test
    void gecersizGovde400() throws Exception {
        UUID alice = newUser();

        for (String body : List.of("{}", "{\"userId\":\"not-a-uuid\"}",
                "{\"userId\":\"%s\",\"senderId\":\"%s\"}".formatted(newUser(), newUser()), "not json")) {
            mockMvc.perform(post(MessagingApiPaths.DIRECT_CONVERSATIONS)
                            .header(HttpHeaders.AUTHORIZATION, bearer(alice))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(blocks.calls()).isZero();
    }

    @Test
    void eszamanliAcilislarTekSohbetteBulusur() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        int parallel = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<UUID>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(parallel)) {
            for (int i = 0; i < parallel; i++) {
                boolean fromAlice = i % 2 == 0;
                Callable<UUID> open = () -> {
                    start.await();
                    return (fromAlice ? conversations.openDirect(alice, bob) : conversations.openDirect(bob, alice))
                            .conversation().id();
                };
                results.add(pool.submit(open));
            }
            start.countDown();
            Set<UUID> ids = new HashSet<>();
            for (Future<UUID> result : results) {
                ids.add(result.get());
            }
            assertThat(ids).hasSize(1);
        }
        assertThat(jdbc.queryForObject("select count(*) from conversations where direct_key like ? and direct_key like ?",
                Integer.class, "%" + alice + "%", "%" + bob + "%")).isEqualTo(1);
    }
}
