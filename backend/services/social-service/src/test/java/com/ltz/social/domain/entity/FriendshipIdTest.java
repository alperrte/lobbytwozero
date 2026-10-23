package com.ltz.social.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class FriendshipIdTest {

    /** En anlamlı bit 1 olan UUID: işaretli karşılaştırmada küçük, işaretsiz (PostgreSQL) karşılaştırmada büyüktür. */
    private static final UUID HIGH_BIT_SET = new UUID(0x8000_0000_0000_0000L, 0L);
    private static final UUID HIGH_BIT_CLEAR = new UUID(0x0000_0000_0000_0001L, 0L);

    @Test
    void ciftSirasizdir_ayniAnahtariUretir() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertThat(FriendshipId.of(first, second)).isEqualTo(FriendshipId.of(second, first));
        assertThat(FriendshipId.of(first, second).hashCode()).isEqualTo(FriendshipId.of(second, first).hashCode());
    }

    @Test
    void kucukKimlikHerZamanLowTarafindadir_postgresIsaretsizSirasiyla() {
        FriendshipId id = FriendshipId.of(HIGH_BIT_SET, HIGH_BIT_CLEAR);

        // PostgreSQL uuid sıralaması baytsal/işaretsizdir: 0000...01 < 8000...00.
        assertThat(id.getUserLowId()).isEqualTo(HIGH_BIT_CLEAR);
        assertThat(id.getUserHighId()).isEqualTo(HIGH_BIT_SET);
        // java.util.UUID.compareTo bunun tersini söyler; bu yüzden kullanılmaz.
        assertThat(HIGH_BIT_SET.compareTo(HIGH_BIT_CLEAR)).isNegative();
    }

    @Test
    void enAnlamsizYarisiFarkliOlanCiftlerDeDogruSiralanir() {
        UUID smaller = new UUID(7L, 0x0000_0000_0000_0001L);
        UUID larger = new UUID(7L, 0x8000_0000_0000_0000L);

        FriendshipId id = FriendshipId.of(larger, smaller);

        assertThat(id.getUserLowId()).isEqualTo(smaller);
        assertThat(id.getUserHighId()).isEqualTo(larger);
    }

    @Test
    void ayniKimlikIkiTarafaYazilirsaSiraKorunur() {
        UUID same = UUID.randomUUID();

        FriendshipId id = FriendshipId.of(same, same);

        assertThat(id.getUserLowId()).isEqualTo(same);
        assertThat(id.getUserHighId()).isEqualTo(same);
    }
}
