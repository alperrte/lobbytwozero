package com.ltz.user.application.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.user.domain.entity.UserConsents;
import com.ltz.user.domain.entity.UserProfile;
import com.ltz.user.domain.enums.AccountStatus;
import com.ltz.user.domain.exception.UserErrorCode;
import com.ltz.user.domain.exception.UserException;
import com.ltz.user.infrastructure.config.SocialProperties;
import com.ltz.user.infrastructure.persistence.repository.UserConsentsRepository;
import com.ltz.user.infrastructure.persistence.repository.UserProfileRepository;

import lombok.RequiredArgsConstructor;

/**
 * Recommendation için aday keşfi (REC-13). Kural, mevcut öneri rızasına ({@code recommendation_granted}) dayanır; ayrı
 * bir rıza yoktur. Fail-closed:
 * <ul>
 *   <li>İzleyicinin kendisi ACTIVE, onboarding'i bitmiş ve rızası açık olmalıdır; değilse boş sayfa döner.</li>
 *   <li>Social engel yansıması güvenilir ilan edilmemişse ({@link SocialProperties#projectionEnabled()} {@code false})
 *       engeller bilinmez: hiç aday dönmez.</li>
 *   <li>Aday: ACTIVE, onboarding'i bitmiş, rızası açık, izleyici olmayan ve izleyiciyle iki yönde engeli olmayan hesap.</li>
 * </ul>
 * "İzleyici uygun değil", "engel var" ve "uygun aday yok" durumları istemciden ayırt edilemez (hep boş sayfa).
 */
@Service
@RequiredArgsConstructor
public class RecommendationCandidateService {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;

    private static final UUID NIL = new UUID(0L, 0L);
    private static final Pattern CODE = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,47}$");

    private final UserProfileRepository profiles;
    private final UserConsentsRepository consents;
    private final SocialProperties socialProperties;

    /** Aday sayfası ve varsa sonraki sayfa imleci. */
    public record CandidatePage(List<UserProfile> candidates, UUID nextCursor) {
        static CandidatePage empty() {
            return new CandidatePage(List.of(), null);
        }
    }

    @Transactional(readOnly = true)
    public CandidatePage candidates(UUID viewer, Integer limit, UUID cursor, String genre, String platform,
            boolean excludeFriends) {
        int pageSize = pageSize(limit);
        String genreCode = code(genre);
        String platformCode = code(platform);
        if (!socialProperties.projectionEnabled() || !eligibleViewer(viewer)) {
            return CandidatePage.empty();
        }
        List<UserProfile> found = profiles.recommendationCandidates(
                viewer, cursor == null ? NIL : cursor, genreCode, platformCode, excludeFriends, pageSize + 1);
        boolean more = found.size() > pageSize;
        List<UserProfile> page = more ? found.subList(0, pageSize) : found;
        return new CandidatePage(List.copyOf(page), more ? page.getLast().getUserId() : null);
    }

    private boolean eligibleViewer(UUID viewer) {
        return profiles.findById(viewer)
                .filter(profile -> profile.getAccountStatus() == AccountStatus.ACTIVE && profile.isOnboardingCompleted())
                .flatMap(profile -> consents.findById(viewer))
                .filter(UserConsents::isRecommendationGranted)
                .isPresent();
    }

    private static int pageSize(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new UserException(UserErrorCode.INVALID_CANDIDATE_QUERY);
        }
        return limit;
    }

    /** Boş/yok süzgeç {@code ""} döner; aksi halde katalog kodu biçimi doğrulanır. */
    private static String code(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String code = raw.strip().toLowerCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) {
            throw new UserException(UserErrorCode.INVALID_CANDIDATE_QUERY);
        }
        return code;
    }
}
