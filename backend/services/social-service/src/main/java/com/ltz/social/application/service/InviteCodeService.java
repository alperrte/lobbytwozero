package com.ltz.social.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ltz.social.domain.entity.InviteCode;
import com.ltz.social.domain.exception.SocialErrorCode;
import com.ltz.social.domain.exception.SocialException;
import com.ltz.social.infrastructure.config.InviteCodeProperties;
import com.ltz.social.infrastructure.persistence.repository.InviteCodeRepository;


/**
 * Arkadaşlık davet kodu (SOC-14). Kullanıcının en fazla bir etkin kodu olur. Kodu giren kişi, kod sahibine
 * normal arkadaşlık isteği gönderir: kendine istek, zaten arkadaş, bekleyen istek ve istek sınırı kuralları
 * {@link FriendRequestService#send} içindedir ve bu akışta da aynen geçerlidir (engelleme kuralı SOC-09 ile
 * aynı yerde uygulanacağından bu akışı da kapsar).
 */
@Service
public class InviteCodeService {

    /** Rastgele kod çakışması ya da eşzamanlı üretim yarışında yeniden deneme sayısı. */
    private static final int MAX_ISSUE_ATTEMPTS = 5;

    private final InviteCodeRepository inviteCodes;
    private final FriendRequestService friendRequests;
    private final InviteCodeGenerator generator;
    private final InviteCodeProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public InviteCodeService(InviteCodeRepository inviteCodes, FriendRequestService friendRequests,
                             InviteCodeGenerator generator, InviteCodeProperties properties,
                             PlatformTransactionManager transactionManager, Clock clock) {
        this.inviteCodes = inviteCodes;
        this.friendRequests = friendRequests;
        this.generator = generator;
        this.properties = properties;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Kullanıcının etkin ve kullanılabilir kodu; yoksa 404. */
    @Transactional(readOnly = true)
    public InviteCodeView find(UUID userId) {
        return inviteCodes.findByUserIdAndRevokedAtIsNull(userId)
                .filter(code -> code.isUsableAt(now()))
                .map(InviteCodeService::toView)
                .orElseThrow(() -> new SocialException(SocialErrorCode.INVITE_CODE_NOT_FOUND));
    }

    /**
     * Etkin ve kullanılabilir kod varsa onu döner; yoksa (hiç yok, süresi dolmuş, kullanım hakkı bitmiş) eskisini
     * iptal edip yenisini üretir. Eşzamanlı iki çağrıda kaybeden, kazananın kodunu bulur (yeniden deneme).
     */
    public InviteCodeResult getOrCreate(UUID userId) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transactions.execute(status -> getOrCreateOnce(userId));
            } catch (DataIntegrityViolationException exception) {
                if (attempt >= MAX_ISSUE_ATTEMPTS) {
                    throw exception;
                }
            }
        }
    }

    /** Etkin kodu iptal eder. Kod yoksa da başarılıdır (idempotent). */
    @Transactional
    public void revoke(UUID userId) {
        inviteCodes.findByUserIdAndRevokedAtIsNull(userId).ifPresent(code -> code.revoke(now()));
    }

    /**
     * Kodu girerek kod sahibine arkadaşlık isteği gönderir. Bilinmeyen, biçimsiz, iptal edilmiş, süresi dolmuş ve
     * kullanım hakkı bitmiş kodlar aynı 404 ile döner (kodun varlığı sızdırılmaz). Kullanım sayacı yalnız istek
     * gerçekten oluştuğunda artar; istek reddedilirse işlem geri alınır.
     */
    @Transactional
    public FriendRequestView redeem(UUID userId, String rawCode) {
        String normalized = InviteCodeGenerator.normalize(rawCode);
        Instant now = now();
        InviteCode invite = Optional.of(normalized)
                .filter(InviteCodeGenerator::isWellFormed)
                .flatMap(inviteCodes::findByCodeForUpdate)
                .filter(code -> code.isUsableAt(now))
                .orElseThrow(() -> new SocialException(SocialErrorCode.INVITE_CODE_NOT_FOUND));
        FriendRequestView request = friendRequests.send(userId, invite.getUserId());
        invite.recordUse(now);
        return request;
    }

    private InviteCodeResult getOrCreateOnce(UUID userId) {
        Instant now = now();
        Optional<InviteCode> active = inviteCodes.findByUserIdAndRevokedAtIsNull(userId);
        if (active.isPresent() && active.get().isUsableAt(now)) {
            return new InviteCodeResult(toView(active.get()), false);
        }
        if (active.isPresent()) {
            active.get().revoke(now);
            // Kısmi tekil indeks (kullanıcı başına tek etkin kod) için eski kayıt yenisinden önce yazılmalı.
            inviteCodes.saveAndFlush(active.get());
        }
        InviteCode issued = InviteCode.issue(userId, generator.next(), now, properties.ttl(), properties.maxUses());
        return new InviteCodeResult(toView(inviteCodes.saveAndFlush(issued)), true);
    }

    private static InviteCodeView toView(InviteCode code) {
        return new InviteCodeView(code.getCode(), code.getCreatedAt(), code.getExpiresAt(), code.getMaxUses(),
                code.getUsedCount());
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
