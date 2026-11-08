package com.ltz.game.application.service;

import com.ltz.game.application.port.GamePriceProvider;
import com.ltz.game.application.port.GamePriceProvider.Market;
import com.ltz.game.application.port.GamePriceProvider.Quote;
import com.ltz.game.api.dto.response.GamePricesResponse;
import com.ltz.game.api.dto.response.GamePricesResponse.Item;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.config.GamePriceProperties;
import com.ltz.game.infrastructure.persistence.repository.GamePriceRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class GamePriceService {
    private final GamePriceProvider provider;
    private final GamePriceRepository repository;
    private final GameRepository games;
    private final GamePriceProperties properties;
    private final Clock clock;

    /** Salt yerel okuma: kullanıcı isteği üçüncü taraf ağını veya fiyat yenilemesini tetiklemez. */
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public GamePricesResponse read(List<UUID> ids,Market market) {
        if(ids==null || ids.size()>50 || ids.stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("En fazla 50 geçerli oyun kimliği.");
        Set<UUID> active=games.findAllById(ids).stream().filter(com.ltz.game.domain.entity.Game::isActive).map(com.ltz.game.domain.entity.Game::getId).collect(java.util.stream.Collectors.toSet());
        var byGame=repository.quotes(active,market).stream().collect(java.util.stream.Collectors.groupingBy(Quote::gameId));
        Set<UUID> noOffers=repository.noOffers(active,market,clock.instant(),properties.coldTtl());
        return new GamePricesResponse(market,ids.stream().distinct().map(id -> {
            if(!active.contains(id)) return new Item(id,"UNAVAILABLE",List.of());
            List<Quote> quotes=byGame.getOrDefault(id,List.of());
            List<Quote> fresh=quotes.stream().filter(quote -> quote.isFreshAt(clock.instant())).toList();
            if(!quotes.isEmpty()) return new Item(id,fresh.isEmpty()?"STALE":"AVAILABLE",fresh.isEmpty()?quotes:fresh);
            return new Item(id,noOffers.contains(id)?"NO_OFFER":"UNAVAILABLE",List.of());
        }).toList());
    }
    /** Her yenileme turunda yapılandırılmış her pazar sırayla yenilenir; bir pazarın hatası diğerlerini engellemez. */
    public void refresh() {
        if(!provider.isConfigured()) return;
        for(Market market:properties.allMarkets()) refresh(market);
    }
    /**
     * Tur başına bir pazar için en çok bu kadar parti (parti = 200 oyun, bir ITAD isteği çifti). Aday kalmadıkça veya parti dolmadıkça
     * sürer; büyük ilk birikim saatlerde erir, kararlı durumda tek (kısmi) parti yeter. Kiralama (120 sn) bütün partileri kapsar.
     */
    static final int MAX_BATCHES_PER_RUN = 5;
    private static final int BATCH_SIZE = 200;

    private void refresh(Market market) {
        UUID owner=UUID.randomUUID();
        if(!repository.claim(owner,clock.instant())) return;
        Set<UUID> ids=Set.of();
        try {
            boolean more=true;
            for(int batch=0;more && batch<MAX_BATCHES_PER_RUN;batch++) {
                var candidates=repository.candidates(market,clock.instant(),properties.ttl(),properties.warmTtl(),properties.coldTtl());
                ids=candidates.stream().map(GamePriceRepository.Candidate::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
                more=!ids.isEmpty() && refreshBatch(owner,market,candidates,ids);
            }
        } catch(ProviderException error) {
            repository.failed(owner,ids,market,clock.instant(),error.code(),error.retryAfter());
        } finally { repository.release(owner); }
    }

    /** Bir toplu yenileme; sıradaki grubu da çekmek gerekiyorsa true döner. */
    private boolean refreshBatch(UUID owner,Market market,java.util.List<GamePriceRepository.Candidate> candidates,Set<UUID> ids) {
        Instant now=clock.instant();
        java.util.Map<UUID,java.time.Duration> tier=candidates.stream().collect(java.util.stream.Collectors.toMap(GamePriceRepository.Candidate::id,GamePriceRepository.Candidate::ttl));
        // Sağlayıcı en uzun tazeliği verir; her oyunun teklifi kendi kademesinin süresine kısaltılır (bir sonraki planlı yenilemeye kadar taze).
        var quotes=provider.prices(ids,market).stream().map(quote -> quote.withExpiresAtMost(now.plus(tier.getOrDefault(quote.gameId(),properties.ttl())))).toList();
        return repository.publish(owner,ids,market,quotes,now) && candidates.size()>=BATCH_SIZE;
    }
}
