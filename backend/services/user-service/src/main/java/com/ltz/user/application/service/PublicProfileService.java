package com.ltz.user.application.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.user.application.mapper.ProfileMapper;
import com.ltz.user.application.service.PublicProfileView.Count;
import com.ltz.user.application.service.PublicProfileView.Details;
import com.ltz.user.application.service.PublicProfileView.Library;
import com.ltz.user.application.service.PublicProfileView.Sections;
import com.ltz.user.application.service.PublicProfileView.SteamLink;
import com.ltz.user.application.service.PublicProfileView.Summary;
import com.ltz.user.application.service.PublicProfileView.SummaryStatus;
import com.ltz.user.domain.entity.LinkedAccount;
import com.ltz.user.domain.entity.PrivacySettings;
import com.ltz.user.domain.entity.UserMedia;
import com.ltz.user.domain.entity.UserProfile;
import com.ltz.user.domain.enums.LinkedProvider;
import com.ltz.user.domain.enums.MediaKind;
import com.ltz.user.domain.enums.MediaStatus;
import com.ltz.user.domain.enums.PrivacyField;
import com.ltz.user.domain.enums.ViewerRelation;
import com.ltz.user.domain.enums.Visibility;
import com.ltz.user.domain.exception.UserErrorCode;
import com.ltz.user.domain.exception.UserException;
import com.ltz.user.infrastructure.config.SocialProperties;
import com.ltz.user.infrastructure.persistence.repository.LinkedAccountRepository;
import com.ltz.user.infrastructure.persistence.repository.PrivacySettingsRepository;
import com.ltz.user.infrastructure.persistence.repository.ProfileCounterRepository;
import com.ltz.user.infrastructure.persistence.repository.ProfileCounterRepository.Counters;
import com.ltz.user.infrastructure.persistence.repository.SocialProjectionRepository;
import com.ltz.user.infrastructure.persistence.repository.UserMediaRepository;
import com.ltz.user.infrastructure.persistence.repository.UserProfileRepository;

import lombok.RequiredArgsConstructor;

/**
 * Herkese açık profil (USER-05) ve profil özetleri (USER-12). Görünürlük tek kuraldan gelir ({@link PrivacyRules}). Olmayan
 * profil, yasaklı/devre dışı/silinmekte olan hesap ve izleyiciyle arasında herhangi bir yönde engel olan profil <b>aynı</b>
 * {@code 404}'ü verir; hiçbiri birbirinden ayırt edilemez.
 */
