package com.ltz.game.api.dto.request;

import com.ltz.game.application.service.DiscoveryQuery;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class GameDiscoveryRequest {
    private int page;
    private int size=20;
    private String q;
    private String genre;
    private String platform;
    private String language;
    private Boolean earlyAccess;
    @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
    private java.time.LocalDate releasedAfter;
    @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
    private java.time.LocalDate releasedBefore;
    private boolean popularOnly;
    private java.math.BigDecimal minRating;
    private com.ltz.game.domain.enums.CatalogSort sort=com.ltz.game.domain.enums.CatalogSort.TITLE;
    private com.ltz.game.domain.enums.CatalogDirection direction=com.ltz.game.domain.enums.CatalogDirection.ASC;
    public DiscoveryQuery toQuery() {
        return new DiscoveryQuery(page,size,q,genre,platform,language,earlyAccess,releasedAfter,releasedBefore,popularOnly,minRating,sort,direction);
    }
}
