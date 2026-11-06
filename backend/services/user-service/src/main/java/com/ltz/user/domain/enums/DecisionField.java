package com.ltz.user.domain.enums;

import java.util.Optional;

/**
 * Servislerin gizlilik kararı isteyebildiği alanlar. İlk dördü kullanıcının yönettiği profil bölümleridir
 * ({@link PrivacyField}); {@link #REVIEW_AUTHOR} profil ayarına bağlı değildir: bir yazarın herkese açık içeriğinin
 * (ör. ürün yorumu) izleyiciye gösterilip gösterilemeyeceğini yalnız hesap durumu ve çift yönlü engel belirler.
 */
public enum DecisionField {
    FRIENDS_LIST(PrivacyField.FRIENDS_LIST),
    LIBRARY(PrivacyField.LIBRARY),
    HARDWARE(PrivacyField.HARDWARE),
    PERFORMANCE(PrivacyField.PERFORMANCE),
    REVIEW_AUTHOR(null);

    private final PrivacyField profileField;

    DecisionField(PrivacyField profileField) {
        this.profileField = profileField;
    }

    /** Profil bölümü ayarına bağlı alanlar için karşılığı; yazar içeriği için boş. */
    public Optional<PrivacyField> profileField() {
        return Optional.ofNullable(profileField);
    }
}
