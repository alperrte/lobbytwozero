package com.ltz.game.application.service;

/** Sunucu tarafında sınırlandırılmış yerel katalog sayfası. */
public record DiscoveryQuery(int page, int size, String q, String genre, String platform, String language,
        Boolean earlyAccess, java.time.LocalDate releasedAfter, java.time.LocalDate releasedBefore,
        boolean popularOnly, java.math.BigDecimal minRating,
        com.ltz.game.domain.enums.CatalogSort sort, com.ltz.game.domain.enums.CatalogDirection direction) {
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
        if (minRating!=null || sort==com.ltz.game.domain.enums.CatalogSort.RATING)
            throw new com.ltz.game.domain.exception.CatalogRatingUnavailableException();
    }
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
