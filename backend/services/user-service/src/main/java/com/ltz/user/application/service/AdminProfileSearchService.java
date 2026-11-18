package com.ltz.user.application.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.user.domain.entity.UserProfile;
import com.ltz.user.infrastructure.persistence.repository.UserProfileRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Yönetim paneli için profil arama: yönetici {@code @kullanıcıadı} ya da görünen ad önekini yazar, hesap kimliğini
 * (UUID) bilmesi gerekmez. Herkese açık aramadan farkı: askıdaki, yasaklı ve silinme sürecindeki hesapları da bulur ve
 * engel ilişkisine bakmaz. Yalnız ADMIN çağırabilir (SecurityConfig). Yalnız ad ve durum döner; biyografi, tercih,
 * gizlilik ya da başka kişisel veri verilmez. Arama metni günlüğe yazılmaz; kim kaç sonuç aldı kaydedilir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminProfileSearchService {

    static final int SEARCH_LIMIT = 20;

    private final UserProfileRepository profiles;

    @Transactional(readOnly = true)
    public List<AdminProfileView> search(UUID admin, String raw) {
        List<AdminProfileView> found = profiles.adminSearch(ProfileText.searchPrefix(withoutAt(raw)), SEARCH_LIMIT)
                .stream().map(AdminProfileSearchService::view).toList();
        log.info("Yönetici profil araması: admin={} sonuc={}", admin, found.size());
        return found;
    }

    /** Sitedeki gibi {@code @ada} yazılabilir; baştaki tek {@code @} atılır (kullanıcı adında {@code @} bulunmaz). */
    private static String withoutAt(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.strip();
        return trimmed.startsWith("@") ? trimmed.substring(1) : trimmed;
    }

    private static AdminProfileView view(UserProfile profile) {
        return new AdminProfileView(profile.getUserId(), profile.getUsernameSnapshot(), profile.getDisplayName(),
                profile.getAccountStatus(), profile.getMemberSince());
    }
}
