package com.ltz.game.application.service;

import com.ltz.game.application.port.GamePriceProvider;
import com.ltz.game.application.port.GamePriceProvider.Market;
import com.ltz.game.application.port.GamePriceProvider.Quote;
import com.ltz.game.application.port.GameProvider;
import com.ltz.game.application.port.ProviderPriority;
import com.ltz.game.api.dto.response.GamePricesResponse;
import com.ltz.game.api.dto.response.GamePricesResponse.Item;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.config.GamePriceProperties;
import com.ltz.game.infrastructure.config.GameRefreshProperties;
import com.ltz.game.infrastructure.persistence.repository.GameExternalReferenceRepository;
import com.ltz.game.infrastructure.persistence.repository.GamePriceRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pazar bazlı fiyatlar. Okuma salt yereldir. Yenileme iki kaynağı birleştirir: ITAD (tüm mağazalar) ve Steam'in kendi mağaza fiyatı
 * ("Steam" satırı). Steam satırı sıcak oyunlarda (yapılandırılmışsa), ITAD'in Steam teklifi vermediği oyunlarda ve sayfası açılan oyunlarda
 * doğrudan Steam'den alınır ve ITAD'in Steam satırını ezer; Steam çağrısı başarısız olursa ITAD'in satırı kalır.
 */
@Service
public class GamePriceService {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(GamePriceService.class);
    static final String STEAM_STORE = "Steam";
    private static final String STEAM_SOURCE = "STEAM";
    private static final String STEAM_PROVIDER = "STEAM";
    private static final String STEAM_APP_URL = "https://store.steampowered.com/app/";
    private static final int STEAM_BATCH = 100;

    private final GamePriceProvider provider;
    private final GamePriceRepository repository;
    private final GameRepository games;
    private final GamePriceProperties properties;
    private final Clock clock;
    private final GameProvider steam;
    private final GameExternalReferenceRepository references;
    private final GameRefreshProperties refresh;
    private final RefreshHints hints;

    /** Yalnız ITAD (Steam fiyatı ve ipucu kapalı): eski kurucu. */
    public GamePriceService(GamePriceProvider provider, GamePriceRepository repository, GameRepository games, GamePriceProperties properties, Clock clock) {
        this(provider, repository, games, properties, clock, null, null, GameRefreshProperties.defaults(), null);
    }

    @Autowired
    public GamePriceService(GamePriceProvider provider, GamePriceRepository repository, GameRepository games, GamePriceProperties properties, Clock clock,
            GameProvider steam, GameExternalReferenceRepository references, GameRefreshProperties refresh, RefreshHints hints) {
        this.provider = provider;
        this.repository = repository;
        this.games = games;
        this.properties = properties;
        this.clock = clock;
        this.steam = steam;
        this.references = references;
        this.refresh = refresh;
        this.hints = hints;
    }

