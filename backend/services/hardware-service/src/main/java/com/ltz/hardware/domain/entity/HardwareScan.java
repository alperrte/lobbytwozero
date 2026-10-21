package com.ltz.hardware.domain.entity;

import com.ltz.hardware.domain.enums.ComponentCategory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Validated hardware description, never proof of physical-device authenticity. */
public record HardwareScan(int schemaVersion, Instant capturedAt, List<Component> components) {
    public HardwareScan {
        if (schemaVersion != 1 || capturedAt == null || components == null || components.isEmpty()
                || components.size() > 32 || components.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Geçersiz tarama");
        }
        components = components.stream().sorted(Comparator.comparing(Component::componentKey)).toList();
        if (components.stream().map(Component::componentKey).distinct().count() != components.size()) {
            throw new IllegalArgumentException("Tekrarlanan bileşen anahtarı");
        }
        capturedAt = capturedAt.truncatedTo(ChronoUnit.MICROS);
    }

    public void validateTime(Instant now) {
        if (capturedAt.isBefore(now.minus(24, ChronoUnit.HOURS))
                || capturedAt.isAfter(now.plus(5, ChronoUnit.MINUTES))) {
            throw new IllegalArgumentException("Geçersiz tarama zamanı");
        }
    }

    public record Component(UUID componentKey, ComponentCategory category, String modelName,
            String brandName, int quantity, HardwareSpecifications specifications) {
        public Component {
            if (componentKey == null || category == null || quantity < 1 || quantity > 128) {
                throw new IllegalArgumentException("Geçersiz tarama bileşeni");
            }
            modelName = CatalogText.clean(modelName, 160);
            brandName = brandName == null ? null : CatalogText.clean(brandName, 100);
            if (specifications != null) {
                specifications = new HardwareSpecifications(new HardwareSpecifications.Values(
                        specifications.getCapacityGb(), specifications.getClockMhz(), specifications.getCoreCount(),
                        specifications.getPowerWatts(), text(specifications.getMemoryType()), text(specifications.getSocket()),
                        text(specifications.getInterfaceType()), text(specifications.getFormFactor())));
            }
        }

        private static String text(String value) { return value == null ? null : CatalogText.clean(value, 32); }

        public HardwareComponent.Selection selection() {
            return new HardwareComponent.Selection(category, null, null, modelName, brandName, specifications);
        }
    }
}
