package com.ltz.user.application.service;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ltz.user.infrastructure.persistence.repository.UserProfileRepository;

import lombok.RequiredArgsConstructor;

/**
 * Diğer servislerin hesap kullanılabilirliği sorgusu. Tek sorgu (N+1 yok), fail-closed: yalnız profili olan ve hesabı
 * {@code ACTIVE} olan kullanıcı {@code ACTIVE} döner. Durum, Auth'tan olayla eşitlenen kopyadır: olay kadar günceldir,
 * anlık değildir.
 */
@Service
@RequiredArgsConstructor
public class UserStatusService {

    /** Kullanılabilir hesap. */
    public static final String ACTIVE = "ACTIVE";
    /** Kullanılamaz ya da bilinmeyen hesap. */
    public static final String UNAVAILABLE = "UNAVAILABLE";

    /** Tek hesabın sonucu. */
    public record Status(UUID userId, boolean active) {

        public String value() {
            return active ? ACTIVE : UNAVAILABLE;
        }
    }

    private final UserProfileRepository profiles;

    /** İstek sırasıyla, yinelenenler bir kez. */
    @Transactional(readOnly = true)
    public List<Status> lookup(List<UUID> userIds) {
        Set<UUID> ordered = new LinkedHashSet<>(userIds);
        Set<UUID> active = new HashSet<>(profiles.findActiveIds(ordered));
        return ordered.stream().map(id -> new Status(id, active.contains(id))).toList();
    }
}
