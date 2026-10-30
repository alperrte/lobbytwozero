package com.ltz.user.application.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.user.application.mapper.ProfileMapper;
import com.ltz.user.domain.entity.UserMedia;
import com.ltz.user.domain.entity.UserProfile;
import com.ltz.user.domain.enums.MediaKind;
import com.ltz.user.domain.enums.MediaStatus;
import com.ltz.user.infrastructure.persistence.repository.SocialProjectionRepository;
import com.ltz.user.infrastructure.persistence.repository.UserMediaRepository;
import com.ltz.user.infrastructure.persistence.repository.UserProfileRepository;

import lombok.RequiredArgsConstructor;

/**
 * Oyuncu arama (USER-11) ve toplu profil kartı. Kart yalnız temel alanları (ad, avatar) taşır; bu alanlar her giriş
 * yapmış üyeye açıktır. Aramada askıdaki hesaplar çıkmaz (K6); her iki uçta da yasaklı/devre dışı hesaplar ve izleyiciyle
 * engel ilişkisi olanlar listelenmez. Ayrı bir arama servisi yoktur; sorgu kendi veritabanında, indeksli önek araması.
 */
@Service
@RequiredArgsConstructor
public class PlayerSearchService {

    static final int SEARCH_LIMIT = 20;

    private final UserProfileRepository profiles;
    private final UserMediaRepository media;
    private final SocialProjectionRepository social;

    @Transactional(readOnly = true)
    public List<ProfileCardView> search(UUID viewer, String query) {
        return cards(profiles.search(ProfileText.searchPrefix(query), viewer, SEARCH_LIMIT));
    }

    /** İstek sırasını korur; olmayan ya da gösterilemeyen kimlikler sessizce atlanır (hangisi olduğu sızdırılmaz). */
    @Transactional(readOnly = true)
    public List<ProfileCardView> batch(UUID viewer, List<UUID> requested) {
        Set<UUID> ids = new LinkedHashSet<>(requested);
        if (ids.isEmpty()) {
            return List.of();
        }
        Set<UUID> blocked = new HashSet<>(social.blockedAmong(viewer, ids));
        Map<UUID, UserProfile> found = profiles.findAllById(ids).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, Function.identity()));
        List<UserProfile> visible = new ArrayList<>();
        for (UUID id : ids) {
            UserProfile profile = found.get(id);
            if (profile != null && profile.isPubliclyVisible() && !blocked.contains(id)) {
                visible.add(profile);
            }
        }
        return cards(visible);
    }

    private List<ProfileCardView> cards(List<UserProfile> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<UserMedia>> avatars = media.findByUserIdInAndStatus(
                        list.stream().map(UserProfile::getUserId).toList(), MediaStatus.CURRENT).stream()
                .filter(item -> item.getKind() == MediaKind.AVATAR)
                .collect(Collectors.groupingBy(UserMedia::getUserId));
        return list.stream()
                .map(profile -> new ProfileCardView(profile.getUserId(), profile.getUsernameSnapshot(),
                        profile.getDisplayName(),
                        ProfileMapper.mediaOf(avatars.getOrDefault(profile.getUserId(), List.of()), MediaKind.AVATAR)))
                .toList();
    }
}
