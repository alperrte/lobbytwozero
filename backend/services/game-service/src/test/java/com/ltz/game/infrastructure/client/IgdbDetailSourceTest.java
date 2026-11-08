package com.ltz.game.infrastructure.client;

import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.enums.LanguageSupportStatus;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.client.FakeServer.Reply;
import com.ltz.game.infrastructure.config.GameRatingConfiguration;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** IGDB detay kaynağı: sahte Twitch ve IGDB sunucuları; gerçek ağ, gerçek anahtar veya canlı IGDB çağrısı yoktur. */
class IgdbDetailSourceTest {
    private static final String SECRET = "test-only-secret-value";
    private static final String TOKEN = "{\"access_token\":\"tok-1\",\"expires_in\":5000000,\"token_type\":\"bearer\"}";
    private static final String STEAM_SOURCE = "[{\"id\":1,\"name\":\"Steam\"}]";
    private static final String EXTERNAL = "[{\"id\":9,\"game\":777,\"uid\":\"570\"}]";
    private static final String GAME = "[{\"id\":777,\"summary\":\"  A summary.  \",\"first_release_date\":1373241600,"
            + "\"involved_companies\":["
            + "{\"id\":1,\"developer\":true,\"publisher\":false,\"company\":{\"id\":5,\"name\":\"Valve\"}},"
            + "{\"id\":2,\"developer\":false,\"publisher\":true,\"company\":{\"id\":6,\"name\":\"Valve Publishing\"}},"
            + "{\"id\":3,\"developer\":false,\"publisher\":false,\"company\":{\"id\":7,\"name\":\"Porting House\"}}],"
            + "\"language_supports\":["
            + "{\"language\":{\"locale\":\"en-US\"},\"language_support_type\":{\"name\":\"Audio\"}},"
            + "{\"language\":{\"locale\":\"en-US\"},\"language_support_type\":{\"name\":\"Interface\"}},"
            + "{\"language\":{\"locale\":\"tr_TR\"},\"language_support_type\":{\"name\":\"Subtitles\"}},"
            + "{\"language\":{\"locale\":\"de-DE\"},\"language_support_type\":{\"name\":\"Mystery\"}},"
            + "{\"language\":{\"locale\":\"not a locale!\"},\"language_support_type\":{\"name\":\"Audio\"}}]}]";

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-05T10:00:00Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration duration) { now = now.plus(duration); }
    }

    /** İki sahte sunucu + kaynak; kapatma try-with-resources ile yapılır. */
    private static final class Rig implements AutoCloseable {
        final FakeServer api;
        final FakeServer token;
        final MutableClock clock = new MutableClock();
        final IgdbDetailSource source;

        Rig(Duration interval) throws IOException {
            this(interval, false);
        }

        /** {@code happy}: kaynak kimliği, eşleme ve oyun yanıtları sıraya konur. */
        Rig(Duration interval, boolean happy) throws IOException {
            api = new FakeServer();
            token = new FakeServer();
            RestClient http = GameRatingConfiguration.restClient(RestClient.builder(), api.baseUrl(), Duration.ofSeconds(1), Duration.ofSeconds(2));
            source = new IgdbDetailSource(http, clock, "test-client", SECRET, api.baseUrl() + "/v4", token.baseUrl() + "/oauth2/token", interval);
            token.enqueue(Reply.json(200, TOKEN));
            if (happy) {
                api.enqueue(Reply.json(200, STEAM_SOURCE), Reply.json(200, EXTERNAL), Reply.json(200, GAME));
            }
        }

        String code(String appId) {
            return assertThrows(ProviderException.class, () -> source.details(appId)).code();
        }

        @Override public void close() {
            api.close();
            token.close();
        }
    }

    @Test void mapsSteamAppIdToDetailsCompaniesAndLanguagesWithoutInventingAnything() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO, true)) {
            GameProvider.Details details = rig.source.details("570");
            assertEquals("A summary.", details.description());
            assertEquals(LocalDate.of(2013, 7, 8), details.releaseDate());
            assertEquals(Set.of("Valve"), details.developers());
            assertEquals(Set.of("Valve Publishing"), details.publishers());
            assertNull(details.providerType());
            // en-us: arayüz+ses var, altyazı listelenmedi -> UNKNOWN; tr-tr yalnız altyazı; bilinmeyen tür ve bozuk dil atlandı.
            assertEquals(List.of(
                    new GameProvider.LanguageSupport("en-us", LanguageSupportStatus.SUPPORTED, LanguageSupportStatus.SUPPORTED, LanguageSupportStatus.UNKNOWN),
                    new GameProvider.LanguageSupport("tr-tr", LanguageSupportStatus.UNKNOWN, LanguageSupportStatus.UNKNOWN, LanguageSupportStatus.SUPPORTED)),
                    details.languages());
        }
    }

    @Test void sendsDocumentedRequestsWithHeadersAndKeepsTheSecretOutOfTheUrl() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO, true)) {
            rig.source.details("570");
            var requests = rig.api.received();
            assertEquals(List.of("/v4/external_game_sources", "/v4/external_games", "/v4/games"),
                    requests.stream().map(FakeServer.Received::pathAndQuery).toList());
            requests.forEach(request -> {
                assertEquals("POST", request.method());
                assertEquals("Bearer tok-1", request.authorization());
            });
            assertEquals("fields name; where name = \"Steam\"; limit 5;", requests.get(0).body());
            assertEquals("fields game,uid; where external_game_source = 1 & uid = \"570\"; limit 10;", requests.get(1).body());
            assertTrue(requests.get(2).body().contains("where id = 777; limit 1;"));
            assertTrue(requests.get(2).body().contains("language_supports.language.locale"));
            assertTrue(requests.get(2).body().startsWith("fields game_type,"), "tür kanıtı IGDB game_type alanından istenir");
            var tokenRequest = rig.token.received().getFirst();
            assertEquals("POST", tokenRequest.method());
            assertEquals("/oauth2/token", tokenRequest.pathAndQuery(), "gizli anahtar URL'de değil");
            assertTrue(tokenRequest.body().contains("grant_type=client_credentials"));
            assertTrue(tokenRequest.body().contains("client_id=test-client"));
            assertTrue(tokenRequest.body().contains("client_secret=" + SECRET));
        }
    }

    @Test void gameTypeEvidenceIsReportedConservativelyAndTheSourceAdvertisesIt() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO)) {
            assertTrue(rig.source.reportsItemType());
            rig.api.enqueue(Reply.json(200, STEAM_SOURCE), Reply.json(200, EXTERNAL), Reply.json(200, "[{\"id\":777,\"game_type\":1,\"summary\":\"Addon\"}]"));
            assertEquals("dlc", rig.source.details("570").providerType());
        }
        try (Rig rig = new Rig(Duration.ZERO)) {
            // Yalnız tür bilgisi olan yanıt da geçerlidir (başka alan uydurulmaz).
            rig.api.enqueue(Reply.json(200, STEAM_SOURCE), Reply.json(200, EXTERNAL), Reply.json(200, "[{\"id\":777,\"game_type\":0}]"));
            var details = rig.source.details("570");
            assertEquals("main_game", details.providerType()); assertNull(details.description());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(value = {"0,main_game", "8,main_game", "9,main_game", "1,dlc", "2,dlc", "3,bundle", "13,pack",
            "4,dlc", "5,", "6,", "7,", "10,", "11,", "12,", "14,", "15,", "999,", "-1,", "4294967296,"})
    void onlyExplicitIgdbGameTypesAreMappedAndEverythingElseStaysUnknown(long id, String expected) {
        assertEquals(expected, IgdbDetailMapper.providerType(id));
    }

    @Test void missingGameTypeProducesNoTypeEvidence() {
        assertNull(IgdbDetailMapper.providerType(null));
    }

    // ---- Toplu kanıt (itemEvidence): tür + çıkış tarihi
    private static GameProvider.ItemEvidence evidence(String type, String date) {
        return new GameProvider.ItemEvidence(type, date == null ? null : LocalDate.parse(date));
    }

    @Test void bulkEvidenceAsksIgdbOncePerBatchAndMapsTypeAndDateToTheRightGameRegardlessOfResponseOrder() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO)) {
            rig.api.enqueue(Reply.json(200, STEAM_SOURCE),
                    Reply.json(200, "[{\"game\":100,\"uid\":\"1245620\"},{\"game\":200,\"uid\":\"70\"},{\"game\":300,\"uid\":\"440\"},{\"game\":400,\"uid\":\"999\"}]"),
                    // Yanıt sırası istek sırasından farklıdır; eşleme oyun kimliğiyledir.
                    Reply.json(200, "[{\"id\":300,\"game_type\":5,\"first_release_date\":1000000000},{\"id\":100,\"game_type\":0,\"first_release_date\":1645747200},{\"id\":200,\"game_type\":1,\"first_release_date\":1373241600}]"));
            var found = rig.source.itemEvidence(List.of("1245620", "70", "440", "5"));
            assertEquals(java.util.Map.of(
                    "1245620", evidence("main_game", "2022-02-25"),
                    "70", evidence("dlc", "2013-07-08"),
                    "440", evidence(null, "2001-09-09")), found,
                    "mod (5) tür kanıtı değil ama tarihi doğru oyuna aittir; eşlemesiz AppID (5) ve istenmeyen uid (999) sonuçta yok");
            var requests = rig.api.received();
            assertEquals(List.of("/v4/external_game_sources", "/v4/external_games", "/v4/games"),
                    requests.stream().map(FakeServer.Received::pathAndQuery).toList());
            assertEquals("fields game,uid; where external_game_source = 1 & uid = (\"1245620\",\"70\",\"440\",\"5\"); limit 500;", requests.get(1).body());
            assertEquals("fields game_type,first_release_date,genres.slug,genres.name,platforms.slug,platforms.name,game_modes.slug,game_modes.name,themes.slug,themes.name; where id = (100,200,300); limit 500;", requests.get(2).body());
        }
    }

    @Test void bulkEvidenceConvertsUnixSecondsToUtcDaysAndDropsInvalidDatesWithoutFailingThePage() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO)) {
            rig.api.enqueue(Reply.json(200, STEAM_SOURCE),
                    Reply.json(200, "[{\"game\":1,\"uid\":\"1\"},{\"game\":2,\"uid\":\"2\"},{\"game\":3,\"uid\":\"3\"},{\"game\":4,\"uid\":\"4\"},{\"game\":5,\"uid\":\"5\"}]"),
                    Reply.json(200, "[{\"id\":1,\"game_type\":0,\"first_release_date\":1451606399},"      // 2015-12-31T23:59:59Z -> 2015-12-31
                            + "{\"id\":2,\"game_type\":0,\"first_release_date\":1451606400},"            // 2016-01-01T00:00:00Z -> 2016-01-01
                            + "{\"id\":3,\"game_type\":0,\"first_release_date\":1373241600000},"          // milisaniye: geçersiz -> tarih yok
                            + "{\"id\":4,\"game_type\":0},"                                                // tarih yok
                            + "{\"id\":5,\"game_type\":0,\"first_release_date\":-1}]"));                   // 1969-12-31: geçerli negatif
            var found = rig.source.itemEvidence(List.of("1", "2", "3", "4", "5"));
            assertEquals(evidence("main_game", "2015-12-31"), found.get("1"));
            assertEquals(evidence("main_game", "2016-01-01"), found.get("2"));
            assertEquals(evidence("main_game", null), found.get("3"));
            assertEquals(evidence("main_game", null), found.get("4"));
            assertEquals(evidence("main_game", "1969-12-31"), found.get("5"));
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    @org.junit.jupiter.params.provider.ValueSource(longs = {253_402_300_800L, -253_402_300_800L, 1_373_241_600_000L, Long.MAX_VALUE})
    void lenientReleaseDateRejectsOutOfRangeValues(Long value) {
        assertNull(IgdbDetailMapper.lenientReleaseDate(value));
    }

    @Test void bulkEvidenceCarriesGenresAndPlatformsAsLowercaseSlugCodesAndSkipsInvalidOnesWithoutInventingAnything() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO)) {
            rig.api.enqueue(Reply.json(200, STEAM_SOURCE), Reply.json(200, "[{\"game\":7,\"uid\":\"1245620\"},{\"game\":8,\"uid\":\"620\"}]"),
                    Reply.json(200, "[{\"id\":7,\"game_type\":0,"
                            + "\"genres\":[{\"slug\":\"Role-Playing-RPG\",\"name\":\" Role-playing (RPG) \"},{\"slug\":\"adventure\",\"name\":\"Adventure\"},"
                            + "{\"slug\":\"adventure\",\"name\":\"Dup\"},{\"slug\":\"Bad Slug!\",\"name\":\"Invalid code\"},{\"slug\":null,\"name\":\"No slug\"},null,{\"slug\":\"\",\"name\":\"Empty\"}],"
                            + "\"platforms\":[{\"slug\":\"win\",\"name\":\"PC (Microsoft Windows)\"},{\"slug\":\"ps4--1\",\"name\":\"PlayStation 4\"},{\"slug\":\"series-x-s\"},{\"slug\":\"x\",\"name\":\"" + "n".repeat(129) + "\"}]},"
                            + "{\"id\":8,\"genres\":[],\"platforms\":[]}]"));
            var found = rig.source.itemEvidence(List.of("1245620", "620"));
            var evidence = found.get("1245620");
            assertEquals("main_game", evidence.providerType());
            assertEquals(List.of(new GameProvider.TermEvidence("role-playing-rpg", "Role-playing (RPG)"), new GameProvider.TermEvidence("adventure", "Adventure")), evidence.genres());
            assertEquals(List.of(new GameProvider.TermEvidence("win", "PC (Microsoft Windows)"), new GameProvider.TermEvidence("ps4--1", "PlayStation 4"),
                    new GameProvider.TermEvidence("series-x-s", null), new GameProvider.TermEvidence("x", null)), evidence.platforms());
            assertNull(found.get("620"), "tür, tarih, tür/platform kanıtı yoksa kayıt sonuçta yer almaz");
        }
    }

    @Test void gameModesBecomeCategoriesAndThemesBecomeTagsWhileKeywordsAreNeverMapped() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO)) {
            rig.api.enqueue(Reply.json(200, STEAM_SOURCE), Reply.json(200, "[{\"game\":7,\"uid\":\"1245620\"}]"),
                    Reply.json(200, "[{\"id\":7,\"game_type\":0,"
                            + "\"genres\":[{\"slug\":\"adventure\",\"name\":\"Adventure\"}],"
                            + "\"game_modes\":[{\"slug\":\"single-player\",\"name\":\"Single player\"},{\"slug\":\"co-operative\",\"name\":\"Co-operative\"}],"
                            + "\"themes\":[{\"slug\":\"fantasy\",\"name\":\"Fantasy\"},{\"slug\":\"Open World\",\"name\":\"invalid code\"}],"
                            + "\"keywords\":[{\"slug\":\"user-noise\",\"name\":\"never mapped\"}]}]"));
            var evidence = rig.source.itemEvidence(List.of("1245620")).get("1245620");
            assertEquals(List.of(new GameProvider.TermEvidence("adventure", "Adventure")), evidence.genres(), "tür ayrı kavramdır");
            assertEquals(List.of(new GameProvider.TermEvidence("single-player", "Single player"), new GameProvider.TermEvidence("co-operative", "Co-operative")), evidence.categories());
            assertEquals(List.of(new GameProvider.TermEvidence("fantasy", "Fantasy")), evidence.tags(), "geçersiz kod atlanır");
            assertTrue(rig.api.received().get(2).body().contains("game_modes.slug") && rig.api.received().get(2).body().contains("themes.slug"));
            assertTrue(!rig.api.received().get(2).body().contains("keywords"), "keywords sorgulanmaz");
        }
    }

    @Test void relationEvidenceCarriesTheIgdbTypeIdAndResolvesTheParentOnlyThroughIgdbSteamRecords() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO)) {
            rig.api.enqueue(Reply.json(200, STEAM_SOURCE),
                    Reply.json(200, "[{\"game\":7,\"uid\":\"10\"},{\"game\":8,\"uid\":\"20\"},{\"game\":9,\"uid\":\"30\"},{\"game\":11,\"uid\":\"40\"},"
                            + "{\"game\":1,\"uid\":\"50\"},{\"game\":2,\"uid\":\"50\"}]"),
                    Reply.json(200, "[{\"id\":7,\"game_type\":13,\"parent_game\":100},{\"id\":8,\"game_type\":1,\"parent_game\":200},"
                            + "{\"id\":9,\"game_type\":13},{\"id\":11,\"game_type\":3,\"parent_game\":400}]"),
                    Reply.json(200, "[{\"game\":100,\"uid\":\"5\"},{\"game\":200,\"uid\":\"6\"},{\"game\":200,\"uid\":\"7\"}]"));
            var found = rig.source.relationEvidence(List.of("10", "20", "30", "40", "50"));
            assertEquals(new GameProvider.RelationEvidence(13, GameProvider.ParentState.LINKED, "5"), found.get("10"));
            assertEquals(new GameProvider.RelationEvidence(1, GameProvider.ParentState.AMBIGUOUS, null), found.get("20"), "üst oyunun birden çok Steam kaydı varsa seçilmez");
            assertEquals(new GameProvider.RelationEvidence(13, GameProvider.ParentState.NONE, null), found.get("30"));
            assertEquals(new GameProvider.RelationEvidence(3, GameProvider.ParentState.UNMAPPED, null), found.get("40"), "üst oyunun Steam kaydı yoksa bağlanmaz");
            assertNull(found.get("50"), "belirsiz AppID eşlemesi kanıt sayılmaz");
            String parentQuery = rig.api.received().get(3).body();
            assertTrue(parentQuery.contains("game = (100,200,400)") && parentQuery.contains("external_game_source ="));
            assertTrue(rig.api.received().get(2).body().contains("parent_game"));
        }
    }

    @Test void bulkDetailsCarryCoverCompaniesAndLanguagesAndSkipUnmappedOrInvalidGames() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO)) {
            rig.api.enqueue(Reply.json(200, STEAM_SOURCE),
                    Reply.json(200, "[{\"game\":7,\"uid\":\"10\"},{\"game\":8,\"uid\":\"20\"},{\"game\":9,\"uid\":\"30\"}]"),
                    Reply.json(200, "[{\"id\":7,\"summary\":\"S\",\"game_type\":0,\"cover\":{\"image_id\":\"co4sbw\"},"
                            + "\"involved_companies\":[{\"developer\":true,\"publisher\":false,\"company\":{\"name\":\"Dev\"}},{\"developer\":false,\"publisher\":true,\"company\":{\"name\":\"Pub\"}},"
                            + "{\"developer\":false,\"publisher\":false,\"company\":{\"name\":\"Support\"}}],"
                            + "\"language_supports\":[{\"language\":{\"locale\":\"en-US\"},\"language_support_type\":{\"name\":\"Audio\"}}]},"
                            + "{\"id\":9,\"summary\":\"X\",\"first_release_date\":999999999999999,\"cover\":{\"image_id\":\"../bad\"}}]"));
            var found = rig.source.bulkDetails(List.of("10", "20", "30"));
            var details = found.get("10");
            assertEquals("https://images.igdb.com/igdb/image/upload/t_cover_big/co4sbw.jpg", details.coverUrl());
            assertEquals(java.util.Set.of("Dev"), details.developers(), "yalnız developer işaretli şirket geliştirici sayılır");
            assertEquals(java.util.Set.of("Pub"), details.publishers());
            assertEquals("en-us", details.languages().getFirst().languageCode());
            assertNull(found.get("20"), "IGDB yanıtında olmayan oyun sonuçta yer almaz");
            assertNull(found.get("30"), "geçersiz tarih yalnız o oyunu düşürür");
            assertTrue(rig.api.received().get(2).body().contains("cover.image_id"));
        }
    }

    @Test void relationEvidenceRejectsInvalidIdsAndTruncatedResponses() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO)) {
            assertEquals(java.util.Map.of(), rig.source.relationEvidence(List.of()));
            assertEquals("INVALID_PROVIDER_REQUEST", org.junit.jupiter.api.Assertions.assertThrows(ProviderException.class,
                    () -> rig.source.relationEvidence(List.of("0abc"))).code());
        }
    }

    @Test void termEvidenceIsCappedAndRejectsCodesThatTheCatalogWouldNotAccept() {
        var many = new java.util.ArrayList<com.ltz.game.infrastructure.client.dto.response.IgdbResponses.Term>();
        for (int index = 0; index < 80; index++) many.add(new com.ltz.game.infrastructure.client.dto.response.IgdbResponses.Term("t" + index, "T" + index));
        assertEquals(50, IgdbDetailMapper.termEvidence(many).size());
        assertTrue(IgdbDetailMapper.termEvidence(List.of(new com.ltz.game.infrastructure.client.dto.response.IgdbResponses.Term("a".repeat(65), "Too long"))).isEmpty());
        assertTrue(IgdbDetailMapper.termEvidence(List.of(new com.ltz.game.infrastructure.client.dto.response.IgdbResponses.Term("-leading", "x"))).isEmpty());
        assertTrue(IgdbDetailMapper.termEvidence(null).isEmpty());
    }

    @Test void bulkEvidenceDoesNotTreatAmbiguousMappingsAsEvidence() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO)) {
            rig.api.enqueue(Reply.json(200, STEAM_SOURCE),
                    Reply.json(200, "[{\"game\":1,\"uid\":\"10\"},{\"game\":2,\"uid\":\"10\"},{\"game\":3,\"uid\":\"20\"}]"),
                    Reply.json(200, "[{\"id\":1,\"game_type\":0},{\"id\":2,\"game_type\":0},{\"id\":3,\"game_type\":0}]"));
            assertEquals(java.util.Map.of("20", evidence("main_game", null)), rig.source.itemEvidence(List.of("10", "20")));
        }
    }

    @Test void bulkEvidenceIgnoresPartialResponsesWithoutTheGameAndRowsWithoutUsableFields() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO)) {
            rig.api.enqueue(Reply.json(200, STEAM_SOURCE),
                    Reply.json(200, "[{\"game\":1,\"uid\":\"10\"},{\"game\":2,\"uid\":\"20\"},{\"game\":3,\"uid\":\"30\"}]"),
                    // 1 yanıtta yok, 2'de tür bilinmiyor ve tarih yok, 3 geçerli
                    Reply.json(200, "[{\"id\":2,\"game_type\":99},{\"id\":3,\"game_type\":9}]"));
            assertEquals(java.util.Map.of("30", evidence("main_game", null)), rig.source.itemEvidence(List.of("10", "20", "30")));
        }
    }

    @Test void bulkEvidenceWithoutAnyMappingSkipsTheGamesRequest() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO)) {
            rig.api.enqueue(Reply.json(200, STEAM_SOURCE), Reply.json(200, "[]"));
            assertTrue(rig.source.itemEvidence(List.of("10")).isEmpty());
            assertEquals(2, rig.api.received().size());
            assertTrue(rig.source.itemEvidence(List.of()).isEmpty());
            assertEquals(2, rig.api.received().size(), "boş istek ağa çıkmaz");
        }
    }

    @Test void bulkEvidenceRejectsInvalidRequestsAndTruncatedMappingResponses() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO)) {
            assertEquals("INVALID_PROVIDER_REQUEST", assertThrows(ProviderException.class, () -> rig.source.itemEvidence(List.of("abc"))).code());
            assertEquals("INVALID_PROVIDER_REQUEST", assertThrows(ProviderException.class, () -> rig.source.itemEvidence(List.of("0"))).code());
            var tooMany = new java.util.ArrayList<String>();
            for (int id = 1; id <= IgdbDetailSource.MAX_BATCH + 1; id++) tooMany.add(Integer.toString(id));
            assertEquals("INVALID_PROVIDER_REQUEST", assertThrows(ProviderException.class, () -> rig.source.itemEvidence(tooMany)).code());
            assertTrue(rig.api.received().isEmpty(), "geçersiz istek sağlayıcıya gitmez");
            var rows = new StringBuilder("[");
            for (int row = 0; row < 500; row++) rows.append(row == 0 ? "" : ",").append("{\"game\":").append(row + 1).append(",\"uid\":\"10\"}");
            rig.api.enqueue(Reply.json(200, STEAM_SOURCE), Reply.json(200, rows.append("]").toString()));
            assertEquals("INVALID_PROVIDER_RESPONSE", assertThrows(ProviderException.class, () -> rig.source.itemEvidence(List.of("10"))).code());
        }
    }

    @Test void bulkEvidenceMapsProviderFailuresToSafeCodes() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO)) {
            rig.api.enqueue(Reply.json(200, STEAM_SOURCE), Reply.json(429, "{}"));
            assertEquals("RATE_LIMITED", assertThrows(ProviderException.class, () -> rig.source.itemEvidence(List.of("10"))).code());
        }
        try (Rig rig = new Rig(Duration.ZERO)) {
            rig.api.enqueue(Reply.json(200, STEAM_SOURCE), Reply.json(200, "[{\"game\":1,\"uid\":\"10\"}]"), Reply.json(503, "{}"));
            assertEquals("PROVIDER_UNAVAILABLE", assertThrows(ProviderException.class, () -> rig.source.itemEvidence(List.of("10"))).code());
        }
        try (Rig rig = new Rig(Duration.ZERO)) {
            rig.api.enqueue(Reply.json(200, STEAM_SOURCE), Reply.json(200, "not json"));
            assertEquals("INVALID_PROVIDER_RESPONSE", assertThrows(ProviderException.class, () -> rig.source.itemEvidence(List.of("10"))).code());
        }
    }

    @Test void bulkRequestsAreSpacedByTheConfiguredMinimumInterval() throws IOException {
        try (Rig rig = new Rig(Duration.ofMillis(120))) {
            rig.api.enqueue(Reply.json(200, STEAM_SOURCE), Reply.json(200, "[{\"game\":1,\"uid\":\"10\"}]"), Reply.json(200, "[{\"id\":1,\"game_type\":0}]"));
            long start = System.nanoTime();
            rig.source.itemEvidence(List.of("10"));
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
            assertTrue(elapsedMillis >= 240, "3 istek arasında iki bekleme (≥ 2x120 ms) olmalı: " + elapsedMillis + " ms");
        }
    }

    @Test void reusesTokenAndSteamSourceIdAcrossGamesThenRefreshesTheTokenBeforeExpiry() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO, true)) {
            rig.source.details("570");
            rig.api.enqueue(Reply.json(200, "[{\"id\":10,\"game\":777,\"uid\":\"730\"}]"), Reply.json(200, GAME));
            rig.source.details("730");
            assertEquals(5, rig.api.received().size(), "kaynak kimliği ikinci oyunda yeniden sorgulanmadı");
            assertEquals(1, rig.token.received().size(), "token yeniden kullanıldı");
            rig.token.enqueue(Reply.json(200, TOKEN.replace("tok-1", "tok-2")));
            rig.clock.advance(Duration.ofSeconds(5_000_000)); // süre doldu
            rig.api.enqueue(Reply.json(200, EXTERNAL), Reply.json(200, GAME));
            rig.source.details("570");
            assertEquals(2, rig.token.received().size());
            assertEquals("Bearer tok-2", rig.api.received().getLast().authorization());
        }
    }

    @Test void zeroUnmatchedOrAmbiguousCandidatesAreNeverAutoMapped() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO)) {
            rig.api.enqueue(Reply.json(200, STEAM_SOURCE), Reply.json(200, "[]"));
            assertEquals("PROVIDER_ITEM_NOT_FOUND", rig.code("570"));
            rig.api.enqueue(Reply.json(200, "[{\"game\":1,\"uid\":\"999\"}]")); // başka uid: kanıt değil
            assertEquals("PROVIDER_ITEM_NOT_FOUND", rig.code("570"));
            rig.api.enqueue(Reply.json(200, "[{\"game\":1,\"uid\":\"570\"},{\"game\":2,\"uid\":\"570\"}]"));
            assertEquals("AMBIGUOUS_PROVIDER_MAPPING", rig.code("570"));
            rig.api.enqueue(Reply.json(200, "[{\"game\":1,\"uid\":\"570\"},{\"game\":1,\"uid\":\"570\"}]"), Reply.json(200, GAME));
            assertEquals("A summary.", rig.source.details("570").description(), "aynı oyunun tekrarı belirsizlik değildir");
        }
    }

    @Test void unknownOrAmbiguousSteamSourceFailsClosed() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO)) {
            rig.api.enqueue(Reply.json(200, "[]"));
            assertEquals("INVALID_PROVIDER_RESPONSE", rig.code("570"));
            rig.api.enqueue(Reply.json(200, "[{\"id\":1,\"name\":\"Steam\"},{\"id\":2,\"name\":\"steam\"}]"));
            assertEquals("AMBIGUOUS_PROVIDER_MAPPING", rig.code("570"));
            rig.api.enqueue(Reply.json(200, "[{\"id\":3,\"name\":\"Steam Deck\"}]"));
            assertEquals("INVALID_PROVIDER_RESPONSE", rig.code("570"));
        }
    }

    @Test void invalidAppIdsNeverReachTheNetworkOrTheQueryLanguage() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO)) {
            for (String appId : new String[]{null, "", "abc", "0570", "570\"; limit 500; where uid = \"1", "12345678901", "-1", " 570"}) {
                assertEquals("INVALID_PROVIDER_REQUEST", rig.code(appId), String.valueOf(appId));
            }
            assertTrue(rig.api.received().isEmpty());
            assertTrue(rig.token.received().isEmpty());
        }
    }

    @Test void accessDeniedDropsTheTokenAndTheNextCallFetchesANewOneWithoutRetrying() throws IOException {
        for (int status : new int[]{401, 403}) {
            try (Rig rig = new Rig(Duration.ZERO)) {
                rig.api.enqueue(Reply.json(status, "{\"message\":\"secret echo " + SECRET + "\"}"));
                var error = assertThrows(ProviderException.class, () -> rig.source.details("570"));
                assertEquals("PROVIDER_ACCESS_DENIED", error.code());
                assertFalse(error.getMessage().contains(SECRET));
                assertEquals(1, rig.api.received().size(), "aynı çağrıda yeniden deneme yok");
                rig.token.enqueue(Reply.json(200, TOKEN.replace("tok-1", "tok-2")));
                rig.api.enqueue(Reply.json(200, STEAM_SOURCE), Reply.json(200, EXTERNAL), Reply.json(200, GAME));
                rig.source.details("570");
                assertEquals(2, rig.token.received().size());
            }
        }
    }

    @Test void mapsRateLimitServerAndOtherStatusesToTheRetryPolicyCodes() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO)) {
            rig.api.enqueue(new Reply(429, "{}", 0, null));
            assertEquals("RATE_LIMITED", rig.code("570"));
            rig.api.enqueue(Reply.json(503, "{}"));
            assertEquals("PROVIDER_UNAVAILABLE", rig.code("570"));
            rig.api.enqueue(Reply.json(400, "{}"));
            assertEquals("INVALID_PROVIDER_RESPONSE", rig.code("570"));
        }
    }

    @Test void transportFailureIsAGenericCodeWithoutDetails() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO)) {
            rig.api.close();
            var error = assertThrows(ProviderException.class, () -> rig.source.details("570"));
            assertEquals("PROVIDER_TRANSPORT_ERROR", error.code());
            assertNull(error.getCause());
        }
    }

    @Test void twitchTokenFailuresAreReportedWithoutLeakingTheSecret() throws IOException {
        for (int status : new int[]{400, 401}) {
            try (Rig rig = new Rig(Duration.ZERO)) {
                rig.token.close();
                try (FakeServer twitch = new FakeServer()) {
                    twitch.enqueue(Reply.json(status, "{\"message\":\"invalid client secret " + SECRET + "\"}"));
                    RestClient http = GameRatingConfiguration.restClient(RestClient.builder(), rig.api.baseUrl(), Duration.ofSeconds(1), Duration.ofSeconds(2));
                    var source = new IgdbDetailSource(http, rig.clock, "test-client", SECRET, rig.api.baseUrl() + "/v4", twitch.baseUrl() + "/oauth2/token", Duration.ZERO);
                    var error = assertThrows(ProviderException.class, () -> source.details("570"));
                    assertEquals("PROVIDER_ACCESS_DENIED", error.code());
                    assertFalse(error.getMessage().contains(SECRET));
                    assertTrue(rig.api.received().isEmpty(), "token yokken IGDB'ye istek atılmaz");
                }
            }
        }
    }

    @Test void emptyTokenOrMalformedBodiesAreInvalidResponses() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO)) {
            try (FakeServer badToken = new FakeServer()) {
                badToken.enqueue(Reply.json(200, "{\"access_token\":\"\",\"expires_in\":100}"),
                        Reply.json(200, "{\"access_token\":\"x\",\"expires_in\":0}"), Reply.json(200, "not json"));
                RestClient http = GameRatingConfiguration.restClient(RestClient.builder(), rig.api.baseUrl(), Duration.ofSeconds(1), Duration.ofSeconds(2));
                var source = new IgdbDetailSource(http, rig.clock, "test-client", SECRET, rig.api.baseUrl() + "/v4", badToken.baseUrl() + "/t", Duration.ZERO);
                for (int i = 0; i < 3; i++) {
                    assertEquals("INVALID_PROVIDER_RESPONSE", assertThrows(ProviderException.class, () -> source.details("570")).code());
                }
            }
            rig.api.enqueue(Reply.json(200, "not json"));
            assertEquals("INVALID_PROVIDER_RESPONSE", rig.code("570"));
            rig.api.enqueue(Reply.json(200, "{\"not\":\"an array\"}"));
            assertEquals("INVALID_PROVIDER_RESPONSE", rig.code("570"));
        }
    }

    @Test void providerWithNoUsableFieldsOrAnImpossibleDateIsRejected() throws IOException {
        try (Rig rig = new Rig(Duration.ZERO)) {
            rig.api.enqueue(Reply.json(200, STEAM_SOURCE), Reply.json(200, EXTERNAL), Reply.json(200, "[{\"id\":777,\"summary\":\"  \"}]"));
            assertEquals("INVALID_PROVIDER_RESPONSE", rig.code("570"), "hiç alan yoksa uydurulmaz");
            rig.api.enqueue(Reply.json(200, EXTERNAL), Reply.json(200, "[{\"id\":777,\"summary\":\"ok\",\"first_release_date\":999999999999999}]"));
            assertEquals("INVALID_PROVIDER_RESPONSE", rig.code("570"));
            rig.api.enqueue(Reply.json(200, EXTERNAL), Reply.json(200, "[]"));
            assertEquals("PROVIDER_ITEM_NOT_FOUND", rig.code("570"));
            rig.api.enqueue(Reply.json(200, EXTERNAL), Reply.json(200, "[{\"id\":777,\"summary\":\"" + "x".repeat(16001) + "\"}]"));
            assertEquals("INVALID_PROVIDER_RESPONSE", rig.code("570"), "aşırı uzun metin reddedilir, kırpılmaz");
        }
    }

    @Test void requestsAreSpacedBelowTheProviderRateLimit() throws IOException {
        try (Rig rig = new Rig(Duration.ofMillis(150), true)) {
            long started = System.nanoTime();
            rig.source.details("570");
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            assertTrue(elapsedMillis >= 250, "3 istek arasında 2 aralık (en az ~300 ms) beklenir: " + elapsedMillis);
        }
    }
}
