package com.ltz.hardware.domain.entity;

import com.ltz.hardware.domain.enums.DeviceType;
import com.ltz.hardware.domain.enums.ComponentCategory;
import com.ltz.hardware.domain.enums.HardwarePrivacyField;
import com.ltz.hardware.domain.enums.HardwareVisibility;
import com.ltz.hardware.domain.exception.HardwareConflictException;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.Map;
import java.util.EnumMap;

@Entity
@Table(name = "hardware_profiles")
public class HardwareProfile {
    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;
    @Column(nullable = false, length = 100)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 32)
    private DeviceType deviceType;
    @Column(nullable = false, length = 32, updatable = false)
    private String source;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private HardwareVisibility visibility = HardwareVisibility.PRIVATE;
    @ElementCollection
    @CollectionTable(name = "hardware_profile_field_visibility", joinColumns = @JoinColumn(name = "profile_id"))
    @MapKeyColumn(name = "field_name", length = 32)
    @MapKeyEnumerated(EnumType.STRING)
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 16)
    private Map<HardwarePrivacyField, HardwareVisibility> fieldVisibilities = new EnumMap<>(HardwarePrivacyField.class);
    @ElementCollection
    @CollectionTable(name = "hardware_profile_category_visibility", joinColumns = @JoinColumn(name = "profile_id"))
    @MapKeyColumn(name = "category", length = 32)
    @MapKeyEnumerated(EnumType.STRING)
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 16)
    private Map<ComponentCategory, HardwareVisibility> categoryVisibilities = new EnumMap<>(ComponentCategory.class);
    @Version
    private long version;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HardwareProfile() { }

    public HardwareProfile(UUID userId, String name, DeviceType deviceType) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId);
        this.name = validName(name);
        this.deviceType = Objects.requireNonNull(deviceType);
        this.source = "MANUAL";
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    private static String validName(String name) {
        if (name == null || name.isBlank() || name.length() > 100
                || name.codePoints().anyMatch(c -> Character.isISOControl(c) || c == '<' || c == '>')) {
            throw new IllegalArgumentException("Geçersiz profil adı");
        }
        return name.strip();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public DeviceType getDeviceType() { return deviceType; }
    public String getSource() { return source; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public HardwareVisibility getVisibility() { return visibility; }
    public Map<HardwarePrivacyField, HardwareVisibility> getFieldVisibilities() { return Map.copyOf(fieldVisibilities); }
    public Map<ComponentCategory, HardwareVisibility> getCategoryVisibilities() { return Map.copyOf(categoryVisibilities); }
    public HardwareVisibility visibilityOf(HardwarePrivacyField field) {
        return fieldVisibilities.getOrDefault(field, HardwareVisibility.PRIVATE);
    }
    public HardwareVisibility visibilityOf(ComponentCategory category) {
        return categoryVisibilities.getOrDefault(category, HardwareVisibility.PRIVATE);
    }

    public void configurePrivacy(HardwareVisibility visibility,
            Map<HardwarePrivacyField, HardwareVisibility> fields,
            Map<ComponentCategory, HardwareVisibility> categories) {
        this.visibility = Objects.requireNonNull(visibility);
        fieldVisibilities.clear();
        fieldVisibilities.putAll(Map.copyOf(fields));
        categoryVisibilities.clear();
        categoryVisibilities.putAll(Map.copyOf(categories));
        touch();
    }

    public void requireVersion(long expected) {
        if (version != expected) throw new HardwareConflictException();
    }

    public void touch() {
        var now = Instant.now();
        updatedAt = now.isAfter(updatedAt) ? now : updatedAt.plusNanos(1000);
    }

    public void update(String name, DeviceType deviceType) {
        this.name = validName(name);
        this.deviceType = Objects.requireNonNull(deviceType);
        touch();
    }
}
