package com.ltz.game.api.controller;
import com.ltz.game.AuthenticatedApiTest;
import com.ltz.game.application.service.CatalogImportService;
import com.ltz.game.infrastructure.persistence.repository.GameExternalReferenceRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class LocalizationMetadataTest extends AuthenticatedApiTest {
    static final String ADMIN_GAMES="/api/v1/game/admin/games";
    static final String LOCALIZATION="""
        {"metadataLanguage":"tr-TR","languageSupport":[{"languageCode":"Türkçe","interfaceSupport":"SUPPORTED","subtitleSupport":"UNSUPPORTED"}],
        "evidence":"Yönetici tarafından kaynak incelendi"}
        """;
    @Autowired CatalogImportService imports;
    @Autowired GameExternalReferenceRepository refs;
    @Autowired jakarta.persistence.EntityManager em;
    String create(String extra) throws Exception {
        return read(send(post(ADMIN_GAMES),admin(),"{\"title\":\"Locale game\",\"gameType\":\"MAIN_GAME\""+extra+"}")
                .andExpect(status().isCreated()),"$.gameId");
    }
    long version(String id) throws Exception { return Long.parseLong(read(send(get(ADMIN_GAMES+"/"+id),admin(),null),"$.version")); }
    String metadata(String id) { return "/api/v1/game/"+id+"/metadata"; }

    @Test void oldRequestsAndLegacyRowsRetainGeneralLanguagesWithoutInventedSupportOrEvidence() throws Exception {
        UUID id=game("Legacy","EXTERNAL","MAIN_GAME","ACTIVE");
        jdbc.update("insert into game_languages(game_id,language_code) values(?,'tr')",id);
        mvc.perform(get(metadata(id.toString()))).andExpect(status().isOk()).andExpect(jsonPath("$.languages[0]").value("tr"))
                .andExpect(jsonPath("$.languageDataState").value("UNKNOWN")).andExpect(jsonPath("$.languageSupport").isEmpty())
                .andExpect(jsonPath("$.metadataLanguage").isEmpty()).andExpect(jsonPath("$.provenance").isEmpty());
        String created=create(",\"languages\":[\"tr\"]");
        mvc.perform(get(metadata(created))).andExpect(jsonPath("$.languageDataState").value("UNKNOWN"))
                .andExpect(jsonPath("$.languageSupport").isEmpty()).andExpect(jsonPath("$.earlyAccess").isEmpty());
    }

    @Test void supportTypesAreIndependentAndManualEditingIsNotProviderVerification() throws Exception {
        String id=create(",\"languages\":[\"en\"],\"localization\":"+LOCALIZATION);
        mvc.perform(get(metadata(id))).andExpect(status().isOk()).andExpect(jsonPath("$.languages[0]").value("en"))
                .andExpect(jsonPath("$.metadataLanguage").value("tr-tr")).andExpect(jsonPath("$.languageDataState").value("AVAILABLE"))
                .andExpect(jsonPath("$.languageSupport[0].languageCode").value("tr"))
                .andExpect(jsonPath("$.languageSupport[0].interfaceSupport").value("SUPPORTED"))
                .andExpect(jsonPath("$.languageSupport[0].audioSupport").value("UNKNOWN"))
                .andExpect(jsonPath("$.languageSupport[0].subtitleSupport").value("UNSUPPORTED"));
        assertEquals("ADMIN",jdbc.queryForObject("select source from game_metadata_provenance where game_id=? and field='LOCALIZATION'",String.class,UUID.fromString(id)));
        assertNull(jdbc.queryForObject("select verified_at from game_metadata_provenance where game_id=? and field='LOCALIZATION'",Object.class,UUID.fromString(id)));
        mvc.perform(get("/api/v1/game/games/"+id)).andExpect(jsonPath("$.metadata.languageSupport[0].audioSupport").value("UNKNOWN"));
    }

    @Test void explicitVerificationIsAuditedAndOrdinaryReEditClearsOldVerification() throws Exception {
        String id=create(",\"localization\":"+LOCALIZATION);
        send(patch(ADMIN_GAMES+"/"+id),admin(),"{\"version\":"+version(id)+",\"localization\":{\"verified\":true,\"evidence\":\"Manuel doğrulama gerçekten yapıldı\"}}")
                .andExpect(status().isOk());
        assertNotNull(jdbc.queryForObject("select verified_at from game_metadata_provenance where game_id=? and field='LOCALIZATION'",Object.class,UUID.fromString(id)));
        assertEquals(1,jdbc.queryForObject("select count(*) from game_admin_audits where game_id=? and reason='Manuel doğrulama gerçekten yapıldı'",Integer.class,UUID.fromString(id)));
        send(patch(ADMIN_GAMES+"/"+id),admin(),"{\"version\":"+version(id)+",\"localization\":{\"metadataLanguage\":\"en\",\"evidence\":\"Açıklama dili yeni kaynaktan düzenlendi\"}}")
                .andExpect(status().isOk());
        assertNull(jdbc.queryForObject("select verified_at from game_metadata_provenance where game_id=? and field='LOCALIZATION'",Object.class,UUID.fromString(id)));
    }

    @Test void emptyInvalidAndUnknownDoNotBecomeUnsupportedAndInvalidInputsRollBack() throws Exception {
        String id=create("");
        for(String state: new String[]{"EMPTY","INVALID","UNKNOWN"}) {
            send(patch(ADMIN_GAMES+"/"+id),admin(),"{\"version\":"+version(id)+",\"localization\":{\"dataState\":\""+state+"\",\"languageSupport\":[],\"evidence\":\"Kaynak veri durumu kontrol edildi\"}}")
                    .andExpect(status().isOk());
            mvc.perform(get(metadata(id))).andExpect(jsonPath("$.languageDataState").value(state)).andExpect(jsonPath("$.languageSupport").isEmpty());
        }
        for(String local: new String[]{"{\"languageSupport\":[{\"languageCode\":\"tr\"},{\"languageCode\":\"Turkish\"}],\"evidence\":\"Yinelenen dil kaydı kontrolü\"}",
                "{\"metadataLanguage\":\"<b>Turkish</b>\",\"evidence\":\"Geçersiz dil girdisi kontrolü\"}",
                "{\"dataState\":\"AVAILABLE\",\"evidence\":\"Destek listesi olmadan dolu olamaz\"}",
                "{\"verified\":true,\"evidence\":\"Bilinmeyen bilgi doğrulanmış sayılmaz\"}",
                "{\"metadataLanguage\":\"tr\",\"source\":\"STEAM\"}"}) {
            long current=version(id);
            send(patch(ADMIN_GAMES+"/"+id),admin(),"{\"version\":"+current+",\"localization\":"+local+"}").andExpect(status().isBadRequest());
            assertEquals(current,version(id));
        }
    }

    @Test void localizationMutationRemainsAdminOnlyAndVersioned() throws Exception {
        String id=create(""); String body="{\"version\":"+version(id)+",\"localization\":"+LOCALIZATION+"}";
        send(patch(ADMIN_GAMES+"/"+id),null,body).andExpect(status().isUnauthorized());
        send(patch(ADMIN_GAMES+"/"+id),developer(),body).andExpect(status().isForbidden());
        send(patch(ADMIN_GAMES+"/"+id),moderator(),body).andExpect(status().isForbidden());
        send(patch(ADMIN_GAMES+"/"+id),admin(),body).andExpect(status().isOk());
        send(patch(ADMIN_GAMES+"/"+id),admin(),body).andExpect(status().isConflict());
    }

    @Test void externalLocalizationUsesExistingManualLockAndSurvivesCatalogSync() throws Exception {
        provider.page(42,"Original",false); imports.startInitial(); imports.synchronizeNextPage();
        String id=refs.findByProviderAndExternalId("STEAM","42").orElseThrow().getGame().getId().toString();
        em.flush(); em.clear();
        send(patch(ADMIN_GAMES+"/"+id),admin(),"{\"version\":"+version(id)+",\"localization\":"+LOCALIZATION+"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.manualOverrides[0].field").value("LOCALIZATION"));
        provider.page(42,"Refreshed",false); imports.startIncremental(); imports.importNextPage(1000); em.flush(); em.clear();
        mvc.perform(get(metadata(id))).andExpect(jsonPath("$.languageSupport[0].interfaceSupport").value("SUPPORTED"));
        assertEquals("ADMIN",jdbc.queryForObject("select source from game_metadata_provenance where game_id=? and field='LOCALIZATION'",String.class,UUID.fromString(id)));
        assertEquals("STEAM",jdbc.queryForObject("select source from game_metadata_provenance where game_id=? and field='TITLE'",String.class,UUID.fromString(id)));
    }
}
