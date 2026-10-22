package com.ltz.game.application.service;

import com.ltz.game.PostgresTestSupport;
import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.enums.*;
import com.ltz.game.domain.exception.ProviderException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @Import(CatalogDiscoveryPerformanceTest.Fakes.class)
class CatalogDiscoveryPerformanceTest extends PostgresTestSupport {
    @TestConfiguration static class Fakes {
        @Bean @Primary UnavailableProvider unavailableDiscoveryProvider() { return new UnavailableProvider(); }
    }
    static class UnavailableProvider implements GameProvider {
        final AtomicInteger calls=new AtomicInteger();
        @Override public String provider() { return "STEAM"; }
        @Override public Set<Capability> capabilities() { return Set.of(Capability.CATALOG); }
        @Override public CatalogPage catalog(CatalogQuery query) { calls.incrementAndGet(); throw new ProviderException("PROVIDER_UNAVAILABLE"); }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired GameDiscoveryService discovery;
    @Autowired UnavailableProvider provider;

    @Test void largeIndexAndConcurrentReadsStayLocalAndProduceMeasuredP95() throws Exception {
        jdbc.update("delete from game_external_refs"); jdbc.update("delete from games");
        jdbc.execute("""
            insert into games(id,title,normalized_title,created_at,updated_at,origin,game_type,status,metadata_completeness,release_date,early_access)
            select gen_random_uuid(),'Benchmark '||lpad(n::text,6,'0'),'benchmark '||lpad(n::text,6,'0'),now(),now(),
            case when n%2=0 then 'INTERNAL' else 'EXTERNAL' end,
            case when n%10=0 then 'DLC' when n%10=1 then 'UNKNOWN' else 'MAIN_GAME' end,
            case when n%23=0 then 'ARCHIVED' else 'ACTIVE' end,'INDEX_ONLY',date '2000-01-01'+n%9000,n%2=0
            from generate_series(1,100000) n
            """);
        jdbc.execute("insert into game_external_refs(id,game_id,provider,external_id) select gen_random_uuid(),id,'STEAM',(10000000+substring(normalized_title from 11)::integer)::text from games where origin='EXTERNAL'");
        jdbc.execute("insert into catalog_terms(id,kind,code) values(gen_random_uuid(),'GENRE','benchmark-action'),(gen_random_uuid(),'PLATFORM','benchmark-pc') on conflict(kind,code) do nothing");
        jdbc.execute("insert into game_terms(game_id,term_id) select g.id,t.id from games g cross join catalog_terms t where t.code in ('benchmark-action','benchmark-pc')");
        jdbc.execute("insert into game_languages(game_id,language_code) select id,'en' from games");
        jdbc.execute("analyze games"); jdbc.execute("analyze game_terms"); jdbc.execute("analyze game_languages");
        assertEquals(100000L,jdbc.queryForObject("select count(*) from games",Long.class));
        var listing=new DiscoveryQuery(0,20);
        var search=new DiscoveryQuery(0,20,"Benchmark 00");
        var filtered=new DiscoveryQuery(0,20,"Benchmark 00","benchmark-action","benchmark-pc","en",false,
                LocalDate.of(2000,1,1),LocalDate.of(2030,1,1),false,null,CatalogSort.RELEASE_DATE,CatalogDirection.DESC);
        Map<String,Object> report=new LinkedHashMap<>();
        report.put("records",100000); report.put("database","PostgreSQL 17 Testcontainers");
        report.put("java",Runtime.version().toString()); report.put("os",System.getProperty("os.name"));
        report.put("poolMax",3); report.put("targetP95Ms",300);
        report.put("listingP95Ms",measure(listing,40)); report.put("searchP95Ms",measure(search,40));
        report.put("combinedFiltersP95Ms",measure(filtered,40));
        List<Double> concurrent=new ArrayList<>();
        try(var executor=Executors.newFixedThreadPool(4)) {
            List<Future<List<Double>>> futures=new ArrayList<>();
            for(int i=0;i<4;i++) futures.add(executor.submit(()->samples(search,25)));
            for(var future:futures) concurrent.addAll(future.get(120,TimeUnit.SECONDS));
        }
        report.put("concurrentWorkers",4); report.put("concurrentSamples",100); report.put("concurrentSearchP95Ms",p95(concurrent));
        assertEquals(0,provider.calls.get());
        // Sonuçlar arasındaki kapsam ve sıra kontrolü; performans hedefi gerçek ölçümle raporlanır.
        assertTrue(discovery.list(filtered).totalElements()>0);
        Set<UUID> ids=new HashSet<>();
        for(int page=0;page<5;page++) {
            var result=discovery.list(new DiscoveryQuery(page,20,"Benchmark 00"));
            for(var item:result.items()) assertTrue(ids.add(item.game().gameId()));
        }
        report.put("targetMet",List.of("listingP95Ms","searchP95Ms","combinedFiltersP95Ms","concurrentSearchP95Ms")
                .stream().allMatch(key->((Number)report.get(key)).doubleValue()<=300));
        Files.writeString(Path.of("target","catalog-discovery-performance.json"),tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(report));
    }
    private double measure(DiscoveryQuery query,int count) {
        for(int i=0;i<5;i++) discovery.list(query);
        return p95(samples(query,count));
    }
    private List<Double> samples(DiscoveryQuery query,int count) {
        List<Double> values=new ArrayList<>();
        for(int i=0;i<count;i++) {
            long started=System.nanoTime();
            var result=discovery.list(query);
            values.add((System.nanoTime()-started)/1_000_000.0);
            assertFalse(result.items().isEmpty());
        }
        return values;
    }
    private double p95(List<Double> samples) {
        List<Double> sorted=samples.stream().sorted().toList();
        return sorted.get((int)Math.ceil(sorted.size()*0.95)-1);
    }
}
