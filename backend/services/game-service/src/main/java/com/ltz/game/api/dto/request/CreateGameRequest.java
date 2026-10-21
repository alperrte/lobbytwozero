package com.ltz.game.api.dto.request;

import com.ltz.game.application.service.GameEdit;
import com.ltz.game.domain.enums.GameType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Set;

/** INTERNAL oyun oluşturma. Köken, durum ve kimlik sunucuda belirlenir; istekten alınmaz. */
public record CreateGameRequest(
        @NotBlank @Size(max=256) String title,
        @Size(max=16000) String description,
        LocalDate releaseDate,
        Boolean earlyAccess,
        @NotNull GameType gameType,
        @Size(max=100) Set<@NotNull @Pattern(regexp="[a-z]{2,3}(-[a-z0-9]{2,8}){0,3}") String> languages,
        @Size(max=100) Set<@NotNull @Size(max=256) String> developers,
        @Size(max=100) Set<@NotNull @Size(max=256) String> publishers,
        @Size(max=100) Set<@NotNull @Pattern(regexp="[a-z0-9][a-z0-9-]{0,63}") String> genres,
        @Size(max=100) Set<@NotNull @Pattern(regexp="[a-z0-9][a-z0-9-]{0,63}") String> categories,
        @Size(max=100) Set<@NotNull @Pattern(regexp="[a-z0-9][a-z0-9-]{0,63}") String> tags,
        @Size(max=100) Set<@NotNull @Pattern(regexp="[a-z0-9][a-z0-9-]{0,63}") String> platforms,
        @Size(max=1024) String coverUrl) {
    public GameEdit toEdit() {
        return new GameEdit(title, description, releaseDate, earlyAccess, gameType, languages, developers, publishers,
                genres, categories, tags, platforms, coverUrl);
    }
}
