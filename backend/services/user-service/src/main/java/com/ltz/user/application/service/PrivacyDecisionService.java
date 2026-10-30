package com.ltz.user.application.service;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.user.domain.entity.PrivacySettings;
import com.ltz.user.domain.entity.UserProfile;
import com.ltz.user.domain.enums.PrivacyField;
import com.ltz.user.domain.enums.ViewerRelation;
import com.ltz.user.domain.enums.Visibility;
import com.ltz.user.infrastructure.config.SocialProperties;
import com.ltz.user.infrastructure.persistence.repository.PrivacySettingsRepository;
import com.ltz.user.infrastructure.persistence.repository.SocialProjectionRepository;
import com.ltz.user.infrastructure.persistence.repository.UserProfileRepository;

import lombok.RequiredArgsConstructor;

/**
 * Diğer servislerin gizlilik kararı (Social SOC-12, Hardware HW-08, Library LIB-11). Tüm sahipler için en çok dört sorgu
 * (N+1 yok). Karar fail-closed'dır:
 * <ul>
 *   <li>Sahip izleyiciyse her zaman izin.</li>
 *   <li>Social yansıması güvenilir ilan edilmemişse ({@link SocialProperties#projectionEnabled()} {@code false}) engel ve
 *       arkadaşlık bilinmez; sahibi olmayan izleyiciye <b>hiç</b> izin verilmez (Hardware sözleşmesi: bilinmeyen karar
 *       reddedilir). {@code setting} yine döner; engel/arkadaşlığı kendisi bilen Social yalnız onu kullanır.</li>
 *   <li>Yasaklı/devre dışı/silinmekte olan hesap için ayar {@code PRIVATE} sayılır.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PrivacyDecisionService {

    private final UserProfileRepository profiles;
    private final PrivacySettingsRepository settings;
    private final SocialProjectionRepository social;
    private final SocialProperties socialProperties;

    /** Tek sahip için verilen karar. */
    public record Decision(UUID ownerId, Visibility setting, boolean allowed, boolean friends) {
    }

    @Transactional(readOnly = true)
    public List<Decision> decide(Optional<UUID> viewer, PrivacyField field, List<UUID> owners) {
        Set<UUID> ids = new LinkedHashSet<>(owners);
        Map<UUID, UserProfile> found = profiles.findAllById(ids).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, Function.identity()));
        Map<UUID, PrivacySettings> stored = settings.findByUserIdIn(ids).stream()
                .collect(Collectors.toMap(PrivacySettings::getUserId, Function.identity()));
        boolean trusted = socialProperties.projectionEnabled();
        Set<UUID> friends = trusted && viewer.isPresent()
                ? new HashSet<>(social.friendsAmong(viewer.get(), ids)) : Set.of();
        Set<UUID> blocked = trusted && viewer.isPresent()
                ? new HashSet<>(social.blockedAmong(viewer.get(), ids)) : Set.of();

        return ids.stream().map(owner -> {
            UserProfile profile = found.get(owner);
            PrivacySettings privacy = stored.getOrDefault(owner, PrivacySettings.defaults(owner));
            boolean visibleAccount = profile == null || profile.isPubliclyVisible();
            Visibility setting = visibleAccount ? privacy.visibilityOf(field) : Visibility.PRIVATE;
            boolean isFriend = friends.contains(owner);
            ViewerRelation relation = relation(viewer, owner, isFriend);
            boolean allowed = relation == ViewerRelation.OWNER
                    || (trusted && visibleAccount && !blocked.contains(owner)
                        && PrivacyRules.allows(relation, setting, privacy.getGuestAccess()));
            return new Decision(owner, setting, allowed, isFriend);
        }).toList();
    }

    private static ViewerRelation relation(Optional<UUID> viewer, UUID owner, boolean friend) {
        if (viewer.isEmpty()) {
            return ViewerRelation.GUEST;
        }
        if (viewer.get().equals(owner)) {
            return ViewerRelation.OWNER;
        }
        return friend ? ViewerRelation.FRIEND : ViewerRelation.MEMBER;
    }
}