@Service
@RequiredArgsConstructor
public class PublicProfileService {

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,20}$");

    private final UserProfileRepository profiles;
    private final UserMediaRepository media;
    private final PrivacySettingsRepository settings;
    private final SocialProjectionRepository social;
    private final ProfileCounterRepository counters;
    private final LinkedAccountRepository links;
    private final SocialProperties socialProperties;

    @Transactional(readOnly = true)
    public PublicProfileView byId(UUID ownerId, Optional<UUID> viewer) {
        return view(profiles.findById(ownerId), viewer);
    }

    @Transactional(readOnly = true)
    public PublicProfileView byUsername(String username, Optional<UUID> viewer) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw notFound();
        }
        return view(profiles.findByUsernameNormalized(UserProfile.normalize(username)), viewer);
    }

    private PublicProfileView view(Optional<UserProfile> found, Optional<UUID> viewer) {
        UserProfile profile = found.filter(UserProfile::isPubliclyVisible).orElseThrow(PublicProfileService::notFound);
        UUID ownerId = profile.getUserId();
        ViewerRelation relation = relation(viewer, ownerId);
        PrivacySettings privacy = settings.findById(ownerId).orElseGet(() -> PrivacySettings.defaults(ownerId));
        List<UserMedia> current = media.findByUserIdAndStatus(ownerId, MediaStatus.CURRENT);

        Details details = PrivacyRules.showsDetails(relation, privacy.getGuestAccess())
                ? new Details(profile.getBio(), List.copyOf(profile.getPlayerTypes()),
                        List.copyOf(profile.getFavoriteGenres()), List.copyOf(profile.getFavoritePlatforms()),
                        profile.getCountry(), steamLink(ownerId))
                : null;
        Sections sections = new Sections(allows(relation, privacy, PrivacyField.FRIENDS_LIST),
                allows(relation, privacy, PrivacyField.LIBRARY), allows(relation, privacy, PrivacyField.HARDWARE),
                allows(relation, privacy, PrivacyField.PERFORMANCE));
        return new PublicProfileView(ownerId, profile.getUsernameSnapshot(), profile.getDisplayName(),
                ProfileMapper.mediaOf(current, MediaKind.AVATAR), ProfileMapper.mediaOf(current, MediaKind.COVER),
                details, sections, summary(ownerId, relation, privacy, sections));
    }

    private SteamLink steamLink(UUID ownerId) {
        return links.findByUserIdAndProvider(ownerId, LinkedProvider.STEAM).filter(LinkedAccount::isPubliclyShown)
                .map(link -> new SteamLink(link.getExternalId(), LinkedProvider.STEAM.profileUrl(link.getExternalId())))
                .orElse(null);
    }

    /** Engel varsa ilişki hesaplanmaz: profil yokmuş gibi {@code 404}. */
    private ViewerRelation relation(Optional<UUID> viewer, UUID ownerId) {
        if (viewer.isEmpty()) {
            return ViewerRelation.GUEST;
        }
        UUID viewerId = viewer.get();
        if (viewerId.equals(ownerId)) {
            return ViewerRelation.OWNER;
        }
        if (social.blockedEitherWay(viewerId, ownerId)) {
            throw notFound();
        }
        return social.areFriends(viewerId, ownerId) ? ViewerRelation.FRIEND : ViewerRelation.MEMBER;
    }

    private Summary summary(UUID ownerId, ViewerRelation relation, PrivacySettings privacy, Sections sections) {
        Optional<Counters> stored = counters.find(ownerId);
        Count friends;
        if (!sections.friendsList()) {
            friends = new Count(SummaryStatus.HIDDEN, null);
        } else if (!socialProperties.projectionEnabled()) {
            friends = new Count(SummaryStatus.UNAVAILABLE, null);
        } else {
            friends = new Count(SummaryStatus.AVAILABLE, Math.toIntExact(social.countFriends(ownerId)));
        }
        Library library;
        Integer collection = stored.map(Counters::libraryCollection).orElse(null);
        if (!sections.library()) {
            library = new Library(SummaryStatus.HIDDEN, null, null, null);
        } else if (collection == null) {
            library = new Library(SummaryStatus.UNAVAILABLE, null, null, null);
        } else {
            Counters values = stored.get();
            library = new Library(SummaryStatus.AVAILABLE, collection, values.libraryCompleted(),
                    values.libraryFavorite());
        }
        // İncelemeler kendi servisinde herkese açık UGC'dir: sayısı PUBLIC bölüm kuralıyla gösterilir.
        Integer reviewCount = stored.map(Counters::reviews).orElse(null);
        Count reviews;
        if (!PrivacyRules.allows(relation, Visibility.PUBLIC, privacy.getGuestAccess())) {
            reviews = new Count(SummaryStatus.HIDDEN, null);
        } else {
            reviews = reviewCount == null ? new Count(SummaryStatus.UNAVAILABLE, null)
                    : new Count(SummaryStatus.AVAILABLE, reviewCount);
        }
        return new Summary(friends, library, reviews);
    }

    private static boolean allows(ViewerRelation relation, PrivacySettings privacy, PrivacyField field) {
        return PrivacyRules.allows(relation, privacy.visibilityOf(field), privacy.getGuestAccess());
    }

    private static UserException notFound() {
        return new UserException(UserErrorCode.PROFILE_NOT_FOUND);
    }
}
