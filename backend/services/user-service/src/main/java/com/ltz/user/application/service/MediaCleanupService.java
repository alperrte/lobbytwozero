package com.ltz.user.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.user.application.port.MediaStorageException;
import com.ltz.user.application.port.MediaStoragePort;
import com.ltz.user.domain.entity.UserMedia;
import com.ltz.user.domain.enums.MediaStatus;
import com.ltz.user.infrastructure.persistence.repository.UserMediaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Silinmeyi bekleyen ({@code PENDING_DELETE}) görsellerin dosyalarını siler. Silme idempotenttir, bu yüzden aynı satır
 * birden çok turda ya da birden çok örnekte denense de zarar vermez. Dosyası silinen satır, yükleme sayacı penceresi
 * ({@link MediaService#UPLOAD_WINDOW}) dolana kadar tutulur çünkü saatlik yükleme sınırı bu satırlardan hesaplanır;
 * pencere dolunca satır da silinir. Dosyası silinemeyen satır sonraki turda yeniden denenir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaCleanupService {

    private static final int BATCH = 200;

    private final UserMediaRepository media;
    private final MediaStoragePort storage;
    private final Clock clock;

    /** @return bu turda veritabanından kaldırılan satır sayısı */
    @Transactional
    public int purge() {
        Instant cutoff = clock.instant().minus(MediaService.UPLOAD_WINDOW);
        List<UUID> removable = new ArrayList<>();
        for (UserMedia row : media.findByStatusOrderByCreatedAtAsc(MediaStatus.PENDING_DELETE, Limit.of(BATCH))) {
            try {
                MediaFiles.delete(storage, row);
            } catch (MediaStorageException _) {
                log.warn("Media cleanup will retry: mediaId={}", row.getId());
                continue;
            }
            if (row.getCreatedAt().isBefore(cutoff)) {
                removable.add(row.getId());
            }
        }
        if (!removable.isEmpty()) {
            media.deleteAllByIds(removable);
        }
        return removable.size();
    }
}
