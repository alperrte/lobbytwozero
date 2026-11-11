package com.ltz.mail.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ltz.mail.domain.entity.MailMessage;

public interface MailMessageRepository extends JpaRepository<MailMessage, UUID> {

    /**
     * Kaydı kuyruğa ekler. Aynı {@code idempotencyKey} zaten varsa hiçbir şey yapmaz; eşzamanlı iki istekte ikincisi
     * birincinin işlemi bitene kadar bekler ve 0 döner (MAIL-14).
     *
     * @return 1 ise kayıt yeni, 0 ise anahtar daha önce kullanılmış
     */
    @Modifying
    @Query(value = """
            INSERT INTO mail_messages (id, idempotency_key, request_hash, type, recipient_user_id, recipient_email,
                                       recipient_masked, locale, payload_encrypted, payload_key_version, status,
                                       attempts, next_attempt_at, expires_at, created_at, updated_at, version)
            VALUES (:id, :idempotencyKey, :requestHash, :type, :userId, :email, :masked, CAST(:locale AS varchar),
                    :payload, CAST(:keyVersion AS smallint), 'PENDING', 0, :now, :expiresAt, :now, :now, 0)
            ON CONFLICT (idempotency_key) DO NOTHING""", nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("idempotencyKey") UUID idempotencyKey,
            @Param("requestHash") String requestHash, @Param("type") String type, @Param("userId") UUID userId,
            @Param("email") String email, @Param("masked") String masked, @Param("locale") String locale,
            @Param("payload") byte[] payload, @Param("keyVersion") int keyVersion, @Param("now") Instant now,
            @Param("expiresAt") Instant expiresAt);

    /**
     * Platform kapısı kaydı: alıcı adresi YAZILMAZ (gönderim anında çözülür), maskeli adres sabit bir yer tutucudur.
     * Dönüş değeri {@link #insertIfAbsent} ile aynıdır.
     */
    @Modifying
    @Query(value = """
            INSERT INTO mail_messages (id, idempotency_key, request_hash, type, recipient_user_id, recipient_email,
                                       recipient_masked, locale, caller, payload_encrypted, payload_key_version, status,
                                       attempts, next_attempt_at, expires_at, created_at, updated_at, version)
            VALUES (:id, :idempotencyKey, :requestHash, :type, :userId, NULL, '***', CAST(:locale AS varchar), :caller,
                    :payload, CAST(:keyVersion AS smallint), 'PENDING', 0, :now, :expiresAt, :now, :now, 0)
            ON CONFLICT (idempotency_key) DO NOTHING""", nativeQuery = true)
    int insertPlatformIfAbsent(@Param("id") UUID id, @Param("idempotencyKey") UUID idempotencyKey,
            @Param("requestHash") String requestHash, @Param("type") String type, @Param("userId") UUID userId,
            @Param("locale") String locale, @Param("caller") String caller, @Param("payload") byte[] payload,
            @Param("keyVersion") int keyVersion, @Param("now") Instant now, @Param("expiresAt") Instant expiresAt);

    /** Hız sınırı: bu çağıranın bu hesaba {@code since}'ten beri yarattığı kayıt sayısı. */
    @Query("SELECT COUNT(m) FROM MailMessage m WHERE m.caller = :caller AND m.recipientUserId = :userId "
            + "AND m.createdAt > :since")
    long countForAccountSince(@Param("caller") String caller, @Param("userId") UUID userId,
            @Param("since") Instant since);

    /** Hız sınırı: bu çağıranın {@code since}'ten beri yarattığı toplam kayıt sayısı. */
    @Query("SELECT COUNT(m) FROM MailMessage m WHERE m.caller = :caller AND m.createdAt > :since")
    long countForCallerSince(@Param("caller") String caller, @Param("since") Instant since);

    @Query("SELECT m.requestHash FROM MailMessage m WHERE m.idempotencyKey = :key")
    Optional<String> findRequestHashByIdempotencyKey(@Param("key") UUID key);

    /**
     * Gönderilmeye hazır kayıtları kilitleyerek seçer: zamanı gelmiş {@code PENDING} ve kiralama süresi dolmuş
     * {@code PROCESSING} (çöken işleyici). Başka bir örneğin kilitlediği satırlar atlanır.
     */
    @Query(value = """
            SELECT id FROM mail_messages
            WHERE (status = 'PENDING' AND next_attempt_at <= :now)
               OR (status = 'PROCESSING' AND lease_until <= :now)
            ORDER BY next_attempt_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED""", nativeQuery = true)
    List<UUID> lockDueIds(@Param("now") Instant now, @Param("limit") int limit);

    /** Hesap silme: kullanıcının tüm kayıtları; deneme geçmişi {@code ON DELETE CASCADE} ile gider. */
    @Modifying
    @Query("DELETE FROM MailMessage m WHERE m.recipientUserId = :userId")
    int deleteAllForUser(@Param("userId") UUID userId);

    /** Saklama süresi dolmuş terminal kayıtları en çok {@code limit} adet siler. */
    @Modifying
    @Query(value = """
            DELETE FROM mail_messages
            WHERE id IN (SELECT id FROM mail_messages
                         WHERE status IN ('SENT', 'FAILED') AND updated_at < :cutoff
                         ORDER BY updated_at
                         LIMIT :limit)""", nativeQuery = true)
    int purgeTerminalBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
