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
        return Set.copyOf(all);
    }

    @Override public CatalogPage catalog(CatalogQuery query) { return catalog.catalog(query); }

    @Override public Details details(String externalId) { return detail.details(externalId); }
}
