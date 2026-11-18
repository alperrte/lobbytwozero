package com.ltz.game.api.dto.response;

import com.ltz.game.domain.enums.GameType;
import com.ltz.game.domain.enums.RequirementLevel;
import java.util.List;
import java.util.UUID;

public record RecommendationMetadataBatchResponse(List<Item> items, List<UUID> unavailable) {
    public record Item(UUID gameId, GameType gameType, boolean adult, boolean visible, List<Term> genres,
            List<Term> platforms, List<Requirement> systemRequirements, GameMetadataResponse.Provenance requirementsProvenance) { }

    public record Term(String code) { }

    public record Requirement(RequirementLevel level, String platform, Field<String> cpu, Field<String> gpu,
            Field<Integer> ramMb, Field<Integer> storageMb, Field<String> os, Field<Integer> vramMb, Field<String> driver) { }

    public record Field<T>(T value, FieldState state) { }

    public enum FieldState { AVAILABLE, UNKNOWN, UNCOMPARABLE }
}