    /** Salt yerel okuma: kullanıcı isteği üçüncü taraf ağını veya fiyat yenilemesini tetiklemez. */
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public GamePricesResponse read(List<UUID> ids,Market market) {
        if(ids==null || ids.size()>50 || ids.stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("En fazla 50 geçerli oyun kimliği.");
        Set<UUID> active=games.findAllById(ids).stream().filter(com.ltz.game.domain.entity.Game::isActive).map(com.ltz.game.domain.entity.Game::getId).collect(java.util.stream.Collectors.toSet());
        // Tek oyunun fiyatı okunuyorsa (oyun sayfası) yalnız bir bellek içi ipucu bırakılır; dış servise gidilmez, veritabanına yazılmaz.
        if(hints!=null && ids.size()==1 && properties.allMarkets().contains(market)) active.forEach(id -> hints.touch(id,market));
        var byGame=repository.quotes(active,market).stream().collect(java.util.stream.Collectors.groupingBy(Quote::gameId));
        Set<UUID> noOffers=repository.noOffers(active,market,clock.instant(),properties.displayTtl(properties.coldTtl()));
        return new GamePricesResponse(market,ids.stream().distinct().map(id -> {
            if(!active.contains(id)) return new Item(id,"UNAVAILABLE",List.of());
            List<Quote> quotes=byGame.getOrDefault(id,List.of());
            List<Quote> fresh=quotes.stream().filter(quote -> quote.isFreshAt(clock.instant())).toList();
            if(!quotes.isEmpty()) return new Item(id,fresh.isEmpty()?"STALE":"AVAILABLE",fresh.isEmpty()?quotes:fresh);
            return new Item(id,noOffers.contains(id)?"NO_OFFER":"UNAVAILABLE",List.of());
        }).toList());
    }

    private boolean steamEnabled() { return steam != null && refresh.steamPrice() != null && steam.storePricesEnabled(); }

    /** Steam'in kendi fiyatı bu pazar için doğrulanmış ve kullanılabilir mi (para birimi pazarla aynı olmalıdır). */
    private boolean steamMarket(Market market) { return steamEnabled() && refresh.steamMarkets().contains(market); }

    private boolean isHot(Duration tier) { return tier != null && tier.equals(properties.ttl()); }

    /** Pazarlar bu kadar iş parçacığında paralel yenilenir (ITAD yanıt süresi parti başına ≈1,5 sn; sıralı işlem kararlı durumdaki yükü karşılayamaz). */
    static final int PARALLEL_MARKETS = 3;
    /** {@code ItadRequestLimiter.LOCAL_THROTTLE} ile aynı kod (istemci paketi uygulama katmanına bağımlı olmasın diye burada yinelenir). */
    static final String LOCAL_THROTTLE = "PRICE_LOCAL_THROTTLE";
    /** Bir turun en çok bu kadar sürmesi hedeflenir (kiralama 120 sn); süre dolunca yeni parti başlatılmaz. */
    static final Duration RUN_BUDGET = Duration.ofSeconds(90);
    /** Çalışan planlı turun kiralama sahibi; sayfası açılan oyunun anlık yenilemesi aynı kiralamayı kullanır (aynı süreç, tek yazıcı). */
    private volatile UUID activeOwner;
    private final PriceMarketScheduler markets=new PriceMarketScheduler();

    long servedCount(Market market) { return markets.servedCount(market); }

    /** Bu turda yenilenecek pazarlar: birikimli pazar varsa yalnız o (her 5. turda hepsi), yoksa en az işlenenden başlayarak hepsi. */
    List<Market> runMarkets() { return markets.runMarkets(properties.allMarkets(),repository::inBacklog); }

    /**
     * Her yenileme turunda tek kiralama alınır ve yapılandırılmış pazarlar {@value #PARALLEL_MARKETS} iş parçacığında paralel yenilenir; bir pazarın hatası
     * diğerlerini engellemez. Kiralama tur boyunca tutulur ve tur sonunda bırakılır.
     */
    public void refresh() {
        boolean itad=provider.isConfigured();
        // ITAD anahtarı yoksa yalnız sıcak oyunların Steam fiyatı (yapılandırılmışsa) yenilenir.
        if(!itad && !(steamEnabled() && refresh.steamHotDirect())) return;
        UUID owner=UUID.randomUUID();
        if(!repository.claim(owner,clock.instant())) return;
        activeOwner=owner;
        Instant deadline=clock.instant().plus(RUN_BUDGET);
        try (java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(PARALLEL_MARKETS)) {
            List<java.util.concurrent.Future<?>> tasks=runMarkets().stream().map(market -> pool.submit(() -> refreshMarket(owner,market,itad,deadline))).collect(java.util.stream.Collectors.toList());
            for(java.util.concurrent.Future<?> task:tasks) {
                try { task.get(); }
                catch(InterruptedException _) { Thread.currentThread().interrupt(); break; }
                catch(java.util.concurrent.ExecutionException error) { LOG.warn("Fiyat pazarı yenilemesi beklenmeyen hatayla bitti: {}",error.getCause()==null ? "?" : error.getCause().getClass().getSimpleName()); }
            }
        } finally {
            activeOwner=null;
            repository.release(owner);
        }
    }
    /**
     * Tur başına bir pazar için en çok bu kadar parti (parti = 200 oyun, bir ITAD isteği çifti). Aday kalmadıkça veya parti dolmadıkça
     * sürer; büyük ilk birikim saatlerde erir, kararlı durumda tek (kısmi) parti yeter. Kiralama (120 sn) bütün partileri kapsar.
     */
    static final int MAX_BATCHES_PER_RUN = 10;
    private static final int BATCH_SIZE = 200;

    /** Bir turun nereye zaman harcadığını ölçer (yalnız log): aday sorgusu, ITAD, Steam, yazma ve parti sayısı. */
    private static final class Timing {
        long itadNanos;
        long steamNanos;
        long writeNanos;
        int batches;
        static long millis(long nanos) { return nanos/1_000_000; }
    }

    private void refreshMarket(UUID owner,Market market,boolean itad,Instant deadline) {
        // Tur süresi dolduysa pahalı aday sorgusu bile çalıştırılmaz: sıradaki pazar bir sonraki turda (rotasyonla) öne geçer.
        if(!clock.instant().isBefore(deadline)) return;
        Set<UUID> ids=Set.of();
        long started=System.nanoTime();
        int candidateCount=0;
        int done=0;
        long queryNanos=0;
        Timing timing=new Timing();
        String outcome="OK";
        try {
            // Tek sorguda birkaç parti aday alınır (sorgu üç büyük tabloyu tarar); 200'lük partiler sırayla yenilenir. Kiralama kaybında tur durur.
            long queryStarted=System.nanoTime();
            var candidates=repository.candidates(market,clock.instant(),properties.ttl(),properties.warmTtl(),properties.coldTtl(),BATCH_SIZE*MAX_BATCHES_PER_RUN);
            queryNanos=System.nanoTime()-queryStarted;
            if(!itad) candidates=candidates.stream().filter(candidate -> isHot(candidate.ttl())).toList();
            candidateCount=candidates.size();
            boolean more=true;
            for(int from=0;more && from<candidates.size() && clock.instant().isBefore(deadline);from+=BATCH_SIZE) {
                var batch=candidates.subList(from,Math.min(candidates.size(),from+BATCH_SIZE));
                ids=batch.stream().map(GamePriceRepository.Candidate::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
                more=refreshBatch(owner,market,batch,ids,itad,false,timing);
                if(more) done+=batch.size();
            }
            if(!more) outcome="LEASE_LOST";
        } catch(ProviderException error) {
            outcome=error.code();
            // Kendi ITAD hız sınırımız doldu: oyunlar başarısız sayılmaz (kayıt yazılmaz), pazar bu turda bırakılır ve sonraki turda aynı adaylardan sürer.
            if(!LOCAL_THROTTLE.equals(error.code())) repository.failed(owner,ids,market,clock.instant(),error.code(),error.retryAfter());
        } finally {
            markets.served(market,done);
            LOG.info("Fiyat pazarı turu: pazar={}-{} aday={} yenilenen={} sonuç={} süre={} ms sorgu_ms={} itad_ms={} steam_ms={} yazma_ms={} parti={}",market.country(),market.currency(),candidateCount,done,outcome,(System.nanoTime()-started)/1_000_000,
                    Timing.millis(queryNanos),Timing.millis(timing.itadNanos),Timing.millis(timing.steamNanos),Timing.millis(timing.writeNanos),timing.batches);
        }
    }

    /**
     * Sayfası açılan oyunlar için hemen yenileme (ipucu tahliyesi): verilen oyunların bu pazardaki fiyatı, oyunun kendi kademesiyle, ITAD'den ve
     * (kullanılabilirse) doğrudan Steam'den yenilenir. Kiralama başka yerdeyse veya yenilenecek oyun yoksa yenileme yapılmaz.
     *
     * @return yenilenen oyun sayısı; yapılamadıysa {@code -1}
     */
    public int refreshNow(Collection<UUID> gameIds,Market market) {
        boolean itad=provider.isConfigured();
        if(gameIds.isEmpty() || (!itad && !steamMarket(market)) || !properties.allMarkets().contains(market)) return -1;
        Map<UUID,Duration> tiers=repository.tiers(gameIds.stream().limit(BATCH_SIZE).collect(java.util.stream.Collectors.toSet()),properties.ttl(),properties.warmTtl(),properties.coldTtl());
        if(tiers.isEmpty()) return -1;
        // Planlı tur bu süreçte çalışıyorsa onun kiralaması kullanılır (tek yazıcı: aynı süreç); aksi halde kiralama alınır ve işin sonunda bırakılır.
        UUID running=activeOwner;
        UUID owner=running!=null ? running : UUID.randomUUID();
        boolean claimed=running==null;
        if(claimed && !claimWithin(owner)) return -1;
        Set<UUID> ids=tiers.keySet();
        try {
            var candidates=tiers.entrySet().stream().map(entry -> new GamePriceRepository.Candidate(entry.getKey(),entry.getValue())).toList();
            return refreshBatch(owner,market,candidates,ids,itad,true,new Timing()) ? ids.size() : -1;
        } catch(ProviderException error) {
            if(!LOCAL_THROTTLE.equals(error.code())) repository.failed(owner,ids,market,clock.instant(),error.code(),error.retryAfter());
            return -1;
        } finally { if(claimed) repository.release(owner); }
    }

    /** Planlı tur kiralamayı sürekli tutabilir (büyük birikim); sayfası açılan oyun kiralamanın turlar arasında boşa çıkmasını kısa süre bekler. */
    static final int CLAIM_ATTEMPTS = 12;
    private java.util.function.LongConsumer sleeper = GamePriceService::sleepMillis;

    /** Yalnız testler için. */
    void sleeperForTests(java.util.function.LongConsumer value) { this.sleeper = value; }

    private boolean claimWithin(UUID owner) {
        for(int attempt=0;attempt<CLAIM_ATTEMPTS;attempt++) {
            if(repository.claim(owner,clock.instant())) return true;
            if(attempt<CLAIM_ATTEMPTS-1) sleeper.accept(1000);
        }
        return false;
    }

    private static void sleepMillis(long millis) {
        try { Thread.sleep(millis); } catch(InterruptedException _) { Thread.currentThread().interrupt(); }
    }

    /** Toplu tur eski iki bağımsız değişkenli çağrıyı kullanır; yalnız sayfası açılan oyun ayrılmış kota payından yararlanır. */
    private List<Quote> itadPrices(Set<UUID> ids,Market market,boolean interactive) {
        return interactive ? provider.prices(ids,market,GamePriceProvider.Priority.INTERACTIVE) : provider.prices(ids,market);
    }

    /** Bir toplu yenileme; sonuç kiralama hâlâ bizdeyken yayımlandıysa true (kiralama kaybında hiçbir snapshot yayımlanmaz). */
    private boolean refreshBatch(UUID owner,Market market,List<GamePriceRepository.Candidate> candidates,Set<UUID> ids,boolean itad,boolean interactive,Timing timing) {
        Instant now=clock.instant();
        timing.batches++;
        Map<UUID,Duration> tier=candidates.stream().collect(java.util.stream.Collectors.toMap(GamePriceRepository.Candidate::id,GamePriceRepository.Candidate::ttl));
        long itadStarted=System.nanoTime();
        List<Quote> itadQuotes=itad ? itadPrices(ids,market,interactive) : List.of();
        timing.itadNanos+=System.nanoTime()-itadStarted;
        Set<UUID> steamIds=steamTargets(market,ids,tier,itadQuotes,itad,interactive);
        Map<UUID,Quote> steamQuotes=Map.of();
        Set<UUID> steamAsked=Set.of();
        if(!steamIds.isEmpty()) {
            long steamStarted=System.nanoTime();
            try {
                steamQuotes=steamQuotes(steamIds,market,tier,now);
                steamAsked=steamIds;
            } catch(ProviderException error) {
                // ITAD varsa Steam hatası yenilemeyi düşürmez: ITAD'in Steam satırı kalır. Yalnız Steam çalışıyorsa hata yenilemeyi başarısız sayar.
                if(!itad) throw error;
                LOG.warn("Steam fiyatı alınamadı, ITAD satırı korunuyor: pazar={}-{} kod={}",market.country(),market.currency(),error.code());
            } finally {
                timing.steamNanos+=System.nanoTime()-steamStarted;
            }
        }
        long writeStarted=System.nanoTime();
        boolean published=repository.publish(owner,ids,market,merge(market,itadQuotes,steamQuotes,steamAsked,tier,now),now);
        timing.writeNanos+=System.nanoTime()-writeStarted;
        return published;
    }

    /**
     * Steam'den doğrudan fiyatı alınacak oyunlar: sıcak oyunlar (yapılandırılmışsa), ITAD'in Steam teklifi vermediği oyunlar (boşluk yedeği; Steam'in
     * ücretsiz veya "yakında" dediği oyunlar hariç, çünkü fiyatları yoktur) ve sayfası açılan oyunlar.
     */
    private Set<UUID> steamTargets(Market market,Set<UUID> ids,Map<UUID,Duration> tier,List<Quote> itadQuotes,boolean itad,boolean forceSteam) {
        if(!steamMarket(market)) return Set.of();
        if(forceSteam) return ids;
        Set<UUID> targets=new HashSet<>();
        if(refresh.steamHotDirect()) ids.stream().filter(id -> isHot(tier.get(id))).forEach(targets::add);
        if(itad && refresh.steamGapFallback()) {
            Set<UUID> withSteamLine=new HashSet<>();
            itadQuotes.stream().filter(quote -> STEAM_STORE.equals(quote.store())).forEach(quote -> withSteamLine.add(quote.gameId()));
            Set<UUID> gaps=new HashSet<>(ids);
            gaps.removeAll(withSteamLine);
            if(!gaps.isEmpty()) gaps.removeAll(repository.withoutSteamPrice(gaps));
            targets.addAll(gaps);
        }
        return targets;
    }

    /** Steam'in kendi fiyatı: oyun başına en düşük paket dışı seçenek; fiyatı olmayan oyun için teklif üretilmez. */
    private Map<UUID,Quote> steamQuotes(Set<UUID> ids,Market market,Map<UUID,Duration> tier,Instant now) {
        Map<UUID,String> apps=new HashMap<>();
        // Birden çok Steam kimliği olan oyunda kararlı seçim: en küçük kimlik.
        Map<UUID,TreeSet<String>> byGame=new HashMap<>();
        references.findByGameIdIn(ids).stream().filter(ref -> STEAM_PROVIDER.equals(ref.getProvider()))
                .forEach(ref -> byGame.computeIfAbsent(ref.getGame().getId(),key -> new TreeSet<>()).add(ref.getExternalId()));
        byGame.forEach((game,keys) -> apps.put(game,keys.first()));
        List<String> appIds=apps.values().stream().distinct().sorted().toList();
        Map<String,GameProvider.StorePrice> found=new HashMap<>();
        // Sıcak oyunların Steam fiyatı öncelikli (arka plan kotası dolsa da alınır); diğerleri arka plandır.
        List<String> hotAppIds=apps.entrySet().stream().filter(entry -> isHot(tier.get(entry.getKey()))).map(Map.Entry::getValue).distinct().sorted().toList();
        List<String> otherAppIds=appIds.stream().filter(id -> !hotAppIds.contains(id)).toList();
        for(int from=0;from<hotAppIds.size();from+=STEAM_BATCH) {
            List<String> batch=hotAppIds.subList(from,Math.min(hotAppIds.size(),from+STEAM_BATCH));
            found.putAll(ProviderPriority.essential(() -> steam.storePrices(batch,market.country())));
        }
        for(int from=0;from<otherAppIds.size();from+=STEAM_BATCH)
            found.putAll(steam.storePrices(otherAppIds.subList(from,Math.min(otherAppIds.size(),from+STEAM_BATCH)),market.country()));
        Map<UUID,Quote> quotes=new HashMap<>();
        apps.forEach((game,appId) -> {
            GameProvider.StorePrice price=found.get(appId);
            if(price==null) return;
            Instant expires=now.plus(properties.displayTtl(tier.getOrDefault(game,properties.ttl())));
            if(price.saleEnd()!=null && price.saleEnd().isAfter(now) && price.saleEnd().isBefore(expires)) expires=price.saleEnd();
            quotes.put(game,new Quote(game,STEAM_STORE,market,BigDecimal.valueOf(price.originalCents(),2),BigDecimal.valueOf(price.finalCents(),2),
                    STEAM_SOURCE,now,expires,STEAM_APP_URL+appId,price.discountPercent(),now));
        });
        return quotes;
    }

    /**
     * ITAD teklifleri kendi kademelerinin gizleme süresine kısaltılır (yalnız kısaltır, uzatmaz). Steam'e sorulan oyunlarda ITAD'in "Steam" satırı
     * atılır: Steam'in kendi fiyatı (varsa) onun yerine geçer, Steam fiyat vermediyse o oyunda Steam satırı yoktur. Steam para birimi doğrulanmamış
     * pazarda (ör. Türkiye: Steam orada ABD doları ile satar) ITAD'in "Steam" satırı hiç gösterilmez: o satır Steam'in gerçek fiyatı değildir.
     */
    private List<Quote> merge(Market market,List<Quote> itadQuotes,Map<UUID,Quote> steamQuotes,Set<UUID> steamAsked,Map<UUID,Duration> tier,Instant now) {
        List<Quote> merged=new ArrayList<>();
        boolean steamLineUntrusted=!refresh.steamMarkets().contains(market);
        for(Quote quote:itadQuotes) {
            if(STEAM_STORE.equals(quote.store()) && (steamLineUntrusted || steamAsked.contains(quote.gameId()))) continue;
            merged.add(quote.withExpiresAtMost(now.plus(properties.displayTtl(tier.getOrDefault(quote.gameId(),properties.ttl())))));
        }
        merged.addAll(steamQuotes.values());
        return List.copyOf(merged);
    }
}
