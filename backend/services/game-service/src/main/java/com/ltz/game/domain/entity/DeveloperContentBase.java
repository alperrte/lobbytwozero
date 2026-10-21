package com.ltz.game.domain.entity;
import com.ltz.game.domain.enums.ContentStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
/** Geliştirici içeriğinde ortak sahiplik ve moderasyon alanları. */
@MappedSuperclass @Getter @Setter
public abstract class DeveloperContentBase {
    @Column(nullable=false) private UUID authorId;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=16) private ContentStatus status = ContentStatus.VISIBLE;
    private UUID moderatedBy;
    @Column(length=1000) private String moderationReason;
    private Instant moderatedAt;
    @Column(nullable=false) private Instant createdAt = Instant.now();
    @Column(nullable=false) private Instant updatedAt = Instant.now();
    @Version private long version;
    public boolean isVisible() { return status == ContentStatus.VISIBLE; }
}
