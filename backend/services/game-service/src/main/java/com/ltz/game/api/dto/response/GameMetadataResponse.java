package com.ltz.game.api.dto.response;
import com.ltz.game.domain.enums.LanguageSupportStatus;
import com.ltz.game.domain.enums.MediaType;
import com.ltz.game.domain.enums.MetadataDataState;
import com.ltz.game.domain.enums.RequirementLevel;
import java.util.List;
import java.util.UUID;
public record GameMetadataResponse(UUID gameId, List<Term> genres, List<Term> categories,
        List<Term> tags, List<Term> platforms, List<String> developers, List<String> publishers,
        List<Media> media, List<Requirement> systemRequirements, List<String> languages, Boolean earlyAccess,
        String metadataLanguage, MetadataDataState languageDataState, List<LanguageSupport> languageSupport,
        List<Provenance> provenance) {
    public record LanguageSupport(String languageCode, LanguageSupportStatus interfaceSupport, LanguageSupportStatus audioSupport, LanguageSupportStatus subtitleSupport) { }
    public record Provenance(String field, String source, MetadataDataState dataState, java.time.Instant recordedAt, java.time.Instant verifiedAt) { }
    public record Term(UUID id, String code, String nameTr, String nameEn) { }
    public record Media(MediaType type, String url, String provider, int sortOrder) { }
    public record Requirement(RequirementLevel level, String platform, String os, String cpu,
            String gpu, Integer ramMb, Integer storageMb, String rawText) { }
}
