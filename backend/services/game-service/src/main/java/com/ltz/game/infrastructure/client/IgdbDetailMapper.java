package com.ltz.game.infrastructure.client;

import com.ltz.game.application.port.GameProvider;
import com.ltz.game.domain.enums.LanguageSupportStatus;
import com.ltz.game.domain.exception.ProviderException;
import com.ltz.game.infrastructure.client.dto.response.IgdbResponses;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * IGDB yanıtını Game'in detay modeline çevirir. Yalnız sağlayıcının gerçekten verdiği alanlar taşınır; veri yoksa
 * hiçbir şey uydurulmaz. IGDB yalnız desteklenen dilleri listeler: listelenmeyen arayüz/ses/altyazı türü UNKNOWN
 * kalır, UNSUPPORTED sayılmaz. Bilinmeyen tür adları ve bozuk yerel ayarlar atlanır.
 */
final class IgdbDetailMapper {
    private static final long MAX_EPOCH_SECOND = 253_402_300_799L;

    private IgdbDetailMapper() { }

    static GameProvider.Details toDetails(IgdbResponses.Game game) {
        Set<String> developers = new LinkedHashSet<>();
        Set<String> publishers = new LinkedHashSet<>();
        for (IgdbResponses.InvolvedCompany involved : orEmpty(game.involvedCompanies())) {
            String name = involved == null || involved.company() == null ? null : involved.company().name();
            if (name == null || name.isBlank()) {
                continue;
            }
            if (Boolean.TRUE.equals(involved.developer())) developers.add(name.strip());
            if (Boolean.TRUE.equals(involved.publisher())) publishers.add(name.strip());
        }
        String summary = game.summary() == null || game.summary().isBlank() ? null : game.summary().strip();
        String cover = game.cover() == null ? null : com.ltz.game.application.service.ProviderCoverPolicy.igdbCover(game.cover().imageId());
        return new GameProvider.Details(summary, releaseDate(game.firstReleaseDate()), developers, publishers, providerType(game.gameType()),
                languages(orEmpty(game.languageSupports())), cover);
    }

    /**
     * IGDB {@code game_type} kimliği (api-docs.igdb.com/#game-type) → Game'in sağlayıcı tür anahtarı. Yalnız açık kanıt
     * eşlenir: 0 Main Game, 8 Remake, 9 Remaster tam oyundur; 1 DLC/Addon, 2 Expansion ve 4 Standalone Expansion DLC (oyun genişletmesi), 3 Bundle ve 13 Pack/Addon paket
     * sayılır (hedef kapsam dışı). Paket türleri ayrı tutulur: 3 "bundle", 13 "pack" (Steam DLC kanıtıyla birlikte
     * DLC'ye yükseltilebilir; bundle asla). Diğer her değer (mod, episode, season, port, expanded game, update, fork, yeni
     * veya bilinmeyen) null döner ve oyun UNKNOWN kalır; ad, popülerlik veya appid'den tür türetilmez.
     */
    static String providerType(Long gameType) {
        if (gameType == null) {
            return null;
        }
        return switch (gameType.intValue() == gameType ? gameType.intValue() : -1) {
            case 0, 8, 9 -> "main_game";
            case 1, 2, 4 -> "dlc";
            case 3 -> "bundle";
            case 13 -> "pack";
            default -> null;
        };
    }

