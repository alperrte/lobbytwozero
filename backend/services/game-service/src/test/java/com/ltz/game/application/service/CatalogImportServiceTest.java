package com.ltz.game.application.service;

import com.ltz.game.PostgresTestSupport;
import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.enums.CatalogRunMode;
import com.ltz.game.domain.enums.CatalogSyncStatus;
import com.ltz.game.domain.enums.GameOrigin;
import com.ltz.game.domain.enums.GameType;
import com.ltz.game.domain.enums.MetadataCompleteness;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.persistence.repository.GameExternalReferenceRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest @Import(CatalogImportServiceTest.Fakes.class)
class CatalogImportServiceTest extends PostgresTestSupport {
    @TestConfiguration static class Fakes { @Bean @Primary FakeProvider fakeProvider() { return new FakeProvider(); } }
    static class FakeProvider implements GameProvider {
        boolean reliableTypes;
        final Queue<Supplier<CatalogPage>> pages=new ConcurrentLinkedQueue<>();
        final List<CatalogQuery> queries=new CopyOnWriteArrayList<>();
        @Override public String provider() { return "STEAM"; }
        @Override public Set<Capability> capabilities() { return reliableTypes ? Set.of(Capability.CATALOG,Capability.INCREMENTAL_CATALOG,Capability.ITEM_TYPE) : Set.of(Capability.CATALOG,Capability.INCREMENTAL_CATALOG); }
        @Override public CatalogPage catalog(CatalogQuery query) { queries.add(query);return Objects.requireNonNull(pages.poll()).get(); }
        void page(long id,String title,boolean more) { pages.add(()->new CatalogPage(List.of(new CatalogItem(Long.toString(id),title,Instant.ofEpochSecond(100))),id,more)); }
    }
    @Autowired FakeProvider provider;
    @Autowired CatalogImportService imports;
    @Autowired CatalogSearchService search;
    @Autowired GameRepository games;
    @Autowired GameExternalReferenceRepository refs;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void reset() {
        provider.pages.clear();provider.queries.clear();provider.reliableTypes=false;
        jdbc.update("delete from game_external_refs");jdbc.update("delete from games");
        jdbc.update("update game_provider_sync_state set cursor=0, sync_status='IDLE', run_mode=null, query_since=null, run_started_at=null, last_successful_at=null, processed_count=0, last_error_code=null");
    }
    @Test void itemTypesRequireAdvertisedProviderCapability() {
        imports.startInitial();
        provider.pages.add(()->new GameProvider.CatalogPage(List.of(new GameProvider.CatalogItem("10","Typed",Instant.ofEpochSecond(100),"game")),10,false));
        imports.importNextPage(1);
        UUID id=refs.findByProviderAndExternalId("STEAM","10").orElseThrow().getGame().getId();
        assertEquals(GameType.UNKNOWN,games.findById(id).orElseThrow().getGameType());
        assertEquals(0,search.search("Typed",0,20).totalElements());
        assertEquals(1,search.search("Typed",0,20,true).totalElements());
        provider.reliableTypes=true;imports.startIncremental();
        provider.pages.add(()->new GameProvider.CatalogPage(List.of(new GameProvider.CatalogItem("10","Typed",Instant.ofEpochSecond(100),"game")),10,false));
        imports.importNextPage(1);
        assertEquals(GameType.MAIN_GAME,games.findById(id).orElseThrow().getGameType());
        assertEquals(1,search.search("Typed",0,20).totalElements());
    }
    @Test void initialImportIsPagedAndIdempotentWithCanonicalIds() {
        imports.startInitial();provider.page(10,"Same name",true);provider.page(20,"Same name",false);
        assertEquals(CatalogSyncStatus.RUNNING,imports.importNextPage(1).syncStatus());
        var complete=imports.importNextPage(1);assertEquals(CatalogSyncStatus.COMPLETE,complete.syncStatus());
        assertEquals(2,complete.indexedEntries());assertEquals(2,complete.processedCount());assertNull(complete.providerTotal());
        assertEquals(10,provider.queries.get(1).afterAppId());
        UUID firstId=refs.findByProviderAndExternalId("STEAM","10").orElseThrow().getGame().getId();
        imports.startInitial();provider.page(10,"Renamed",true);provider.page(20,"Same name",false);
        imports.importNextPage(1);imports.importNextPage(1);
        assertEquals(2,games.count());assertEquals(2,refs.count());
        assertEquals(firstId,refs.findByProviderAndExternalId("STEAM","10").orElseThrow().getGame().getId());
        assertEquals(GameOrigin.EXTERNAL,games.findById(firstId).orElseThrow().getOrigin());
        assertEquals("Renamed",games.findById(firstId).orElseThrow().getTitle());
        assertEquals(4,provider.queries.size());imports.importNextPage(1);assertEquals(4,provider.queries.size());
    }
    @Test void incrementalUsesSuccessfulStartWatermarkAndDoesNotEraseMetadata() {
        assertThrows(IllegalStateException.class,imports::startIncremental);
        imports.startInitial();provider.page(10,"Alpha",false);var initial=imports.importNextPage(1);
        UUID id=refs.findByProviderAndExternalId("STEAM","10").orElseThrow().getGame().getId();
        jdbc.update("update games set metadata_completeness='PARTIAL' where id=?",id);
        imports.startIncremental();provider.page(10,"Alpha updated",false);imports.importNextPage(1);
        assertEquals(initial.lastSuccessfulAt().minusSeconds(1),provider.queries.get(1).modifiedSince());
        assertEquals(CatalogRunMode.INCREMENTAL,imports.status().runMode());
        assertEquals(MetadataCompleteness.PARTIAL,games.findById(id).orElseThrow().getMetadataCompleteness());
        assertEquals(1,games.count());
    }
    @Test void providerFailureKeepsCommittedPagesAndCheckpoint() {
        imports.startInitial();provider.page(10,"Alpha",true);imports.importNextPage(1);
        provider.pages.add(()->{throw new ProviderException("PROVIDER_UNAVAILABLE");});
        var failed=imports.importNextPage(1);assertEquals(CatalogSyncStatus.FAILED,failed.syncStatus());
        assertEquals(10,failed.cursor());assertEquals(1,failed.indexedEntries());assertNull(failed.lastSuccessfulAt());
        assertEquals("PROVIDER_UNAVAILABLE",failed.lastErrorCode());assertEquals(1,search.search("Al",0,20,true).totalElements());
    }
    @Test void concurrentPagesSerializeThroughPersistentState() throws Exception {
        imports.startInitial();assertThrows(IllegalStateException.class,imports::startInitial);
        provider.page(10,"Alpha",true);provider.page(20,"Beta",false);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var first=executor.submit(()->imports.importNextPage(1));var second=executor.submit(()->imports.importNextPage(1));
            first.get(10,TimeUnit.SECONDS);second.get(10,TimeUnit.SECONDS);
        }
        assertEquals(CatalogSyncStatus.COMPLETE,imports.status().syncStatus());assertEquals(2,refs.count());
        assertEquals(List.of(0L,10L),provider.queries.stream().map(GameProvider.CatalogQuery::afterAppId).toList());
    }
    @Test void localPrefixSearchIsStableEscapesWildcardsAndWorksWithoutProvider() {
        imports.startInitial();provider.page(10,"Alpha",true);provider.page(20,"Alpha",false);
        imports.importNextPage(1);imports.importNextPage(1);
        var first=search.search("AL",0,1,true);var second=search.search("AL",1,1,true);
        assertEquals(2,first.totalElements());assertNotEquals(first.items().getFirst().gameId(),second.items().getFirst().gameId());
        assertEquals(0,search.search("%",0,20,true).totalElements());
        assertThrows(IllegalArgumentException.class,()->search.search(" ",0,20,true));
        assertThrows(IllegalArgumentException.class,()->search.search("A",0,101,true));
        assertThrows(IllegalArgumentException.class,()->search.search("A",-1,20,true));
        assertEquals("a b",CatalogImportService.normalize("  Ａ   B  "));
    }
}
