package com.ltz.game.infrastructure.client;

import com.ltz.game.application.port.GameProvider.Capability;
import com.ltz.game.application.port.GameProvider.CatalogItem;
import com.ltz.game.application.port.GameProvider.CatalogQuery;
import com.ltz.game.domain.exception.ProviderException;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SteamGameProviderTest {
    // Sabit test girdisi; gerçek anahtar değildir.
    private static final String TEST_KEY = "synthetic-test-value";
    MockRestServiceServer server;
    SteamGameProvider provider;
    @BeforeEach void setup() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new SteamGameProvider(builder.build(), TEST_KEY, true,
                new SteamRequestBudget(Clock.fixed(Instant.parse("2026-10-20T00:00:00Z"), ZoneOffset.UTC)));
    }
    @Test void dlcOnlyListIsPagedPacedWithinTheRequestBudgetWithGamesExcludedAndReturnedAsAppIdSet() {
        var clock = new Clock() {
            Instant now = Instant.parse("2026-10-20T00:00:00Z");
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now; }
        };
        RestClient.Builder builder = RestClient.builder();
        var dlcServer = MockRestServiceServer.bindTo(builder).build();
        var dlcProvider = new SteamGameProvider(builder.build(), TEST_KEY, true, new SteamRequestBudget(clock), millis -> clock.now = clock.now.plusMillis(millis));
        dlcServer.expect(method(HttpMethod.GET)).andExpect(request -> {
            String query = java.net.URLDecoder.decode(request.getURI().getRawQuery(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(query.contains("\"include_games\":false") && query.contains("\"include_dlc\":true"));
            assertTrue(query.contains("\"last_appid\":0") && !query.contains("if_modified_since"));
        }).andRespond(withSuccess("""
                {"response":{"apps":[{"appid":20,"name":"D1","last_modified":1},{"appid":30,"name":"D2","last_modified":1}],
                "last_appid":30,"have_more_results":true}}
                """, MediaType.APPLICATION_JSON));
        dlcServer.expect(method(HttpMethod.GET)).andExpect(request -> {
            String query = java.net.URLDecoder.decode(request.getURI().getRawQuery(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(query.contains("\"last_appid\":30"));
        }).andRespond(withSuccess("""
                {"response":{"apps":[{"appid":40,"name":"D3","last_modified":1}]}}
                """, MediaType.APPLICATION_JSON));
        assertEquals(java.util.Set.of("20", "30", "40"), dlcProvider.catalogDlcIds());
        dlcServer.verify();
    }

    @Test void storeItemsCarryHeaderDevelopersPublishersAndLanguagesFromSteamsOwnBulkResponse() {
        server.expect(method(HttpMethod.GET)).andExpect(request -> {
            assertEquals("api.steampowered.com", request.getURI().getHost());
            assertEquals("/IStoreBrowseService/GetItems/v1/", request.getURI().getPath());
            assertEquals(TEST_KEY, request.getHeaders().getFirst("x-webapi-key"));
            String query = java.net.URLDecoder.decode(request.getURI().getRawQuery(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(query.contains("{\"appid\":3595230}") && query.contains("\"include_assets\":true") && query.contains("\"include_basic_info\":true")
                    && query.contains("\"include_supported_languages\":true"));
            assertFalse(request.getURI().toString().contains(TEST_KEY));
        }).andRespond(withSuccess("""
                {"response":{"store_items":[
                 {"appid":3595230,"assets":{"asset_url_format":"steam/apps/3595230/${FILENAME}?t=1","header":"ce4d5e53b36cb9d3c4309d1df72bf8663bbbc7ef/header.jpg"},
                  "basic_info":{"developers":[{"name":"Infinity Ward"},{"name":" Treyarch "},{"name":""}],"publishers":[{"name":"Activision"}]},
                  "supported_languages":[{"elanguage":0,"supported":true,"full_audio":true,"subtitles":true},{"elanguage":22,"supported":true,"full_audio":false,"subtitles":true},
                   {"elanguage":25,"supported":true,"full_audio":false,"subtitles":false},{"elanguage":20,"supported":true},{"elanguage":23,"supported":true},{"elanguage":99,"supported":true},
                   {"elanguage":3,"supported":false}]},
                 {"appid":570,"assets":{"asset_url_format":"steam/apps/570/${FILENAME}?t=2","header":"header.jpg"}},
                 {"appid":1091500,"assets":{"asset_url_format":"steam/apps/1091500/${FILENAME}?t=3","header":"48a93d6445a67520ea49833c93dae8f2e68fc540/header_alt_assets_9.jpg"}},
                 {"appid":42,"success":2},
                 {"appid":43,"assets":{"asset_url_format":"steam/apps/999/${FILENAME}","header":"header.jpg"}},
                 {"appid":44,"assets":{"asset_url_format":"steam/apps/44/${FILENAME}","header":"../evil.jpg"}},
                 {"appid":777,"assets":{"asset_url_format":"steam/apps/777/${FILENAME}","header":"header.jpg"}}]}}
                """, MediaType.APPLICATION_JSON));
        var found = provider.storeItems(java.util.List.of("3595230", "570", "1091500", "42", "43", "44"));
        var cod = found.get("3595230");
        assertEquals("https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/3595230/ce4d5e53b36cb9d3c4309d1df72bf8663bbbc7ef/header.jpg", cod.headerImageUrl());
        assertEquals(java.util.Set.of("Infinity Ward", "Treyarch"), cod.developers());
        assertEquals(java.util.Set.of("Activision"), cod.publishers());
        assertEquals(java.util.List.of("en-us", "pt-br", "ar"), cod.languages().stream().map(l -> l.languageCode()).toList(),
                "desteklenmeyen (3), ayırt edilemeyen (20, 23) ve bilinmeyen (99) dil numaraları atlanır; dil uydurulmaz");
        assertEquals(com.ltz.game.domain.enums.LanguageSupportStatus.SUPPORTED, cod.languages().get(0).audioSupport());
        assertEquals(com.ltz.game.domain.enums.LanguageSupportStatus.UNKNOWN, cod.languages().get(1).audioSupport(), "full_audio=false ses için UNKNOWN (UNSUPPORTED iddiası yok)");
        assertEquals(com.ltz.game.domain.enums.LanguageSupportStatus.SUPPORTED, cod.languages().get(1).subtitleSupport());
        assertEquals("https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/570/header.jpg", found.get("570").headerImageUrl());
        assertEquals("https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/1091500/48a93d6445a67520ea49833c93dae8f2e68fc540/header_alt_assets_9.jpg", found.get("1091500").headerImageUrl());
        assertEquals(java.util.Set.of("3595230", "570", "1091500"), found.keySet(), "hiçbir alanı olmayan, başka uygulamaya ait adresli, geçersiz dosyalı ve istenmeyen kayıtlar atlanır");
    }

    @Test void releaseStatusComesFromSteamsFlagsAndOnlyForAppsSteamRecognises() {
        server.expect(method(HttpMethod.GET)).andExpect(request -> {
            assertEquals("/IStoreBrowseService/GetItems/v1/", request.getURI().getPath());
            assertEquals(TEST_KEY, request.getHeaders().getFirst("x-webapi-key"));
            String query = java.net.URLDecoder.decode(request.getURI().getRawQuery(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(query.contains("{\"appid\":1966720}") && query.contains("\"include_release\":true"));
            assertFalse(query.contains("include_assets") || query.contains("include_basic_info"), "yalnız çıkış bilgisi istenir");
            assertFalse(request.getURI().toString().contains(TEST_KEY));
        }).andRespond(withSuccess("""
                {"response":{"store_items":[
                 {"appid":1966720,"release":{"steam_release_date":1698095392,"is_early_access":true}},
                 {"appid":570,"release":{"steam_release_date":1373389200,"original_release_date":0}},
                 {"appid":730,"is_free":true,"release":{"steam_release_date":1345000000}},
                 {"appid":4087790,"release":{"steam_release_date":1830240000,"is_coming_soon":true,"coming_soon_display":"date_year"}},
                 {"appid":1623730,"release":{"steam_release_date":1783652772,"release_from_early_access_date":1783666800}},
                 {"appid":42,"success":15},
                 {"appid":777,"release":{"is_early_access":true}}]}}
                """, MediaType.APPLICATION_JSON));
        var found = provider.releaseStatus(java.util.List.of("1966720", "570", "730", "4087790", "1623730", "42"));
        assertEquals(java.util.Map.of(
                "1966720", new com.ltz.game.application.port.GameProvider.ReleaseStatus(true, false, false),
                "570", new com.ltz.game.application.port.GameProvider.ReleaseStatus(false, false, false),
                "730", new com.ltz.game.application.port.GameProvider.ReleaseStatus(false, true, false),
                "4087790", new com.ltz.game.application.port.GameProvider.ReleaseStatus(false, false, true),
                "1623730", new com.ltz.game.application.port.GameProvider.ReleaseStatus(false, false, false)), found,
                "bayrak yoksa false; tanınmayan uygulama (42) ve istenmeyen kimlik (777) sonuçta yok");
        server.verify();
    }

    @Test void releaseStatusAlsoCarriesAdultContentDescriptorsAndTheStoreType() {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess("""
                {"response":{"store_items":[
                 {"appid":1,"type":0,"content_descriptorids":[1,2,5],"release":{}},
                 {"appid":2,"type":0,"content_descriptorids":[5,3],"release":{}},
                 {"appid":3,"type":4,"content_descriptorids":[4],"release":{}},
                 {"appid":4,"type":1,"release":{}},
                 {"appid":5,"content_descriptorids":"bozuk","release":{}}]}}
                """, MediaType.APPLICATION_JSON));
        var found = provider.releaseStatus(java.util.List.of("1", "2", "3", "4", "5"));
        assertFalse(found.get("1").adult(), "1, 2 ve 5 yetişkin içerik değildir");
        assertTrue(found.get("2").adult());
        assertTrue(found.get("3").adult());
        assertEquals(java.util.List.of(0, 0, 4, 1), java.util.stream.Stream.of("1", "2", "3", "4").map(id -> found.get(id).storeType()).toList());
        assertFalse(found.get("5").adult());
        assertNull(found.get("5").storeType(), "tür bildirilmediyse null");
    }

    @Test void storeTypesCarryTypeVisibilityReleaseDayParentTagsCategoriesAndPlatformsOnlyForRecognisedApps() {
        server.expect(method(HttpMethod.GET)).andExpect(request -> {
            assertEquals("/IStoreBrowseService/GetItems/v1/", request.getURI().getPath());
            assertEquals(TEST_KEY, request.getHeaders().getFirst("x-webapi-key"));
            String query = java.net.URLDecoder.decode(request.getURI().getRawQuery(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(query.contains("\"include_release\":true") && query.contains("\"include_tag_count\":20") && query.contains("\"include_platforms\":true"));
            assertFalse(request.getURI().toString().contains(TEST_KEY));
        }).andRespond(withSuccess("""
                {"response":{"store_items":[
                 {"appid":10,"success":1,"type":0,"visible":true,"tagids":[492,21,0,-5],"release":{"steam_release_date":1373389200},
                  "categories":{"supported_player_categoryids":[2,1]},"platforms":{"windows":true,"mac":false,"steamos_linux":true}},
                 {"appid":20,"success":1,"type":4,"visible":true,"related_items":{"parent_appid":10},"release":{"steam_release_date":4102444700,"is_coming_soon":true}},
                 {"appid":30,"success":1,"type":0,"visible":false,"release":{"steam_release_date":100}},
                 {"appid":40,"success":15},
                 {"appid":50,"success":1},
                 {"appid":999,"success":1,"type":0}]}}
                """, MediaType.APPLICATION_JSON));
        var found = provider.storeTypes(java.util.List.of("10", "20", "30", "40", "50"));
        assertEquals(java.util.Set.of("10", "20", "30"), found.keySet(), "tanınmayan (40), türsüz (50) ve istenmeyen (999) kayıtlar yok");
        var game = found.get("10");
        assertEquals(0, game.type());
        assertTrue(game.visible());
        assertEquals(java.time.LocalDate.parse("2013-07-09"), game.releasedOn());
        assertEquals(java.util.Set.of(492, 21), game.tagIds(), "geçersiz kimlikler atlanır");
        assertEquals(java.util.Set.of(2, 1), game.categoryIds());
        assertEquals(java.util.Set.of("windows", "linux"), game.platforms(), "steamos_linux → linux");
        var dlc = found.get("20");
        assertEquals(4, dlc.type());
        assertEquals("10", dlc.parentAppId());
        assertNull(dlc.releasedOn(), "yakında olan oyunun tarihi yazılmaz");
        assertFalse(found.get("30").visible());
        assertNull(found.get("30").releasedOn(), "makul aralık dışındaki tarih yazılmaz");
        server.verify();
    }

    @Test void storeTagNamesAreReadFromTheTagListAndRejectBrokenResponses() {
        var clock = new Clock() {
            Instant now = Instant.parse("2026-10-20T00:00:00Z");
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now; }
        };
        RestClient.Builder builder = RestClient.builder();
        var tagServer = MockRestServiceServer.bindTo(builder).build();
        var tagProvider = new SteamGameProvider(builder.build(), TEST_KEY, true, new SteamRequestBudget(clock), millis -> clock.now = clock.now.plusMillis(millis));
        tagServer.expect(method(HttpMethod.GET)).andExpect(request -> {
            assertEquals("/IStoreService/GetTagList/v1/", request.getURI().getPath());
            assertEquals(TEST_KEY, request.getHeaders().getFirst("x-webapi-key"));
            assertFalse(request.getURI().toString().contains(TEST_KEY));
        }).andRespond(withSuccess("""
                {"response":{"version_hash":"1","tags":[{"tagid":9,"name":"Strategy"},{"tagid":0,"name":"X"},{"tagid":21,"name":"  "},{"tagid":19,"name":"Action"}]}}
                """, MediaType.APPLICATION_JSON));
        tagServer.expect(method(HttpMethod.GET)).andRespond(withSuccess("{\"response\":{}}", MediaType.APPLICATION_JSON));
        tagServer.expect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertEquals(java.util.Map.of(9, "Strategy", 19, "Action"), tagProvider.storeTagNames());
        assertEquals("INVALID_PROVIDER_RESPONSE", assertThrows(ProviderException.class, tagProvider::storeTagNames).code());
        assertEquals("RATE_LIMITED", assertThrows(ProviderException.class, tagProvider::storeTagNames).code());
        assertEquals(java.util.Map.of(), provider.storeTypes(java.util.List.of()));
        assertEquals("INVALID_PROVIDER_REQUEST", assertThrows(ProviderException.class, () -> provider.storeTypes(java.util.List.of("x"))).code());
        tagServer.verify();
    }

    @Test void releaseStatusRejectsInvalidIdsAndPricesRejectInvalidCountriesAndIds() {
        assertEquals(java.util.Map.of(), provider.releaseStatus(java.util.List.of()));
        assertEquals("INVALID_PROVIDER_REQUEST", assertThrows(ProviderException.class, () -> provider.releaseStatus(java.util.List.of("0; drop"))).code());
        assertEquals(java.util.Map.of(), provider.storePrices(java.util.List.of(), "US"));
        var validId = java.util.List.of("1");
        var badId = java.util.List.of("0; drop");
        assertEquals("INVALID_PROVIDER_REQUEST", assertThrows(ProviderException.class, () -> provider.storePrices(validId, "us")).code());
        assertEquals("INVALID_PROVIDER_REQUEST", assertThrows(ProviderException.class, () -> provider.storePrices(validId, "US\",\"x")).code());
        assertEquals("INVALID_PROVIDER_REQUEST", assertThrows(ProviderException.class, () -> provider.storePrices(badId, "US")).code());
    }

    @Test void storePricesPickTheLowestNonBundleOptionPerCountryAndKeepSaleAndScaleExactly() {
        server.expect(method(HttpMethod.GET)).andExpect(request -> {
            assertEquals("/IStoreBrowseService/GetItems/v1/", request.getURI().getPath());
            String query = java.net.URLDecoder.decode(request.getURI().getRawQuery(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(query.contains("\"country_code\":\"JP\"") && query.contains("\"include_all_purchase_options\":true"));
            assertFalse(request.getURI().toString().contains(TEST_KEY));
        }).andRespond(withSuccess("""
                {"response":{"store_items":[
                 {"appid":1245620,"purchase_options":[
                   {"packageid":1010505,"final_price_in_cents":"924000"},
                   {"packageid":1010509,"final_price_in_cents":"902000"},
                   {"bundleid":32470,"final_price_in_cents":"100"},
                   {"packageid":1010506,"final_price_in_cents":"990000","must_purchase_as_set":true}]},
                 {"appid":4303420,"purchase_options":[
                   {"packageid":1504951,"final_price_in_cents":"9400","original_price_in_cents":"79000","discount_pct":88,
                    "active_discounts":[{"discount_amount":"69600","discount_end_date":1794675600},{"discount_end_date":1900000000}]}]},
                 {"appid":730,"is_free":true,"purchase_options":[]},
                 {"appid":4087790,"release":{"is_coming_soon":true}},
                 {"appid":42,"success":15},
                 {"appid":99,"purchase_options":[{"packageid":5,"final_price_in_cents":"oops"},{"packageid":6,"final_price_in_cents":"12345678901"}]},
                 {"appid":777,"purchase_options":[{"packageid":7,"final_price_in_cents":"100"}]}]}}
                """, MediaType.APPLICATION_JSON));
        var found = provider.storePrices(java.util.List.of("1245620", "4303420", "730", "4087790", "42", "99"), "JP");
        assertEquals(java.util.Set.of("1245620", "4303420"), found.keySet(), "ücretsiz, çıkmamış, tanınmayan, geçersiz tutarlı ve istenmeyen kimlik sonuçta yok");
        var elden = found.get("1245620");
        assertEquals(902000L, elden.finalCents(), "paket olmayan ve set olarak satılmayan seçenekler içinde en düşük");
        assertEquals(902000L, elden.originalCents(), "indirim yoksa indirimsiz fiyat güncel fiyata eşit");
        assertEquals(0, elden.discountPercent());
        assertNull(elden.saleEnd());
        var sale = found.get("4303420");
        assertEquals(9400L, sale.finalCents());
        assertEquals(79000L, sale.originalCents());
        assertEquals(88, sale.discountPercent());
        assertEquals(Instant.ofEpochSecond(1794675600L), sale.saleEnd(), "en yakın indirim bitişi");
        server.verify();
    }

    @Test void storePriceFallsBackToTheBestOptionWhenNoOptionListIsPresentAndComputesMissingPercent() {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess("""
                {"response":{"store_items":[
                 {"appid":10,"best_purchase_option":{"packageid":1,"final_price_in_cents":500,"original_price_in_cents":"1000"}},
                 {"appid":11,"best_purchase_option":{"bundleid":2,"final_price_in_cents":"100"}}]}}
                """, MediaType.APPLICATION_JSON));
        var found = provider.storePrices(java.util.List.of("10", "11"), "US");
        assertEquals(java.util.Set.of("10"), found.keySet(), "paket (bundle) seçeneği tek başına fiyat sayılmaz");
        assertEquals(500L, found.get("10").finalCents());
        assertEquals(1000L, found.get("10").originalCents());
        assertEquals(50, found.get("10").discountPercent(), "discount_pct yoksa fiyatlardan hesaplanır");
    }

    @Test void storeItemsRejectInvalidIdsOversizedBatchesAndMalformedResponses() {
        assertEquals(java.util.Map.of(), provider.storeItems(java.util.List.of()));
        assertEquals("INVALID_PROVIDER_REQUEST", assertThrows(ProviderException.class, () -> provider.storeItems(java.util.List.of("0; drop"))).code());
        var many = new java.util.ArrayList<String>();
        for (int index = 1; index <= 101; index++) many.add(Integer.toString(index));
        assertEquals("INVALID_PROVIDER_REQUEST", assertThrows(ProviderException.class, () -> provider.storeItems(many)).code());
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess("{\"response\":{}}", MediaType.APPLICATION_JSON));
        assertEquals("INVALID_PROVIDER_RESPONSE", assertThrows(ProviderException.class, () -> provider.storeItems(java.util.List.of("1"))).code());
    }

    @Test void shortDescriptionsAskTheRequestedLanguageOnlyAndParseTheTextsAndRejectUnsupportedLanguages() {
        server.expect(method(HttpMethod.GET)).andExpect(request -> {
            String query = java.net.URLDecoder.decode(request.getURI().getRawQuery(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(query.contains("\"language\":\"turkish\"") && query.contains("\"include_basic_info\":true") && query.contains("{\"appid\":570}"));
            assertFalse(query.contains("include_assets") || query.contains("include_supported_languages"), "yalnız gerekli veri istenir");
            assertEquals(TEST_KEY, request.getHeaders().getFirst("x-webapi-key"));
        }).andRespond(withSuccess("""
                {"response":{"store_items":[
                 {"appid":570,"basic_info":{"short_description":"  Her gün milyonlarca oyuncu.  "}},
                 {"appid":730,"basic_info":{"short_description":""}},
                 {"appid":731,"basic_info":{}},
                 {"appid":999,"basic_info":{"short_description":"istenmedi"}}]}}
                """, MediaType.APPLICATION_JSON));
        var found = provider.shortDescriptions(java.util.List.of("570", "730", "731"), "tr");
        assertEquals(java.util.Map.of("570", "Her gün milyonlarca oyuncu."), found, "boş, eksik ve istenmeyen kayıtlar atlanır; metin kırpılır");
        assertEquals("INVALID_PROVIDER_REQUEST", assertThrows(ProviderException.class, () -> provider.shortDescriptions(java.util.List.of("1"), "de")).code(),
                "yapılandırılmamış dil için istek atılmaz");
        assertEquals("INVALID_PROVIDER_REQUEST", assertThrows(ProviderException.class, () -> provider.shortDescriptions(java.util.List.of("0; drop"), "tr")).code());
        assertEquals(java.util.Map.of(), provider.shortDescriptions(java.util.List.of(), "tr"));
    }

    @Test void capabilitiesAreExplicitAndUnavailableFeaturesFail() {
        assertEquals("STEAM", provider.provider());
        assertTrue(provider.capabilities().contains(Capability.CATALOG));
        assertTrue(provider.capabilities().contains(Capability.INCREMENTAL_CATALOG));
        provider.require(Capability.CATALOG);
        assertEquals("UNSUPPORTED_CAPABILITY", assertThrows(ProviderException.class,
                () -> provider.require(Capability.DETAILS)).code());
        assertThrows(ProviderException.class, () -> provider.require(Capability.SEARCH));
    }
    @Test void authorizedPagedRequestUsesDocumentedInputAndNormalizesResponse() {
        server.expect(method(HttpMethod.GET)).andExpect(request -> {
            assertEquals(TEST_KEY, request.getHeaders().getFirst("x-webapi-key"));
            assertFalse(request.getURI().toString().contains(TEST_KEY));
            assertEquals("api.steampowered.com", request.getURI().getHost());
            assertEquals("/IStoreService/GetAppList/v1/", request.getURI().getPath());
            String query = java.net.URLDecoder.decode(request.getURI().getRawQuery(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(query.contains("\"last_appid\":10"));
            assertTrue(query.contains("\"max_results\":2"));
            assertTrue(query.contains("\"if_modified_since\":100"));
            // Hedef kapsam: ana oyun + DLC; yazılım, video ve donanım kapalı.
            assertTrue(query.contains("\"include_games\":true"));
            assertTrue(query.contains("\"include_dlc\":true"));
            assertTrue(query.contains("\"include_software\":false"));
            assertTrue(query.contains("\"include_videos\":false"));
            assertTrue(query.contains("\"include_hardware\":false"));
        }).andRespond(withSuccess("""
                {"response":{"apps":[{"appid":20,"name":" Example ","last_modified":200,"unknown":true,"type":"game"}],
                "last_appid":20,"have_more_results":true}}
                """, MediaType.APPLICATION_JSON));
        var result = provider.catalog(new CatalogQuery(10, Instant.ofEpochSecond(100), 2));
        assertEquals("20", result.items().getFirst().externalId());
        assertEquals("Example", result.items().getFirst().title());
        assertNull(result.items().getFirst().providerType());
        assertFalse(provider.capabilities().contains(Capability.ITEM_TYPE));
        assertEquals(Instant.ofEpochSecond(200), result.items().getFirst().lastModified());
        assertEquals(20, result.nextAppId()); assertTrue(result.hasMore()); server.verify();
    }
    @Test void missingConfigurationNeverCallsNetwork() {
        var disabled = new SteamGameProvider(RestClient.create(), "", false, new SteamRequestBudget(Clock.systemUTC()));
        var query = new CatalogQuery(0,null,10);
        assertEquals("PROVIDER_NOT_CONFIGURED", assertThrows(ProviderException.class,
                () -> disabled.catalog(query)).code()); server.verify();
    }
    @ParameterizedTest @ValueSource(strings={"120","Wed, 21 Oct 2099 00:00:00 GMT","invalid"})
    void retryAfterIsParsedWithoutLeakingProviderResponse(String retryAfter) {
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", retryAfter));
        var query = new CatalogQuery(0,null,10);
        var error = assertThrows(ProviderException.class, () -> provider.catalog(query));
        assertEquals("RATE_LIMITED",error.code());
        if (retryAfter.equals("120")) assertEquals(Duration.ofSeconds(120),error.retryAfter());
        else if (retryAfter.equals("invalid")) assertNull(error.retryAfter());
        else assertNotNull(error.retryAfter());
    }
    @ParameterizedTest @ValueSource(ints={400,401,403,429,503})
    void httpErrorsHaveSafeCodes(int status) {
        server.expect(anything()).andRespond(withStatus(HttpStatusCode.valueOf(status)));
        var query = new CatalogQuery(0,null,10);
        var exception = assertThrows(ProviderException.class, () -> provider.catalog(query));
        String expected = switch(status) {
            case 401,403 -> "PROVIDER_ACCESS_DENIED";
            case 429 -> "RATE_LIMITED";
            case 503 -> "PROVIDER_UNAVAILABLE";
            default -> "PROVIDER_REQUEST_REJECTED";
        };
        assertEquals(expected,exception.code());assertNull(exception.getCause());
        assertFalse(exception.toString().contains(TEST_KEY)); server.verify();
    }
    @Test void transportFailureDoesNotExposeUriOrCause() {
        server.expect(anything()).andRespond(withException(new IOException("secret URI="+TEST_KEY)));
        var query = new CatalogQuery(0,null,10);
        var exception = assertThrows(ProviderException.class, () -> provider.catalog(query));
        assertEquals("PROVIDER_TRANSPORT_ERROR", exception.code());assertNull(exception.getCause());
        assertFalse(exception.toString().contains(TEST_KEY));
    }
    @ParameterizedTest @ValueSource(strings={
        "{}", "{\"response\":{}}", "{\"response\":{\"apps\":[],\"have_more_results\":true}}",
        "{\"response\":{\"apps\":[{\"appid\":0,\"name\":\"A\",\"last_modified\":1}],\"have_more_results\":false}}",
        "{\"response\":{\"apps\":[],\"last_appid\":3,\"have_more_results\":false}}",
        "{\"response\":{\"apps\":[{\"appid\":1,\"name\":\"A\",\"last_modified\":1},{\"appid\":1,\"name\":\"B\",\"last_modified\":2}],\"have_more_results\":false}}"
    })
    void malformedAndNonProgressingResponsesAreRejected(String body) {
        server.expect(anything()).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        var query = new CatalogQuery(0,null,10);
        assertEquals("INVALID_PROVIDER_RESPONSE", assertThrows(ProviderException.class,
                () -> provider.catalog(query)).code());
    }
    @Test void emptyFinalPageIsValid() {
        server.expect(anything()).andRespond(withSuccess("{\"response\":{\"apps\":[],\"have_more_results\":false}}",MediaType.APPLICATION_JSON));
        var page=provider.catalog(new CatalogQuery(10,null,10));assertEquals(10,page.nextAppId());assertFalse(page.hasMore());
    }
    @Test void unsignedUpperBoundsPreserveCursorAndNormalizedItem() {
        server.expect(anything()).andRespond(withSuccess("""
                {"response":{"apps":[{"appid":4294967295,"name":" Last ","last_modified":4294967295}],
                "last_appid":4294967295,"have_more_results":false}}
                """, MediaType.APPLICATION_JSON));
        var page = provider.catalog(new CatalogQuery(4294967294L, null, 1));
        assertEquals(4294967295L, page.nextAppId());
        assertEquals("4294967295", page.items().getFirst().externalId());
        assertEquals("Last", page.items().getFirst().title());
        assertEquals(Instant.ofEpochSecond(4294967295L), page.items().getFirst().lastModified());
        assertFalse(page.hasMore());
    }
    @Test void invalidQueryIsRejectedBeforeRequest() {
        assertThrows(IllegalArgumentException.class,()->new CatalogQuery(-1,null,10));
        assertThrows(IllegalArgumentException.class,()->new CatalogQuery(0,null,1001));
        var negativeTime = Instant.ofEpochSecond(-1);
        assertThrows(IllegalArgumentException.class,()->new CatalogQuery(0,negativeTime,10));
    }
    @Test void immediateRepeatWaitsForTheSharedBudgetInsteadOfFailingAndTheDailyLimitStillFails() {
        var clock = new Clock() {
            Instant now = Instant.parse("2026-10-20T00:00:00Z");
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now; }
        };
        RestClient.Builder builder = RestClient.builder();
        var waiting = MockRestServiceServer.bindTo(builder).build();
        var waitingProvider = new SteamGameProvider(builder.build(), TEST_KEY, true, new SteamRequestBudget(clock), millis -> clock.now = clock.now.plusMillis(millis));
        waiting.expect(anything()).andRespond(withSuccess("{\"response\":{\"apps\":[],\"have_more_results\":false}}",MediaType.APPLICATION_JSON));
        waiting.expect(anything()).andRespond(withSuccess("{\"response\":{\"apps\":[],\"have_more_results\":false}}",MediaType.APPLICATION_JSON));
        var query = new CatalogQuery(0,null,10);
        waitingProvider.catalog(query);
        waitingProvider.catalog(query);
        waiting.verify();
        // Günlük sınır aşılınca bekleme çözüm değildir: uzun süreli RATE_LIMITED hemen yansır.
        var exhausted = new SteamRequestBudget(clock, 100, 4);   // arka plan payı 3 çağrı
        RestClient.Builder otherBuilder = RestClient.builder();
        var other = MockRestServiceServer.bindTo(otherBuilder).build();
        for (int index = 0; index < 3; index++) other.expect(anything()).andRespond(withSuccess("{\"response\":{\"apps\":[],\"have_more_results\":false}}",MediaType.APPLICATION_JSON));
        var limited = new SteamGameProvider(otherBuilder.build(), TEST_KEY, true, exhausted, millis -> clock.now = clock.now.plusMillis(millis));
        for (int index = 0; index < 3; index++) limited.catalog(query);
        assertEquals("RATE_LIMITED",assertThrows(ProviderException.class,()->limited.catalog(query)).code());
    }

    // ---- Kayıt düzeyi hata yalnız o kaydı atlar; sağlayıcı düzeyi hata tüm sayfayı reddeder.
    private static String app(long id, String name, String lastModified) {
        return "{\"appid\":" + id + (name == null ? "" : ",\"name\":\"" + name + "\"") + (lastModified == null ? "" : ",\"last_modified\":" + lastModified) + "}";
    }
    private static String envelope(String apps, Long last, boolean more) {
        return "{\"response\":{\"apps\":[" + apps + "]" + (last == null ? "" : ",\"last_appid\":" + last) + ",\"have_more_results\":" + more + "}}";
    }
    @Test void blankNameRecordIsSkippedAndTheCursorAdvancesPastIt() {
        server.expect(anything()).andRespond(withSuccess(envelope(
                app(396419, "Before", "10") + "," + app(396420, "", "10") + "," + app(396421, "After", "10"), 396421L, true), MediaType.APPLICATION_JSON));
        var page = provider.catalog(new CatalogQuery(396000, null, 1000));
        assertEquals(java.util.List.of("396419", "396421"), page.items().stream().map(CatalogItem::externalId).toList());
        assertEquals(1, page.skippedRecords()); assertEquals(396421L, page.nextAppId()); assertTrue(page.hasMore());
    }
    @Test void trailingInvalidRecordStillAdvancesTheCursorSoTheNextPageDoesNotRepeatIt() {
        server.expect(anything()).andRespond(withSuccess(envelope(app(5, "Ok", "1") + "," + app(6, " ", "1"), 6L, true), MediaType.APPLICATION_JSON));
        var page = provider.catalog(new CatalogQuery(0, null, 10));
        assertEquals(1, page.items().size()); assertEquals(1, page.skippedRecords()); assertEquals(6L, page.nextAppId()); assertTrue(page.hasMore());
    }
    @Test void aPageWhoseOnlyRecordIsInvalidIsNotAnError() {
        server.expect(anything()).andRespond(withSuccess(envelope(app(396420, "", "1"), 396420L, true), MediaType.APPLICATION_JSON));
        var page = provider.catalog(new CatalogQuery(396419, null, 10));
        assertTrue(page.items().isEmpty()); assertEquals(1, page.skippedRecords()); assertEquals(396420L, page.nextAppId()); assertTrue(page.hasMore());
    }
    @ParameterizedTest @ValueSource(strings = {"NAME_MISSING", "NAME_BLANK", "NAME_TOO_LONG", "MODIFIED_MISSING", "MODIFIED_NEGATIVE", "MODIFIED_TOO_BIG"})
    void eachRecordLevelDefectSkipsOnlyThatRecord(String defect) {
        String bad = switch (defect) {
            case "NAME_MISSING" -> app(2, null, "1");
            case "NAME_BLANK" -> app(2, "   ", "1");
            case "NAME_TOO_LONG" -> app(2, "x".repeat(257), "1");
            case "MODIFIED_MISSING" -> app(2, "Bad", null);
            case "MODIFIED_NEGATIVE" -> app(2, "Bad", "-1");
            default -> app(2, "Bad", "4294967296");
        };
        server.expect(anything()).andRespond(withSuccess(envelope(app(1, "Good", "1") + "," + bad + "," + app(3, "Good too", "1"), 3L, false), MediaType.APPLICATION_JSON));
        var page = provider.catalog(new CatalogQuery(0, null, 10));
        assertEquals(java.util.List.of("1", "3"), page.items().stream().map(CatalogItem::externalId).toList());
        assertEquals(1, page.skippedRecords()); assertEquals(3L, page.nextAppId());
    }
    @Test void nullRecordAndUnreadableAppIdAreSkippedAndCursorFollowsTheProviderReportedLastAppId() {
        server.expect(anything()).andRespond(withSuccess(envelope(app(5, "Ok", "1") + ",null,{\"name\":\"No id\",\"last_modified\":1},"
                + app(4294967296L, "Too big", "1"), 9L, true), MediaType.APPLICATION_JSON));
        var page = provider.catalog(new CatalogQuery(0, null, 10));
        assertEquals(1, page.items().size()); assertEquals(3, page.skippedRecords());
        assertEquals(9L, page.nextAppId(), "sayfa sonundaki okunamayan kayıtlar döngü oluşturmamalı");
    }
    @Test void nonIncreasingOrderIsStillAProviderLevelFailureEvenWhenTheOtherFieldsAreValid() {
        server.expect(anything()).andRespond(withSuccess(envelope(app(5, "A", "1") + "," + app(4, "B", "1"), 5L, false), MediaType.APPLICATION_JSON));
        var query = new CatalogQuery(0, null, 10);
        assertEquals("INVALID_PROVIDER_RESPONSE", assertThrows(ProviderException.class, () -> provider.catalog(query)).code());
    }
    @Test void aFewBadRecordsInAFullPageAreTolerated() {
        var apps = new StringBuilder();
        for (int id = 1; id <= 1000; id++) apps.append(id > 1 ? "," : "").append(app(id, id % 170 == 0 ? "" : "G" + id, "1"));
        server.expect(anything()).andRespond(withSuccess(envelope(apps.toString(), 1000L, true), MediaType.APPLICATION_JSON));
        var page = provider.catalog(new CatalogQuery(0, null, 1000));
        assertEquals(5, page.skippedRecords()); assertEquals(995, page.items().size()); assertEquals(1000L, page.nextAppId());
    }
    @Test void aMostlyInvalidPageIsAProviderFormatProblemNotARecordProblem() {
        var apps = new StringBuilder();
        for (int id = 1; id <= 20; id++) apps.append(id > 1 ? "," : "").append(app(id, id <= 10 ? "" : "G" + id, "1"));
        server.expect(anything()).andRespond(withSuccess(envelope(apps.toString(), 20L, true), MediaType.APPLICATION_JSON));
        var query = new CatalogQuery(0, null, 100);
        assertEquals("INVALID_PROVIDER_RESPONSE", assertThrows(ProviderException.class, () -> provider.catalog(query)).code());
    }

    // ---- Son sayfa / boş yanıt: Steam gerçek sözleşmesi (have_more_results ve last_appid yalnız devam varsa gelir)
    @Test void shortPageWithoutHaveMoreResultsIsTheLastPageAndKeepsTheCursorOnTheLastApp() {
        server.expect(anything()).andRespond(withSuccess("{\"response\":{\"apps\":[" + app(5, "A", "1") + "," + app(9, "B", "1") + "]}}", MediaType.APPLICATION_JSON));
        var page = provider.catalog(new CatalogQuery(0, null, 10));
        assertEquals(2, page.items().size()); assertFalse(page.hasMore()); assertEquals(9L, page.nextAppId()); assertEquals(0, page.skippedRecords());
    }
    @Test void fullPageWithoutHaveMoreResultsCannotBeFinalSoTheSyncContinues() {
        server.expect(anything()).andRespond(withSuccess("{\"response\":{\"apps\":[" + app(5, "A", "1") + "," + app(9, "B", "1") + "]}}", MediaType.APPLICATION_JSON));
        var page = provider.catalog(new CatalogQuery(0, null, 2));
        assertTrue(page.hasMore()); assertEquals(9L, page.nextAppId());
    }
    @Test void emptyResponseAfterTheCursorIsTheFinalEmptyPage() {
        server.expect(anything()).andRespond(withSuccess("{\"response\":{}}", MediaType.APPLICATION_JSON));
        var page = provider.catalog(new CatalogQuery(5563880, null, 1000));
        assertTrue(page.items().isEmpty()); assertFalse(page.hasMore()); assertEquals(5563880L, page.nextAppId());
    }
    @Test void emptyIncrementalResponseFromTheStartMeansNothingChanged() {
        server.expect(anything()).andRespond(withSuccess("{\"response\":{}}", MediaType.APPLICATION_JSON));
        var page = provider.catalog(new CatalogQuery(0, Instant.ofEpochSecond(1000), 1000));
        assertTrue(page.items().isEmpty()); assertFalse(page.hasMore()); assertEquals(0L, page.nextAppId());
    }
    @ParameterizedTest @ValueSource(strings = {"{\"response\":{}}", "{\"response\":{\"apps\":[]}}"})
    void anEmptyFirstPageOfAnInitialRunIsBrokenAndNeverCompletesTheSync(String body) {
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        var query = new CatalogQuery(0, null, 1000);
        assertEquals("INVALID_PROVIDER_RESPONSE", assertThrows(ProviderException.class, () -> provider.catalog(query)).code());
    }
    @ParameterizedTest @ValueSource(strings = {
            "{\"response\":{\"have_more_results\":false}}", "{\"response\":{\"last_appid\":5}}", "{\"response\":{\"have_more_results\":true}}",
            "{\"response\":null}", "{\"response\":[]}", "[]"})
    void aResponseWithoutAppsButWithOtherFieldsOrAnInvalidEnvelopeIsRejected(String body) {
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        var query = new CatalogQuery(10, null, 1000);
        assertEquals("INVALID_PROVIDER_RESPONSE", assertThrows(ProviderException.class, () -> provider.catalog(query)).code());
    }
    @Test void aMissingFlagNeverHidesAnInconsistentCursorOrOrder() {
        server.expect(anything()).andRespond(withSuccess("{\"response\":{\"apps\":[" + app(5, "A", "1") + "],\"last_appid\":9}}", MediaType.APPLICATION_JSON));
        var query = new CatalogQuery(0, null, 10);
        assertEquals("INVALID_PROVIDER_RESPONSE", assertThrows(ProviderException.class, () -> provider.catalog(query)).code());
    }
    @Test void aMissingFlagNeverHidesNonIncreasingIds() {
        server.expect(anything()).andRespond(withSuccess("{\"response\":{\"apps\":[" + app(9, "A", "1") + "," + app(5, "B", "1") + "]}}", MediaType.APPLICATION_JSON));
        var query = new CatalogQuery(0, null, 10);
        assertEquals("INVALID_PROVIDER_RESPONSE", assertThrows(ProviderException.class, () -> provider.catalog(query)).code());
    }
    @Test void aFinalPageWhoseOnlyRecordIsInvalidStillEndsTheRunAndCountsTheSkip() {
        server.expect(anything()).andRespond(withSuccess("{\"response\":{\"apps\":[" + app(7, " ", "1") + "]}}", MediaType.APPLICATION_JSON));
        var page = provider.catalog(new CatalogQuery(5, null, 10));
        assertTrue(page.items().isEmpty()); assertEquals(1, page.skippedRecords()); assertFalse(page.hasMore()); assertEquals(7L, page.nextAppId());
    }
    @Test void oversizedPageIsStillRejected() {
        server.expect(anything()).andRespond(withSuccess("{\"response\":{\"apps\":[" + app(1, "A", "1") + "," + app(2, "B", "1") + "]}}", MediaType.APPLICATION_JSON));
        var query = new CatalogQuery(0, null, 1);
        assertEquals("INVALID_PROVIDER_RESPONSE", assertThrows(ProviderException.class, () -> provider.catalog(query)).code());
    }
}