    /**
     * IGDB {@code first_release_date} Unix saniyesi (UTC) → gün. Toplu yolda geçersiz değer sayfayı düşürmez: aralık dışı
     * (ör. milisaniye sanılan değer, yıl 9999 sonrası) null döner ve o oyunun tarihi yazılmaz. Negatif (1970 öncesi) geçerlidir.
     */
    static LocalDate lenientReleaseDate(Long epochSecond) {
        if (epochSecond == null || Math.abs(epochSecond) > MAX_EPOCH_SECOND) {
            return null;
        }
        return Instant.ofEpochSecond(epochSecond).atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static final java.util.regex.Pattern TERM_CODE = java.util.regex.Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    private static final int MAX_TERMS = 50;

    /**
     * IGDB tür/platform kayıtları → terim kanıtı. Kod, IGDB slug'ının küçük harfli hâlidir ve katalog kod kuralına
     * ({@code [a-z0-9][a-z0-9-]{0,63}}) uymalıdır; uymayan, eksik veya yinelenen kayıt atlanır (kod uydurulmaz, ad kırpılmaz).
     * Ad yoksa veya 128 karakteri aşıyorsa ad boş bırakılır, kod yine geçerlidir.
     */
    static List<GameProvider.TermEvidence> termEvidence(List<IgdbResponses.Term> terms) {
        Map<String, GameProvider.TermEvidence> byCode = new LinkedHashMap<>();
        for (IgdbResponses.Term term : orEmpty(terms)) {
            if (term == null || term.slug() == null) {
                continue;
            }
            String code = term.slug().strip().toLowerCase(Locale.ROOT);
            if (!TERM_CODE.matcher(code).matches() || byCode.containsKey(code) || byCode.size() >= MAX_TERMS) {
                continue;
            }
            String name = term.name() == null ? null : term.name().strip();
            byCode.put(code, new GameProvider.TermEvidence(code, name == null || name.isEmpty() || name.length() > 128 ? null : name));
        }
        return List.copyOf(byCode.values());
    }

    private static LocalDate releaseDate(Long epochSecond) {
        if (epochSecond == null) {
            return null;
        }
        if (Math.abs(epochSecond) > MAX_EPOCH_SECOND) {
            throw new ProviderException("INVALID_PROVIDER_RESPONSE");
        }
        return Instant.ofEpochSecond(epochSecond).atZone(ZoneOffset.UTC).toLocalDate();
    }

    /** IGDB yalnız desteklenen dilleri listeler: listelenmeyen tür UNKNOWN kalır, UNSUPPORTED sayılmaz. */
    private static List<GameProvider.LanguageSupport> languages(List<IgdbResponses.LanguageSupport> supports) {
        Map<String, LanguageSupportStatus[]> byCode = new LinkedHashMap<>();
        for (IgdbResponses.LanguageSupport support : supports) {
            String code = support == null || support.language() == null ? null : code(support.language().locale());
            int slot = support == null || support.languageSupportType() == null ? -1 : slot(support.languageSupportType().name());
            if (code != null && slot >= 0) {
                LanguageSupportStatus[] row = byCode.computeIfAbsent(code, key -> unknownRow());
                row[slot] = LanguageSupportStatus.SUPPORTED;
            }
        }
        List<GameProvider.LanguageSupport> result = new ArrayList<>();
        byCode.forEach((code, row) -> result.add(new GameProvider.LanguageSupport(code, row[0], row[1], row[2])));
        return result;
    }

    private static LanguageSupportStatus[] unknownRow() {
        return new LanguageSupportStatus[]{LanguageSupportStatus.UNKNOWN, LanguageSupportStatus.UNKNOWN, LanguageSupportStatus.UNKNOWN};
    }

    private static String code(String locale) {
        if (locale == null) {
            return null;
        }
        String code = locale.strip().toLowerCase(Locale.ROOT).replace('_', '-');
        return code.matches("[a-z]{2,3}(-[a-z0-9]{2,8}){0,3}") ? code : null;
    }

    /** 0 arayüz, 1 ses, 2 altyazı; bilinmeyen tür adı yok sayılır (uydurulmaz). */
    private static int slot(String type) {
        if (type == null) {
            return -1;
        }
        return switch (type.strip().toLowerCase(Locale.ROOT)) {
            case "interface" -> 0;
            case "audio" -> 1;
            case "subtitles" -> 2;
            default -> -1;
        };
    }

    private static <T> List<T> orEmpty(List<T> values) { return values == null ? List.of() : values; }
}
