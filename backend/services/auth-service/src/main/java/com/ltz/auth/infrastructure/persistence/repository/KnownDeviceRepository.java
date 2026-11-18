package com.ltz.auth.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.ltz.auth.domain.entity.KnownDevice;

public interface KnownDeviceRepository extends JpaRepository<KnownDevice, UUID> {

    long countByAccountId(UUID accountId);

    boolean existsByAccountIdAndNotifiedAtAfter(UUID accountId, Instant after);

    /** Bilinen cihazsa son görülme zamanını yeniler; etkilenen satır sayısı 0 ise cihaz yenidir. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update KnownDevice d set d.lastSeenAt = :now where d.accountId = :accountId and d.deviceHash = :hash")
    int touch(UUID accountId, String hash, Instant now);

    /** Eşzamanlı iki girişte yalnız biri ekler; eklenen satır sayısını döner (0 = başka istek ekledi). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            insert into known_devices (id, account_id, device_hash, first_seen_at, last_seen_at)
            values (:id, :accountId, :hash, :now, :now)
            on conflict (account_id, device_hash) do nothing
            """)
    int insertIfAbsent(UUID id, UUID accountId, String hash, Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update KnownDevice d set d.notifiedAt = :now where d.accountId = :accountId and d.deviceHash = :hash")
    int markNotified(UUID accountId, String hash, Instant now);

    /** Hesap başına en çok {@code keep} kayıt kalır; en az yakın zamanda görülenler silinir. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            delete from known_devices where account_id = :accountId and id not in (
                select id from known_devices where account_id = :accountId
                order by last_seen_at desc, id limit :keep)
            """)
    int pruneBeyond(UUID accountId, int keep);
}
