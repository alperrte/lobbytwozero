package com.ltz.social.domain.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Arkadaşlığın bileşik anahtarı. Çift sırasızdır: (A,B) ve (B,A) aynı anahtardır; küçük kimlik her zaman
 * {@code userLowId}'dir. Sıralama, DB'deki {@code CHECK (user_low_id < user_high_id)} ile aynı olmak zorundadır.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FriendshipId {

    @Column(name = "user_low_id", nullable = false, updatable = false)
    private UUID userLowId;

    @Column(name = "user_high_id", nullable = false, updatable = false)
    private UUID userHighId;

    private FriendshipId(UUID userLowId, UUID userHighId) {
        this.userLowId = userLowId;
        this.userHighId = userHighId;
    }

    public static FriendshipId of(UUID first, UUID second) {
        return compareUnsigned(first, second) < 0 ? new FriendshipId(first, second) : new FriendshipId(second, first);
    }

    /**
     * PostgreSQL uuid karşılaştırması 16 baytı işaretsiz, baştan sona kıyaslar. {@link UUID#compareTo} ise
     * işaretli olduğundan farklı sıra verebilir ve CHECK kısıtını ihlal ederdi; bu yüzden işaretsiz kıyaslanır.
     */
    static int compareUnsigned(UUID first, UUID second) {
        int byMostSignificant = Long.compareUnsigned(first.getMostSignificantBits(), second.getMostSignificantBits());
        if (byMostSignificant != 0) {
            return byMostSignificant;
        }
        return Long.compareUnsigned(first.getLeastSignificantBits(), second.getLeastSignificantBits());
    }
}
