package com.ltz.messaging.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.ltz.messaging.IntegrationTestSupport;
import com.ltz.messaging.application.service.ConversationAccessPolicy;
import com.ltz.messaging.domain.exception.MessagingErrorCode;
import com.ltz.messaging.domain.exception.MessagingException;
import com.nimbusds.jose.JOSEObjectType;

/**
 * MSG-14: yalnız birebir sohbetin tarafları ve grubun aktif üyeleri sohbete erişir; yazma ayrıca birebir sohbette
 * engel olmamasını ister (MSG-15). Var olmayan sohbet ile erişilemeyen sohbet ayırt edilemez.
 */
class ConversationAccessTest extends IntegrationTestSupport {

    @Autowired
    private ConversationAccessPolicy access;

    // ------------------------------------------------------------ okuma (HTTP)

    @Test
    void uyeSohbetiGorurDisaridakiVeOlmayanSohbetAyniYanit404() throws Exception {
        UUID owner = newUser();
        UUID member = newUser();
        UUID group = createGroupAndGetId(owner, member);
        UUID direct = openDirectAndGetId(owner, member);

        getConversation(member, group).andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("MEMBER"));
        getConversation(member, direct).andExpect(status().isOk());

        // Erişilemeyen ve olmayan sohbet aynı kod ve açıklamayı alır (instance yalnız istenen yoldur).
        for (UUID target : List.of(group, direct, UUID.randomUUID())) {
            getConversation(newUser(), target).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"))
                    .andExpect(jsonPath("$.detail").value("Conversation was not found."));
        }
    }

    @Test
    void sahteKimlikBasligiYokSayilir() throws Exception {
        UUID owner = newUser();
        UUID group = createGroupAndGetId(owner, newUser());
        UUID intruder = newUser();

        mockMvc.perform(get(MessagingApiPaths.CONVERSATION, group)
                        .header(HttpHeaders.AUTHORIZATION, bearer(intruder))
                        .header("X-User-Id", owner.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void gecersizKimlikParametresi400DegerYansitilmaz() throws Exception {
        mockMvc.perform(get(MessagingApiPaths.CONVERSATION, "<script>")
                        .header(HttpHeaders.AUTHORIZATION, bearer(newUser())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail").value(not(containsString("script"))));
    }

    // ------------------------------------------------------------ kimlik doğrulama

    @Test
    void tokensizIstek401() throws Exception {
        mockMvc.perform(get(MessagingApiPaths.CONVERSATION, UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(post(MessagingApiPaths.DIRECT_CONVERSATIONS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"%s\"}".formatted(newUser())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void kurallaraUymayanTokenlar401() throws Exception {
        UUID user = newUser();
        String[] invalid = {
            accessToken(user, claims -> claims.audience("other-api")),
            accessToken(user, claims -> claims.issuer("someone-else")),
            accessToken(user, claims -> claims.expirationTime(new Date(System.currentTimeMillis() - 120_000))),
            accessToken(user, claims -> claims.notBeforeTime(null)),
            accessToken(user, claims -> claims.subject("not-a-uuid")),
            sign(SIGNING_KEY, new JOSEObjectType("svc+jwt"), validClaims(user).build()),
            sign(generateKey("unknown-key"), ACCESS_TOKEN_TYPE, validClaims(user).build()),
            "not-a-jwt"
        };
        for (String token : invalid) {
            mockMvc.perform(get(MessagingApiPaths.CONVERSATION, UUID.randomUUID())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void tanimsizYollarKapali() throws Exception {
        mockMvc.perform(get("/internal/v1/anything").header(HttpHeaders.AUTHORIZATION, bearer(newUser())))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/actuator/env")).andExpect(status().is4xxClientError());
        mockMvc.perform(get(MessagingApiPaths.BASE + "/unknown").header(HttpHeaders.AUTHORIZATION, bearer(newUser())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ------------------------------------------------------------ yazma kuralı (politika)

    @Test
    void birebirSohbeteYazmaEngelYoksaSerbestEngelVarsa403() throws Exception {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID direct = openDirectAndGetId(alice, bob);

        assertThatCode(() -> access.requireCanWrite(direct, alice)).doesNotThrowAnyException();

        blocks.block(bob, alice);
        assertErrorCode(() -> access.requireCanWrite(direct, alice), MessagingErrorCode.MESSAGING_NOT_ALLOWED);
        assertErrorCode(() -> access.requireCanWrite(direct, bob), MessagingErrorCode.MESSAGING_NOT_ALLOWED);

        blocks.setUnavailable(true);
        assertErrorCode(() -> access.requireCanWrite(direct, alice), MessagingErrorCode.BLOCK_CHECK_UNAVAILABLE);
    }

    @Test
    void grupUyesiYazabilirUyelerArasiEngelUyeligiEtkilemez() throws Exception {
        UUID owner = newUser();
        UUID member = newUser();
        UUID group = createGroupAndGetId(owner, member);
        blocks.block(owner, member);
        int callsBefore = blocks.calls();

        assertThatCode(() -> access.requireCanWrite(group, member)).doesNotThrowAnyException();
        // Grupta engel sorgusu yapılmaz.
        assertThat(blocks.calls()).isEqualTo(callsBefore);
    }

    @Test
    void uyeOlmayanVeAyrilanYazamaz404() throws Exception {
        UUID owner = newUser();
        UUID member = newUser();
        UUID group = createGroupAndGetId(owner, member);
        leave(member, group).andExpect(status().isNoContent());

        assertErrorCode(() -> access.requireCanWrite(group, member), MessagingErrorCode.CONVERSATION_NOT_FOUND);
        assertErrorCode(() -> access.requireCanWrite(group, newUser()), MessagingErrorCode.CONVERSATION_NOT_FOUND);
        assertErrorCode(() -> access.requireCanWrite(UUID.randomUUID(), owner),
                MessagingErrorCode.CONVERSATION_NOT_FOUND);
    }

    private static void assertErrorCode(ThrowingCallable call,
                                        MessagingErrorCode expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(MessagingException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(expected));
    }
}
