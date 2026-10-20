package com.ltz.hardware.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HardwareSpecifications {
    @Column(name = "capacity_gb")
    private Integer capacityGb;
    @Column(name = "clock_mhz")
    private Integer clockMhz;
    @Column(name = "core_count")
    private Integer coreCount;
    @Column(name = "power_watts")
    private Integer powerWatts;
    @Column(name = "memory_type", length = 32)
    private String memoryType;
    @Column(length = 32)
    private String socket;
    @Column(name = "interface_type", length = 32)
    private String interfaceType;
    @Column(name = "form_factor", length = 32)
    private String formFactor;

    public HardwareSpecifications(Integer capacityGb, Integer clockMhz, Integer coreCount, Integer powerWatts,
                                  String memoryType, String socket, String interfaceType, String formFactor) {
        this.capacityGb = range(capacityGb, 1048576);
        this.clockMhz = range(clockMhz, 20000);
        this.coreCount = range(coreCount, 1024);
        this.powerWatts = range(powerWatts, 10000);
        this.memoryType = text(memoryType);
        this.socket = text(socket);
        this.interfaceType = text(interfaceType);
        this.formFactor = text(formFactor);
    }

    private static Integer range(Integer value, int max) {
        if (value != null && (value < 1 || value > max)) {
            throw new IllegalArgumentException("Geçersiz teknik değer");
        }
        return value;
    }

    private static String text(String value) {
        if (value == null) return null;
        if (value.isBlank() || value.length() > 32
                || value.codePoints().anyMatch(c -> Character.isISOControl(c) || c == '<' || c == '>')) {
            throw new IllegalArgumentException("Geçersiz teknik metin");
        }
        return value.strip();
    }
}
