package com.ltz.hardware.domain.entity;

import com.ltz.hardware.domain.enums.CaseColor;
import com.ltz.hardware.domain.enums.DeviceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class HardwareProfileTest {
    @Test
    void createsIndependentManualProfileWithOwnerAndUtcTimes() {
        var owner = UUID.randomUUID();
        var profile = new HardwareProfile(owner, "  Oyun bilgisayarım  ", DeviceType.DESKTOP);
        var another = new HardwareProfile(owner, "Dizüstüm", DeviceType.LAPTOP);
        assertThat(profile.getId()).isNotNull().isNotEqualTo(another.getId());
        assertThat(profile.getUserId()).isEqualTo(owner);
        assertThat(profile.getName()).isEqualTo("Oyun bilgisayarım");
        assertThat(profile.getDeviceType()).isEqualTo(DeviceType.DESKTOP);
        assertThat(profile.getSource()).isEqualTo("MANUAL");
        assertThat(profile.getVersion()).isZero();
        assertThat(profile.getCreatedAt()).isEqualTo(profile.getUpdatedAt());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "a\nb", "<script>", "ad>"})
    void rejectsInvalidNames(String name) {
        var owner = UUID.randomUUID();
        assertThatIllegalArgumentException().isThrownBy(
                () -> new HardwareProfile(owner, name, DeviceType.OTHER));
    }

    @Test
    void rejectsOversizedNameAndMissingOwnerOrType() {
        var owner = UUID.randomUUID();
        assertThatIllegalArgumentException().isThrownBy(
                () -> new HardwareProfile(owner, "a".repeat(101), DeviceType.OTHER));
        assertThatNullPointerException().isThrownBy(
                () -> new HardwareProfile(null, "PC", DeviceType.OTHER));
        assertThatNullPointerException().isThrownBy(
                () -> new HardwareProfile(owner, "PC", null));
    }

    @Test
    void caseColorDefaultsToBlackAndChangesOnlyThroughExplicitUpdate() {
        var owner = UUID.randomUUID();
        var profile = new HardwareProfile(owner, "PC", DeviceType.DESKTOP);
        assertThat(profile.getCaseColor()).isEqualTo(CaseColor.BLACK);
        profile.update("PC", DeviceType.DESKTOP, CaseColor.GRAPHITE);
        assertThat(profile.getCaseColor()).isEqualTo(CaseColor.GRAPHITE);
        profile.update("PC 2", DeviceType.DESKTOP);
        assertThat(profile.getCaseColor()).isEqualTo(CaseColor.GRAPHITE);
        assertThat(new HardwareProfile(owner, "W", DeviceType.DESKTOP, CaseColor.WHITE).getCaseColor())
                .isEqualTo(CaseColor.WHITE);
        assertThatNullPointerException().isThrownBy(
                () -> new HardwareProfile(owner, "PC", DeviceType.DESKTOP, null));
        assertThatNullPointerException().isThrownBy(
                () -> profile.update("PC", DeviceType.DESKTOP, null));
    }
}
