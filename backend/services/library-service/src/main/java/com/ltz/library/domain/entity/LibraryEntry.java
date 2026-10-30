package com.ltz.library.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Satırın varlığı koleksiyon üyeliğidir; oyun sahipliğini kanıtlamaz. */
@Entity
@Table(name = "library_entries")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class LibraryEntry {
    @Id private UUID id;
    @Column(nullable = false) private UUID userId;
    @Column(nullable = false) private UUID gameId;
    @Column(nullable = false) private boolean inCollection;
    @Column(nullable = false) private boolean favorite;
    @Column(nullable = false) private boolean completed;
    @Column(nullable = false) private Instant addedAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;
}
