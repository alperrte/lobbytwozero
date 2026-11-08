package com.ltz.game.infrastructure.client;

import com.ltz.game.application.port.GameDetailSource;
import com.ltz.game.application.port.GameProvider;
import java.util.EnumSet;
import java.util.Set;

/**
 * Katalog sağlayıcısını (Steam) değiştirmeden, detayı ayrı bir kaynaktan (IGDB) alır. Kimlik, senkronizasyon durumu
 * ve dış referans katalog sağlayıcısındadır; yalnız detay yeteneği ve provenance kaynağı detay kaynağından gelir.
 */
public class DetailEnrichedGameProvider implements GameProvider {
    private final GameProvider catalog;
    private final GameDetailSource detail;

    public DetailEnrichedGameProvider(GameProvider catalog, GameDetailSource detail) {
        this.catalog = catalog;
        this.detail = detail;
    }

    @Override public String provider() { return catalog.provider(); }

    @Override public String detailSource() { return detail.source(); }

    @Override public Set<Capability> capabilities() {
        Set<Capability> all = EnumSet.noneOf(Capability.class);
        all.addAll(catalog.capabilities());
        all.add(Capability.DETAILS);
        // Tür kanıtı detay kaynağından gelir (ör. IGDB game_type); kaynak türü bildirmiyorsa sınıflandırma UNKNOWN kalır.
        if (detail.reportsItemType()) all.add(Capability.ITEM_TYPE);
        return Set.copyOf(all);
    }

    @Override public CatalogPage catalog(CatalogQuery query) { return catalog.catalog(query); }

    @Override public Details details(String externalId) { return detail.details(externalId); }

    @Override public java.util.Map<String, ItemEvidence> itemEvidence(java.util.Collection<String> externalIds) { return detail.itemEvidence(externalIds); }

    @Override public java.util.Map<String, RelationEvidence> relationEvidence(java.util.Collection<String> externalIds) { return detail.relationEvidence(externalIds); }

    @Override public java.util.Map<String, Details> bulkDetails(java.util.Collection<String> externalIds) { return detail.bulkDetails(externalIds); }

    @Override public java.util.Map<String, StoreItem> storeItems(java.util.Collection<String> externalIds) { return catalog.storeItems(externalIds); }

    @Override public java.util.Map<String, String> shortDescriptions(java.util.Collection<String> externalIds, String language) { return catalog.shortDescriptions(externalIds, language); }

    @Override public Set<String> catalogDlcIds() { return catalog.catalogDlcIds(); }
}
