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
    /**
     * Mağaza öğesi (toplu): 16:9 başlık görseli, geliştirici, yayıncı ve dil desteği. Alanlar bağımsızdır; sağlayıcının vermediği alan boş/null kalır.
     */
    record StoreItem(String headerImageUrl, Set<String> developers, Set<String> publishers, List<LanguageSupport> languages) {
        public StoreItem {
            developers = developers == null ? Set.of() : Set.copyOf(developers);
            publishers = publishers == null ? Set.of() : Set.copyOf(publishers);
            languages = languages == null ? List.of() : List.copyOf(languages);
        }
    }
    /**
     * Toplu mağaza öğeleri: katalog kimliği → {@link StoreItem}. En çok 100 kimlik; yalnız sağlayıcının en az bir alan bildirdiği kimlikler döner.
     * Sağlayıcı yeteneği yoksa UNSUPPORTED_CAPABILITY.
     */
    default java.util.Map<String, StoreItem> storeItems(java.util.Collection<String> externalIds) {
        throw new com.ltz.game.domain.exception.ProviderException("UNSUPPORTED_CAPABILITY");
    }
    /**
     * Bir oyunun mağazadaki çıkış durumu: erken erişimde mi, ücretsiz mi, henüz çıkmamış ("yakında") mı. Sağlayıcı oyunu tanıdığında her
     * üç alan da kesindir (sağlayıcı yalnız {@code true} olanı bildirir, yokluk {@code false} demektir).
     */
    record ReleaseStatus(boolean earlyAccess, boolean free, boolean comingSoon, boolean adult, Integer storeType) {
        /** Yetişkin içerik ve mağaza türü bilinmeden (testler ve eski çağıranlar). */
        public ReleaseStatus(boolean earlyAccess, boolean free, boolean comingSoon) { this(earlyAccess, free, comingSoon, false, null); }
    }
    /**
     * Toplu çıkış durumu: katalog kimliği → {@link ReleaseStatus}. En çok 100 kimlik; yalnız sağlayıcının oyunu tanıdığı kimlikler döner
     * (kimliğin yokluğu "erken erişim/ücretsiz/yakında değil" demek değildir: çağıran bunları bilinmiyor bırakır). Sağlayıcı yeteneği
     * yoksa UNSUPPORTED_CAPABILITY.
     */
    default java.util.Map<String, ReleaseStatus> releaseStatus(java.util.Collection<String> externalIds) {
        throw new com.ltz.game.domain.exception.ProviderException("UNSUPPORTED_CAPABILITY");
    }
    /**
     * Sağlayıcının mağaza türü ve tür-türevi bilgileri (tek toplu çağrıdan). {@code type} Steam sayısal türüdür (0 oyun, 4 DLC; 1 demo, 14 vb.
     * ayrı anlam taşır), {@code visible} mağazada görünür mü, {@code releasedOn} yalnız oyun "yakında" değil ve tarih geçmişteyse çıkış günü
     * (UTC), {@code parentAppId} DLC'nin ana oyunu, {@code tagIds}/{@code categoryIds} mağaza etiket ve oyuncu kategorisi kimlikleri,
     * {@code platforms} desteklenen platform adları (küçük harf: windows, mac, linux).
     */
    record StoreType(int type, boolean visible, java.time.LocalDate releasedOn, String parentAppId, Set<Integer> tagIds, Set<Integer> categoryIds,
                     Set<String> platforms) {
        public StoreType {
            tagIds = tagIds == null ? Set.of() : Set.copyOf(tagIds);
            categoryIds = categoryIds == null ? Set.of() : Set.copyOf(categoryIds);
            platforms = platforms == null ? Set.of() : Set.copyOf(platforms);
        }
    }
    /**
     * Toplu mağaza türü: katalog kimliği → {@link StoreType}. En çok 100 kimlik; yalnız sağlayıcının tanıdığı kimlikler döner (yokluk "oyun
     * değil" demek değildir). Sağlayıcı yeteneği yoksa UNSUPPORTED_CAPABILITY.
     */
    default java.util.Map<String, StoreType> storeTypes(java.util.Collection<String> externalIds) {
        throw new com.ltz.game.domain.exception.ProviderException("UNSUPPORTED_CAPABILITY");
    }
    /** Mağaza etiket kimliği → İngilizce ad (yaklaşık yüzlerce kayıt; çağıran önbelleğe alır). Yeteneği yoksa UNSUPPORTED_CAPABILITY. */
    default java.util.Map<Integer, String> storeTagNames() {
        throw new com.ltz.game.domain.exception.ProviderException("UNSUPPORTED_CAPABILITY");
    }
    /**
     * Mağazanın kendi fiyatı (tutarlar küçük para birimi biriminde, yani yüzde biri: {@code 1499} = 14,99). {@code originalCents} indirimsiz
     * fiyattır (indirim yoksa güncel fiyata eşit), {@code saleEnd} indirimin bitişi (bilinmiyorsa null).
     */
    record StorePrice(long finalCents, long originalCents, int discountPercent, Instant saleEnd) {
        public StorePrice {
            if (finalCents < 0 || finalCents > 9_999_999_999L || originalCents < finalCents || originalCents > 9_999_999_999L
                    || discountPercent < 0 || discountPercent > 100) {
                throw new com.ltz.game.domain.exception.ProviderException("INVALID_PROVIDER_RESPONSE");
            }
        }
    }
    /**
     * Toplu mağaza fiyatı, verilen ülke için: katalog kimliği → {@link StorePrice}. En çok 100 kimlik; yalnız o ülkede satın alınabilir
     * fiyatı olan kimlikler döner (ücretsiz, çıkmamış veya o ülkede satılmayan oyun sonuçta yer almaz). Birden çok satın alma seçeneği varsa
     * paket olmayanlar içinde en düşük güncel fiyat seçilir. {@code country} iki büyük harfli ülke kodudur.
     */
    default java.util.Map<String, StorePrice> storePrices(java.util.Collection<String> externalIds, String country) {
        throw new com.ltz.game.domain.exception.ProviderException("UNSUPPORTED_CAPABILITY");
    }
    /**
     * Arka plan taramalarının sağlayıcı kotasından payı hâlâ var mı. False iken yalnız öncelikli işler ({@link ProviderPriority}: sayfası açılan ve
     * sıcak oyunlar) çağrı yapabilir; çağıran arka plan taramasını durdurmalı ya da yalnız sıcak oyunlara daraltmalıdır.
     */
    default boolean backgroundWorkAllowed() { return true; }
    /** {@link #storePrices} çağrılabilir mi (sağlayıcı etkin ve anahtarı tanımlı). Yapılandırılmamış sağlayıcı ağ isteği başlatmaz. */
    default boolean storePricesEnabled() { return false; }
    /**
     * Toplu mağaza kısa açıklaması, istenen dilde: katalog kimliği → metin. {@code language} küçük harf dil kodudur (ör. {@code en}, {@code tr});
     * desteklenmeyen kod INVALID_PROVIDER_REQUEST. Sağlayıcı o dilde metin yoksa genellikle İngilizce metne düşer: çağıran bunu İngilizce metinle
     * karşılaştırarak ayırmalıdır. En çok 100 kimlik; yalnız metin dönen kimlikler yer alır.
     */
    default java.util.Map<String, String> shortDescriptions(java.util.Collection<String> externalIds, String language) {
        throw new com.ltz.game.domain.exception.ProviderException("UNSUPPORTED_CAPABILITY");
    }
    /**
     * Toplu detay: katalog kimliği → detay (açıklama, tarih, şirketler, diller, kapak). En çok 100 kimlik; yalnız eşlenebilen ve
     * kanıtı olan kimlikler döner (yokluk ve belirsiz eşleme sonuçta yer almaz). {@link Capability#DETAILS} gerekir.
     */
    default java.util.Map<String, Details> bulkDetails(java.util.Collection<String> externalIds) {
        throw new com.ltz.game.domain.exception.ProviderException("UNSUPPORTED_CAPABILITY");
    }
    /**
     * Toplu kanıt: katalog kimliği → tür ve çıkış tarihi. Yalnız kanıt bulunan kimlikler döner; yokluk, belirsiz eşleme ve
     * bilinmeyen tür sonuçta yer almaz (çağıran bunları UNKNOWN bırakır). {@link Capability#ITEM_TYPE} gerekir.
     */
    default java.util.Map<String, ItemEvidence> itemEvidence(java.util.Collection<String> externalIds) {
        throw new com.ltz.game.domain.exception.ProviderException("UNSUPPORTED_CAPABILITY");
    }
    /**
     * Sağlayıcının kendi kataloğunda DLC olarak listelediği kimlikler (ör. Steam GetAppList yalnız DLC). Tür kanıtı olarak
     * tek başına yetmez; yalnız başka bir kaynağın tür kanıtını doğrulamak için kullanılır.
     */
    default Set<String> catalogDlcIds() {
        throw new com.ltz.game.domain.exception.ProviderException("UNSUPPORTED_CAPABILITY");
    }
    /**
     * Toplu ilişki kanıtı: katalog kimliği → sağlayıcı tür kimliği ve üst oyun durumu. Yalnız eşlenebilen kimlikler döner.
     * {@link Capability#ITEM_TYPE} gerekir.
     */
    default java.util.Map<String, RelationEvidence> relationEvidence(java.util.Collection<String> externalIds) {
        throw new com.ltz.game.domain.exception.ProviderException("UNSUPPORTED_CAPABILITY");
    }
    /** Üst oyun durumu: NONE sağlayıcıda üst oyun yok; UNMAPPED var ama katalogda karşılığı yok; AMBIGUOUS birden çok karşılık; LINKED tek karşılık. */
    enum ParentState { NONE, UNMAPPED, AMBIGUOUS, LINKED }
    /** @param providerGameType sağlayıcının sayısal tür kimliği (ör. IGDB game_type); @param parentExternalId yalnız LINKED iken katalog kimliği */
    record RelationEvidence(Integer providerGameType, ParentState parentState, String parentExternalId) {
        public RelationEvidence {
            if (parentState == null || (parentState == ParentState.LINKED) != (parentExternalId != null)) {
                throw new com.ltz.game.domain.exception.ProviderException("INVALID_PROVIDER_RESPONSE");
            }
        }
    }
    /**
     * Bir katalog kaydı için sağlayıcının tür, çıkış tarihi, tür (genre), platform, kategori (oyun modu) ve etiket (tema) kanıtı. Alanlar bağımsızdır; yalnız biri
     * dolu olabilir, hepsi boş olamaz. Tarih UTC gün olarak, doğrulanmış aralıkta olmalıdır.
     */
    record ItemEvidence(String providerType, java.time.LocalDate releaseDate, java.util.List<TermEvidence> genres, java.util.List<TermEvidence> platforms,
                        java.util.List<TermEvidence> categories, java.util.List<TermEvidence> tags) {
        public ItemEvidence(String providerType, java.time.LocalDate releaseDate) { this(providerType, releaseDate, java.util.List.of(), java.util.List.of()); }
        public ItemEvidence(String providerType, java.time.LocalDate releaseDate, java.util.List<TermEvidence> genres, java.util.List<TermEvidence> platforms) {
            this(providerType, releaseDate, genres, platforms, java.util.List.of(), java.util.List.of());
        }
        public ItemEvidence {
            genres = genres == null ? java.util.List.of() : java.util.List.copyOf(genres);
            platforms = platforms == null ? java.util.List.of() : java.util.List.copyOf(platforms);
            categories = categories == null ? java.util.List.of() : java.util.List.copyOf(categories);
            tags = tags == null ? java.util.List.of() : java.util.List.copyOf(tags);
            if (providerType == null && releaseDate == null && genres.isEmpty() && platforms.isEmpty() && categories.isEmpty() && tags.isEmpty()) {
                throw new com.ltz.game.domain.exception.ProviderException("INVALID_PROVIDER_RESPONSE");
            }
            if (providerType != null && (providerType.isBlank() || providerType.length() > 64)) {
                throw new com.ltz.game.domain.exception.ProviderException("INVALID_PROVIDER_RESPONSE");
            }
        }
    }
    /** Sağlayıcının bildirdiği tür/platform: kod (küçük harf slug, TERM_CODE kuralına uyan) ve en çok 128 karakterlik İngilizce ad. */
    record TermEvidence(String code, String name) { }
    /**
     * Detayı hangi dış kaynağın verdiği (provenance ve atıf için). Katalog sağlayıcısından farklı olabilir
     * (ör. katalog STEAM, detay IGDB); varsayılan olarak katalog sağlayıcısıdır.
     */
    default String detailSource() { return provider(); }
    /** Yalnız sağlayıcının gerçekten sunduğu alanlar; null/boş veri mevcut alanları silmez. */
    record Details(String description, java.time.LocalDate releaseDate, Set<String> developers,
                   Set<String> publishers, String providerType, List<LanguageSupport> languages, String coverUrl) {
        public Details(String description, java.time.LocalDate releaseDate, Set<String> developers,
                       Set<String> publishers, String providerType) {
            this(description, releaseDate, developers, publishers, providerType, List.of(), null);
        }
        public Details(String description, java.time.LocalDate releaseDate, Set<String> developers,
                       Set<String> publishers, String providerType, List<LanguageSupport> languages) {
            this(description, releaseDate, developers, publishers, providerType, languages, null);
        }
        public Details {
            if (developers == null || publishers == null || languages == null
                    || developers.stream().anyMatch(java.util.Objects::isNull)
                    || publishers.stream().anyMatch(java.util.Objects::isNull)
                    || languages.stream().anyMatch(java.util.Objects::isNull))
                throw new com.ltz.game.domain.exception.ProviderException("INVALID_PROVIDER_RESPONSE");
            developers = Set.copyOf(developers);
            publishers = Set.copyOf(publishers);
            languages = List.copyOf(languages);
            if ((description == null && releaseDate == null && developers.isEmpty() && publishers.isEmpty()
                    && providerType == null && languages.isEmpty() && coverUrl == null)
                    || (description != null && (description.isBlank() || description.length() > 16000))
                    || developers.size() > 100 || publishers.size() > 100 || languages.size() > 100
                    || languages.stream().map(LanguageSupport::languageCode).distinct().count() != languages.size()
                    || java.util.stream.Stream.concat(developers.stream(), publishers.stream())
                    .anyMatch(name -> name.isBlank() || name.length() > 256)
                    || (providerType != null && providerType.length() > 64)
                    || (coverUrl != null && !com.ltz.game.application.service.ProviderCoverPolicy.accepts(coverUrl))) {
                throw new com.ltz.game.domain.exception.ProviderException("INVALID_PROVIDER_RESPONSE");
            }
        }
    }
    /**
     * Bir dilin sağlayıcıda bildirilen desteği. Sağlayıcının listelemediği tür UNKNOWN'dır, UNSUPPORTED değildir
     * (yokluk, desteklenmediği anlamına gelmez). Kod küçük harf BCP47 benzeri olmalıdır (ör. tr-tr).
     */
    record LanguageSupport(String languageCode, com.ltz.game.domain.enums.LanguageSupportStatus interfaceSupport,
                           com.ltz.game.domain.enums.LanguageSupportStatus audioSupport,
                           com.ltz.game.domain.enums.LanguageSupportStatus subtitleSupport) {
        public LanguageSupport {
            if (languageCode == null || !languageCode.matches("[a-z]{2,3}(-[a-z0-9]{2,8}){0,3}")
                    || interfaceSupport == null || audioSupport == null || subtitleSupport == null)
                throw new com.ltz.game.domain.exception.ProviderException("INVALID_PROVIDER_RESPONSE");
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
    /**
     * {@code skippedRecords}: sağlayıcının döndürdüğü ama kayıt düzeyinde geçersiz olduğu için güvenle atlanan
     * kayıt sayısı (ör. boş ad). Cursor atlanan kayıtların da ilerisine taşınır; sayı sessizce yutulmaz, loglanır.
     */
    record CatalogPage(List<CatalogItem> items, long nextAppId, boolean hasMore, int skippedRecords) {
        public CatalogPage {
            items = List.copyOf(items);
            if (skippedRecords < 0) throw new IllegalArgumentException("Geçersiz atlanan kayıt sayısı.");
        }
        public CatalogPage(List<CatalogItem> items, long nextAppId, boolean hasMore) { this(items, nextAppId, hasMore, 0); }
    }
}
