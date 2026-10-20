package com.ltz.game.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Sağlayıcı yetenekleri frontend DTO'larından bağımsızdır. */
public interface GameProvider {
    enum Capability { CATALOG, INCREMENTAL_CATALOG, SEARCH, DETAILS, ITEM_TYPE }
    String provider();
    Set<Capability> capabilities();
    CatalogPage catalog(CatalogQuery query);
    default Details details(String externalId) {
        throw new com.ltz.game.domain.exception.ProviderException("UNSUPPORTED_CAPABILITY");
    }
    /** Yalnız sağlayıcının gerçekten sunduğu alanlar; null/boş veri mevcut alanları silmez. */
    record Details(String description, java.time.LocalDate releaseDate, Set<String> developers,
                   Set<String> publishers, String providerType) {
        public Details {
            if (developers == null || publishers == null || developers.stream().anyMatch(java.util.Objects::isNull)
                    || publishers.stream().anyMatch(java.util.Objects::isNull))
                throw new com.ltz.game.domain.exception.ProviderException("INVALID_PROVIDER_RESPONSE");
            developers = Set.copyOf(developers);
            publishers = Set.copyOf(publishers);
            if ((description == null && releaseDate == null && developers.isEmpty() && publishers.isEmpty() && providerType == null)
                    || (description != null && (description.isBlank() || description.length() > 16000))
                    || developers.size() > 100 || publishers.size() > 100
                    || java.util.stream.Stream.concat(developers.stream(), publishers.stream())
                    .anyMatch(name -> name.isBlank() || name.length() > 256)
                    || (providerType != null && providerType.length() > 64)) {
                throw new com.ltz.game.domain.exception.ProviderException("INVALID_PROVIDER_RESPONSE");
            }
        }
    }
    default void require(Capability capability) {
        if (!capabilities().contains(capability)) {
            throw new com.ltz.game.domain.exception.ProviderException("UNSUPPORTED_CAPABILITY");
        }
    }
    record CatalogQuery(long afterAppId, Instant modifiedSince, int size) {
        public CatalogQuery {
            if (afterAppId < 0 || afterAppId > 4294967295L || size < 1 || size > 1000
                    || (modifiedSince != null && (modifiedSince.getEpochSecond() < 0
                    || modifiedSince.getEpochSecond() > 4294967295L))) {
                throw new IllegalArgumentException("Geçersiz katalog sayfası.");
            }
        }
    }
    record CatalogItem(String externalId, String title, Instant lastModified, String providerType) {
        public CatalogItem(String externalId, String title, Instant lastModified) { this(externalId,title,lastModified,null); }
    }
    record CatalogPage(List<CatalogItem> items, long nextAppId, boolean hasMore) {
        public CatalogPage { items = List.copyOf(items); }
    }
}
