package com.ltz.hardware.domain.entity;

import com.ltz.hardware.domain.enums.ComponentCategory;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HardwareScanTest {
    @Test
    void enforcesDomainEnvelopeAndDefensiveSnapshot() {
        var now = Instant.now();
        var component = new HardwareScan.Component(UUID.randomUUID(), ComponentCategory.CPU, "Model", null, 1, null);
        var entries = new ArrayList<>(List.of(component));
        var scan = new HardwareScan(1, now, entries);
        entries.clear();
        assertThat(scan.components()).hasSize(1);
        var frozen = scan.components();
        var valid = List.of(component);
        var containsNull = Arrays.asList(component, null);
        assertThatThrownBy(frozen::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new HardwareScan(2, now, valid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HardwareScan(1, null, valid)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HardwareScan(1, now, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HardwareScan(1, now, containsNull)).isInstanceOf(IllegalArgumentException.class);
        assertThat(component.selection().productId()).isNull();
        assertThat(component.selection().brandId()).isNull();
    }

    @Test
    void enforcesComponentIdentityCategoryAndQuantityAtDomainBoundary() {
        var key = UUID.randomUUID();
        assertThatThrownBy(() -> new HardwareScan.Component(null, ComponentCategory.CPU, "Model", null, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HardwareScan.Component(key, null, "Model", null, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HardwareScan.Component(key, ComponentCategory.CPU, "Model", null, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HardwareScan.Component(key, ComponentCategory.CPU, "Model", null, 129, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
