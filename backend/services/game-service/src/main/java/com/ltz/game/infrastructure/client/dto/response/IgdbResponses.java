package com.ltz.game.infrastructure.client.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * IGDB v4 yanıtlarının yalnız kullanılan alanları (şema: api-docs.igdb.com). Yanıt güvenilmez girdidir; alanlar
 * adaptörde doğrulanır. Bilinmeyen alanlar yok sayılır.
 */
public final class IgdbResponses {
    private IgdbResponses() { }

    /** Twitch OAuth client-credentials yanıtı. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Token(@JsonProperty("access_token") String accessToken,
                        @JsonProperty("expires_in") Long expiresIn,
                        @JsonProperty("token_type") String tokenType) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Source(Long id, String name) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExternalGame(Long game, String uid) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Game(String summary,
                       @JsonProperty("first_release_date") Long firstReleaseDate,
                       @JsonProperty("involved_companies") List<InvolvedCompany> involvedCompanies,
                       @JsonProperty("language_supports") List<LanguageSupport> languageSupports) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InvolvedCompany(Boolean developer, Boolean publisher, Company company) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Company(String name) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LanguageSupport(Language language,
                                  @JsonProperty("language_support_type") SupportType languageSupportType) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Language(String locale) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SupportType(String name) { }
}
