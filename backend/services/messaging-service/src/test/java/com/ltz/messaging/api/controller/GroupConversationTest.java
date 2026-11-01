package com.ltz.messaging.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.ltz.messaging.IntegrationTestSupport;

/** MSG-02 grup sohbeti oluşturma; oluşturan ile ilk üyeler arasında MSG-15 engel kontrolü. */
class GroupConversationTest extends IntegrationTestSupport {

    @Test
    void grupOlusturanSahipDigerleriUyeBaslikKirpilir() throws Exception {
        UUID owner = newUser();
        UUID first = newUser();
        UUID second = newUser();

        createGroup(owner, "  Hafta sonu takımı  ", first, second)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("GROUP"))
                .andExpect(jsonPath("$.title").value("Hafta sonu takımı"))
                .andExpect(jsonPath("$.createdBy").value(owner.toString()))
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andExpect(jsonPath("$.members.length()").value(3))
                .andExpect(jsonPath("$.members[?(@.userId=='%s')].role".formatted(owner)).value("OWNER"))
                .andExpect(jsonPath("$.members[?(@.userId=='%s')].role".formatted(first)).value("MEMBER"));
    }

    @Test
    void yinelenenUyeTekSayilir() throws Exception {
        UUID owner = newUser();
        UUID member = newUser();

        createGroup(owner, "Takım", member, member).andExpect(status().isCreated())
                .andExpect(jsonPath("$.members.length()").value(2));
    }

    @Test
    void olusturanUyeListesindeOlamaz() throws Exception {
        UUID owner = newUser();

        createGroup(owner, "Takım", newUser(), owner).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_ADD_SELF"));
    }

    @Test
    void gecersizBaslikVeUyeListesi400() throws Exception {
        UUID owner = newUser();
        String member = "[\"%s\"]".formatted(newUser());
        String tooMany = jsonArray(Stream.generate(UUID::randomUUID).limit(100).toArray(UUID[]::new));
        int before = conversationCount();

        for (String body : List.of(
                "{\"title\":\"   \",\"memberIds\":%s}".formatted(member),
                "{\"title\":\"%s\",\"memberIds\":%s}".formatted("x".repeat(101), member),
                "{\"title\":\"Satır\\nsonu\",\"memberIds\":%s}".formatted(member),
                "{\"memberIds\":%s}".formatted(member),
                "{\"title\":\"Takım\",\"memberIds\":[]}",
                "{\"title\":\"Takım\"}",
                "{\"title\":\"Takım\",\"memberIds\":[null]}",
                "{\"title\":\"Takım\",\"memberIds\":%s}".formatted(tooMany),
                "{\"title\":\"Takım\",\"memberIds\":%s,\"ownerId\":\"%s\"}".formatted(member, newUser()))) {
            mockMvc.perform(post(MessagingApiPaths.GROUP_CONVERSATIONS)
                            .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(conversationCount()).isEqualTo(before);
    }

    @Test
    void yuzKarakterBaslikKabulEdilir() throws Exception {
        createGroup(newUser(), "x".repeat(100), newUser()).andExpect(status().isCreated());
    }

    @Test
    void olusturanIleEngelliBiriVarsaGrupKurulmaz() throws Exception {
        UUID owner = newUser();
        UUID friend = newUser();
        UUID blocker = newUser();
        blocks.block(blocker, owner);
        int before = conversationCount();

        createGroup(owner, "Takım", friend, blocker).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MESSAGING_NOT_ALLOWED"));

        assertThat(conversationCount()).isEqualTo(before);
    }

    @Test
    void uyelerArasindakiEngelGrupKurmayiEngellemez() throws Exception {
        UUID owner = newUser();
        UUID first = newUser();
        UUID second = newUser();
        // Karar: yalnız ekleyen ↔ eklenen arasındaki engel denetlenir.
        blocks.block(first, second);

        createGroup(owner, "Takım", first, second).andExpect(status().isCreated());
    }

    @Test
    void engelDurumuOgrenilemezseGrupKurulmaz503() throws Exception {
        blocks.setUnavailable(true);
        int before = conversationCount();

        createGroup(newUser(), "Takım", newUser()).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("BLOCK_CHECK_UNAVAILABLE"));

        assertThat(conversationCount()).isEqualTo(before);
    }

    @Test
    void doksanDokuzUyeylaYuzKisilikGrupKurulur() throws Exception {
        UUID[] members = Stream.generate(UUID::randomUUID).limit(99).toArray(UUID[]::new);

        UUID group = idOf(createGroup(newUser(), "Kalabalık", members).andExpect(status().isCreated()));

        assertThat(activeMemberCount(group)).isEqualTo(100);
    }
}
