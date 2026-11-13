package com.ltz.lfp.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.lfp.application.mapper.ListingMapper;
import com.ltz.lfp.application.port.CatalogGame;
import com.ltz.lfp.application.port.GameCatalogPort;
import com.ltz.lfp.application.port.GameCatalogUnavailableException;
import com.ltz.lfp.domain.entity.Listing;
import com.ltz.lfp.domain.entity.ListingContent;
import com.ltz.lfp.domain.exception.LfpErrorCode;
import com.ltz.lfp.domain.exception.LfpException;
import com.ltz.lfp.infrastructure.config.ListingProperties;
import com.ltz.lfp.infrastructure.persistence.repository.ListingRepository;
import com.ltz.lfp.infrastructure.persistence.repository.OwnerLock;

import lombok.RequiredArgsConstructor;

/**
 * İlan oluşturma (LTZ-UMT-LFP-01: LFP-01..09). Sıra: metin ve süre kuralları → oyunun katalogda doğrulanması
 * (game-service, transaction dışında; dış çağrı veritabanı bağlantısını tutmasın) → sahip kilidi altında açık ilan ve
 * saatlik oluşturma sınırı → kayıt. Sahip yalnız doğrulanmış token'dan gelir.
 */
@Service
@RequiredArgsConstructor
public class ListingService {

    /** Oyun olmayan katalog kayıtları için ilan açılmaz; bilinmeyen ve yeni türler engellenmez. */
    static final Set<String> NON_GAME_TYPES = Set.of("SOUNDTRACK", "VIDEO", "HARDWARE", "SOFTWARE");

    private static final Duration RATE_WINDOW = Duration.ofHours(1);

    private final ListingRepository listings;
    private final OwnerLock ownerLock;
    private final GameCatalogPort gameCatalog;
    private final ListingProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public ListingView create(UUID ownerId, CreateListingCommand command) {
        ListingContent content = ListingContent.of(command.platform(), command.skillLevel(), command.roles(),
                command.playersNeeded(), command.playTimeStart(), command.playTimeEnd(), command.micRequired(),
                command.description());
        Duration duration = duration(command.durationHours());
        CatalogGame game = playableGame(command.gameId());
        return transactions.execute(status -> {
            ownerLock.acquire(ownerId);
            Instant now = now();
            enforceLimits(ownerId, now);
            Listing listing = Listing.open(ownerId, game.gameId(), game.title(), content, now, duration,
                    properties.maxPlayWindow());
            return ListingMapper.toView(listings.saveAndFlush(listing));
        });
    }

    /** LFP-15: süre saat olarak seçilir; verilmezse varsayılan. Ayarlı aralığın dışı 400. */
    private Duration duration(Integer hours) {
        if (hours == null) {
            return properties.defaultDuration();
        }
        Duration requested = Duration.ofHours(hours);
        if (requested.compareTo(properties.minDuration()) < 0 || requested.compareTo(properties.maxDuration()) > 0) {
            throw new LfpException(LfpErrorCode.INVALID_DURATION);
        }
        return requested;
    }

    /** LFP-02: oyun katalogda yayında olmalı ve bir oyun olmalı. Katalog yanıt vermezse ilan açılmaz (503). */
    private CatalogGame playableGame(UUID gameId) {
        CatalogGame game;
        try {
            game = gameCatalog.find(gameId).orElseThrow(() -> new LfpException(LfpErrorCode.GAME_NOT_FOUND));
        } catch (GameCatalogUnavailableException exception) {
            throw new LfpException(LfpErrorCode.GAME_CATALOG_UNAVAILABLE, exception);
        }
        if (NON_GAME_TYPES.contains(game.gameType())) {
            throw new LfpException(LfpErrorCode.GAME_NOT_PLAYABLE);
        }
        return game;
    }

    /** Sahip kilidi altında çağrılır: eşzamanlı isteklerle sınır aşılamaz. */
    private void enforceLimits(UUID ownerId, Instant now) {
        if (listings.countOpenByOwner(ownerId, now) >= properties.maxOpenPerOwner()) {
            throw new LfpException(LfpErrorCode.OPEN_LISTING_LIMIT);
        }
        if (listings.countByOwnerIdAndCreatedAtGreaterThanEqual(ownerId, now.minus(RATE_WINDOW))
                >= properties.maxCreatedPerHour()) {
            throw new LfpException(LfpErrorCode.LISTING_RATE_LIMITED);
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
