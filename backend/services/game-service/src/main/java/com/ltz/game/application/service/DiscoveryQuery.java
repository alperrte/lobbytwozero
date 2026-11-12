package com.ltz.game.application.service;

/** Sunucu tarafında sınırlandırılmış yerel katalog sayfası. */
public record DiscoveryQuery(int page, int size, String q, String genre, String platform, String language,
        Boolean earlyAccess, java.time.LocalDate releasedAfter, java.time.LocalDate releasedBefore,
        boolean popularOnly, java.math.BigDecimal minRating,
        com.ltz.game.domain.enums.CatalogSort sort, com.ltz.game.domain.enums.CatalogDirection direction,
        boolean discountedOnly,String country,String currency,boolean includeAdult) {
    /** {@code includeAdult}: yetişkin içerik (+18) varsayılan listelerde gizlidir; true ise listeye katılır. Doğrudan bağlantı, kütüphane ve toplu okuma bundan etkilenmez. */
    public DiscoveryQuery(int page,int size,String q,String genre,String platform,String language,Boolean earlyAccess,
            java.time.LocalDate releasedAfter,java.time.LocalDate releasedBefore,boolean popularOnly,java.math.BigDecimal minRating,
            com.ltz.game.domain.enums.CatalogSort sort,com.ltz.game.domain.enums.CatalogDirection direction,
            boolean discountedOnly,String country,String currency) {
        this(page,size,q,genre,platform,language,earlyAccess,releasedAfter,releasedBefore,popularOnly,minRating,sort,direction,discountedOnly,country,currency,false);
    }
    public DiscoveryQuery(int page,int size,String q,String genre,String platform,String language,Boolean earlyAccess,
            java.time.LocalDate releasedAfter,java.time.LocalDate releasedBefore,boolean popularOnly,java.math.BigDecimal minRating,
            com.ltz.game.domain.enums.CatalogSort sort,com.ltz.game.domain.enums.CatalogDirection direction) {
        this(page,size,q,genre,platform,language,earlyAccess,releasedAfter,releasedBefore,popularOnly,minRating,sort,direction,false,"US","USD",false);
    }
    public DiscoveryQuery(int page, int size) { this(page,size,null); }
    public DiscoveryQuery(int page,int size,String q) { this(page,size,q,null,null,null,null,null,null,false,null,
            com.ltz.game.domain.enums.CatalogSort.TITLE,com.ltz.game.domain.enums.CatalogDirection.ASC); }
    public DiscoveryQuery {
        if (page < 0 || page > 10000 || size < 1 || size > 100)
            throw new IllegalArgumentException("Geçersiz katalog sayfası.");
        if (q != null) {
            if (q.isBlank() || q.length() > 128 || q.indexOf(0)>=0) throw new IllegalArgumentException("Geçersiz arama.");
            q=CatalogImportService.normalize(q);
        }
        validateTerm(genre); validateTerm(platform);
        if (language!=null) {
            language=language.toLowerCase(java.util.Locale.ROOT);
            if (!language.matches("[a-z]{2,3}(-[a-z0-9]{2,8}){0,3}")) throw new IllegalArgumentException("Geçersiz dil.");
        }
        if (releasedAfter!=null && releasedBefore!=null && releasedAfter.isAfter(releasedBefore))
            throw new IllegalArgumentException("Geçersiz tarih aralığı.");
        if (sort==null || direction==null) throw new IllegalArgumentException("Geçersiz sıralama.");
        validateDate(releasedAfter); validateDate(releasedBefore);
        validateMinRating(minRating);
        new com.ltz.game.application.port.GamePriceProvider.Market(country,currency);
    }
    /** Review ölçeği 1–10, en çok iki ondalıktır; ölçek dışı değer boş sonuç değil 400 üretir. */
    private static void validateMinRating(java.math.BigDecimal value) {
        if (value!=null && (value.compareTo(java.math.BigDecimal.ONE)<0 || value.compareTo(java.math.BigDecimal.TEN)>0
                || value.stripTrailingZeros().scale()>2)) throw new IllegalArgumentException("Geçersiz puan filtresi.");
    }
    /** minRating veya sort=RATING, hazır bir puan projeksiyonu gerektirir. */
    public boolean ratingBased() { return minRating!=null || sort==com.ltz.game.domain.enums.CatalogSort.RATING; }
    private static void validateTerm(String value) {
        if (value!=null && (value.isBlank() || value.length()>64 || value.indexOf(0)>=0)) throw new IllegalArgumentException("Geçersiz filtre.");
    }
    private static void validateDate(java.time.LocalDate value) {
        if (value!=null && (value.getYear()<1 || value.getYear()>9999)) throw new IllegalArgumentException("Geçersiz tarih.");
    }
    public static DiscoveryQuery popular(int page,int size) {
        return new DiscoveryQuery(page,size,null,null,null,null,null,null,null,true,null,
                com.ltz.game.domain.enums.CatalogSort.POPULARITY,com.ltz.game.domain.enums.CatalogDirection.DESC);
    }
}
