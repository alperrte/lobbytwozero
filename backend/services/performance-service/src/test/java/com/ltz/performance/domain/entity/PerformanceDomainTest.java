package com.ltz.performance.domain.entity;

import com.ltz.performance.api.dto.request.SyncBatchRequest;
import com.ltz.performance.domain.enums.DeviceLinkStatus;
import com.ltz.performance.infrastructure.security.PerformanceAuthProperties;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PerformanceDomainTest {
    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void deviceLinkLifecycleIsIdempotent() {
        UUID device = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        var link = DeviceLink.create(device, owner, "  Oyun\u0000PC  ", T0);
        assertThat(link.isActive()).isTrue();
        assertThat(link.getLabel()).isEqualTo("Oyun PC");
        assertThat(link.isOwnedBy(owner)).isTrue();
        assertThat(link.isOwnedBy(UUID.randomUUID())).isFalse();

        link.disable(T0.plusSeconds(10));
        link.disable(T0.plusSeconds(99));
        assertThat(link.getStatus()).isEqualTo(DeviceLinkStatus.DISABLED);
        assertThat(link.getDisabledAt()).isEqualTo(T0.plusSeconds(10));

        link.activate("Yeni", T0.plusSeconds(20));
        assertThat(link.isActive()).isTrue();
        assertThat(link.getDisabledAt()).isNull();
        link.recordSync(T0.plusSeconds(30));
        assertThat(link.getLastSyncAt()).isEqualTo(T0.plusSeconds(30));
    }

    @Test
    void labelIsNormalizedAndBounded() {
        assertThat(DeviceLink.normalizeLabel(null)).isEqualTo("Cihaz");
        assertThat(DeviceLink.normalizeLabel("   \t ")).isEqualTo("Cihaz");
        assertThat(DeviceLink.normalizeLabel("x".repeat(200))).hasSize(DeviceLink.LABEL_MAX);
        String emoji = "🎮".repeat(80);
        assertThat(DeviceLink.normalizeLabel(emoji).codePointCount(0, DeviceLink.normalizeLabel(emoji).length()))
                .isEqualTo(DeviceLink.LABEL_MAX);
    }

    @Test
    void linkRequiresDeviceAndOwner() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> DeviceLink.create(null, id, "a", T0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> DeviceLink.create(id, null, "a", T0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void consentGrantAndRevokeAreIdempotent() {
        var consent = SyncConsent.granted(UUID.randomUUID(), T0);
        assertThat(consent.isGranted()).isTrue();
        consent.grant(T0.plusSeconds(5));
        assertThat(consent.getGrantedAt()).isEqualTo(T0);

        consent.revoke(T0.plusSeconds(10));
        consent.revoke(T0.plusSeconds(20));
        assertThat(consent.isGranted()).isFalse();
        assertThat(consent.getRevokedAt()).isEqualTo(T0.plusSeconds(10));

        consent.grant(T0.plusSeconds(30));
        assertThat(consent.isGranted()).isTrue();
        assertThat(consent.getRevokedAt()).isNull();
        assertThat(consent.getGrantedAt()).isEqualTo(T0.plusSeconds(30));
    }

    @Test
    void digestIsStableSha256Hex() {
        assertThat(PerformanceDigest.hash("v1|1|1|1")).matches("[0-9a-f]{64}").isEqualTo(PerformanceDigest.hash("v1|1|1|1"));
        assertThat(PerformanceDigest.hash("a")).isNotEqualTo(PerformanceDigest.hash("b"));
    }

    @Test
    void batchCanonicalFormChangesWithAnyField() {
        var base = new SyncBatchRequest(1, T0, 5, null, null);
        assertThat(base.canonicalV1()).isEqualTo("v1|1|" + T0.toEpochMilli() + "|5");
        assertThat(new SyncBatchRequest(1, T0.plusMillis(1), 5, null, null).canonicalV1())
                .isNotEqualTo(base.canonicalV1());
        assertThat(new SyncBatchRequest(1, T0, 6, null, null).canonicalV1()).isNotEqualTo(base.canonicalV1());
    }

    @Test
    void authPropertiesRejectUnsafeValues() {
        URI ok = URI.create("http://auth.internal:8081");
        Duration t = Duration.ofSeconds(1);
        var props = new PerformanceAuthProperties(ok, "ltz-auth", "ltz-api", t, t);
        assertThat(props.jwksUri()).hasToString("http://auth.internal:8081/api/v1/auth/.well-known/jwks.json");
        assertThat(props.devicesUri()).hasToString("http://auth.internal:8081/api/v1/auth/devices");

        for (String bad : new String[]{"ftp://auth", "http://user:pw@auth", "http://auth/path", "http://auth?x=1", "http://auth#f"}) {
            URI badUri = URI.create(bad);
            assertThatThrownBy(() -> new PerformanceAuthProperties(badUri, "i", "a", t, t))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new PerformanceAuthProperties(null, "i", "a", t, t)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PerformanceAuthProperties(ok, " ", "a", t, t)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PerformanceAuthProperties(ok, "i", "", t, t)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PerformanceAuthProperties(ok, "i", "a", Duration.ZERO, t))
                .isInstanceOf(IllegalArgumentException.class);
        Duration tooLong = Duration.ofSeconds(31);
        assertThatThrownBy(() -> new PerformanceAuthProperties(ok, "i", "a", t, tooLong))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
