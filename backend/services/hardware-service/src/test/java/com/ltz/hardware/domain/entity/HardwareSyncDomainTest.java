package com.ltz.hardware.domain.entity;

import com.ltz.hardware.domain.enums.ComponentCategory;
import com.ltz.hardware.domain.exception.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HardwareSyncDomainTest {
    @Test
    void consentAndSequenceCannotBeReusedAfterRevocation() {
        var profile = UUID.randomUUID(); var device = UUID.randomUUID();
        var consent = new HardwareSyncConsent(profile, device); var generation = consent.getGeneration();
        var wrongDevice = UUID.randomUUID(); var wrongGeneration = UUID.randomUUID();
        assertThatThrownBy(() -> consent.requireGrant(wrongDevice, generation)).isInstanceOf(HardwareNotFoundException.class);
        assertThatThrownBy(() -> consent.requireGrant(device, wrongGeneration)).isInstanceOf(HardwareConflictException.class);
        consent.revoke();
        var now = Instant.now();
        assertThatThrownBy(() -> consent.advance(1, now, now)).isInstanceOf(HardwareConflictException.class);
        consent.grant(device);
        assertThat(consent.getGeneration()).isNotEqualTo(generation);
        assertThat(consent.getLastSequence()).isZero();
    }

    @Test
    void scanReplacementCannotOverwriteManualOrDifferentSyncKey() {
        var profile = UUID.randomUUID();
        var scan = new HardwareScan.Component(UUID.randomUUID(), ComponentCategory.CPU, "Model", null, 1, null);
        var component = HardwareComponent.desktop(profile, scan);
        var other = new HardwareScan.Component(UUID.randomUUID(), ComponentCategory.CPU, "Other", null, 1, null);
        assertThatThrownBy(() -> component.replaceFromScan(other)).isInstanceOf(IllegalArgumentException.class);
        component.replaceWith(new HardwareComponent(profile, other.selection(), 1));
        assertThat(component.getSource()).isEqualTo("MANUAL");
        assertThat(component.getSyncComponentKey()).isNull();
        assertThatThrownBy(() -> component.replaceFromScan(scan)).isInstanceOf(IllegalArgumentException.class);
    }
}
