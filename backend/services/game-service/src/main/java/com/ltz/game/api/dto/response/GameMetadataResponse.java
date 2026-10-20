package com.ltz.game.api.dto.response;
import com.ltz.game.domain.enums.*;
import java.util.*;
public record GameMetadataResponse(UUID gameId, List<Term> genres, List<Term> categories,
        List<Term> tags, List<Term> platforms, List<String> developers, List<String> publishers,
        List<Media> media, List<Requirement> systemRequirements) {
    public record Term(UUID id, String code, String nameTr, String nameEn) { }
    public record Media(MediaType type, String url, String provider, int sortOrder) { }
    public record Requirement(RequirementLevel level, String platform, String os, String cpu,
            String gpu, Integer ramMb, Integer storageMb, String rawText) { }
}
