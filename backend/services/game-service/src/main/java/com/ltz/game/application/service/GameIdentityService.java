package com.ltz.game.application.service;

import com.ltz.game.domain.entity.*;
import com.ltz.game.domain.enums.*;
import com.ltz.game.domain.exception.*;
import com.ltz.game.infrastructure.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** HTTP'ye açık değildir. Doğrulayan operatör ve eşleme kanıtı zorunludur;
 * JWT/rol kullanan yönetim giriş noktaları Faz 4'te bu servisi çağırmalıdır.
 */
@Service @RequiredArgsConstructor
public class GameIdentityService {
    private final GameRepository games;
    private final GameExternalReferenceRepository references;
    private final GameIdentityAuditRepository audits;
    private final GameProviderSyncStateRepository states;

    public record Verification(UUID actorId, String evidence) {
        public Verification {
            if (actorId==null || evidence==null || evidence.strip().length()<8 || evidence.length()>1000)
                throw new IllegalArgumentException("Doğrulayan operatör ve eşleme kanıtı zorunlu.");
            evidence=evidence.strip();
        }
    }

    @Transactional
    public UUID reassign(String provider, String externalId, UUID expectedGameId, UUID targetGameId, Verification verification) {
        validate(provider,externalId,expectedGameId,verification);
        if (targetGameId==null) throw new IllegalArgumentException("Hedef oyun zorunlu.");
        var ref=lockedReference(provider,externalId);
        UUID current=ref.getGame().getId();
        if (current.equals(targetGameId)) return current;
        if (!current.equals(expectedGameId)) throw new ReferenceMappingConflictException();
        var target=games.lockGame(targetGameId).filter(Game::isActive).orElseThrow(GameNotFoundException::new);
        ref.setGame(target);
        audit(ref,current,targetGameId,"MAP",verification);
        return targetGameId;
    }

    @Transactional
    public UUID split(String provider, String externalId, UUID expectedGameId, Verification verification) {
        validate(provider,externalId,expectedGameId,verification);
        var ref=lockedReference(provider,externalId);
        var source=ref.getGame();
        if (!source.getId().equals(expectedGameId)) throw new ReferenceMappingConflictException();
        Game separated=new Game();
        separated.setTitle(source.getTitle());
        separated.setNormalizedTitle(source.getNormalizedTitle());
        separated.setOrigin(GameOrigin.EXTERNAL);
        // Kaynağa ait zengin metadata yeni eşlemeye doğrulanmadan kopyalanmaz.
        separated=games.save(separated);
        ref.setGame(separated);
        audit(ref,expectedGameId,separated.getId(),"SPLIT",verification);
        return separated.getId();
    }

    /** HTTP girişi: dış servis referansları doğrulanamadığı için yalnız kesin no-op kabul edilir.
     * Operatör kanıtı dış referans güvenliği sözleşmesinin yerine geçmez.
     */
    @Transactional
    public UUID safeReassign(String provider, String externalId, UUID expectedGameId, UUID targetGameId, Verification verification) {
        validateSafe(provider,externalId,expectedGameId,verification);
        if (targetGameId==null) throw new IllegalArgumentException("Hedef oyun zorunlu.");
        var ref=lockedReference(provider,externalId);
        requireExpected(ref,expectedGameId);
        games.lockGame(targetGameId).filter(Game::isActive).orElseThrow(GameNotFoundException::new);
        if (expectedGameId.equals(targetGameId)) return targetGameId;
        throw new GameConflictException("FOREIGN_REFERENCE_SAFETY_UNVERIFIED");
    }

    /** Kaynak UUID'nin korunması, kullanıcı verisinin oyun anlamının korunduğunu kanıtlamaz. */
    @Transactional
    public UUID safeSplit(String provider, String externalId, UUID expectedGameId, Verification verification) {
        validateSafe(provider,externalId,expectedGameId,verification);
        var ref=lockedReference(provider,externalId);
        requireExpected(ref,expectedGameId);
        throw new GameConflictException("FOREIGN_REFERENCE_SAFETY_UNVERIFIED");
    }

    private void requireExpected(GameExternalReference ref, UUID expected) {
        if (!ref.getGame().getId().equals(expected)) throw new GameConflictException("REFERENCE_MAPPING_CONFLICT");
    }

    private void validateSafe(String provider, String externalId, UUID expected, Verification verification) {
        validate(provider,externalId,expected,verification);
        // Mevcut katalog kilidi yalnız Steam akışlarıyla ortak güvence sağlar.
        if (!"STEAM".equals(provider) || !externalId.matches("[1-9]\\d{0,9}")
                || Long.parseLong(externalId)>4294967295L)
            throw new IllegalArgumentException("Geçersiz Steam referansı.");
    }

    private GameExternalReference lockedReference(String provider, String externalId) {
        lockCatalog();
        return references.lockReference(provider,externalId).orElseThrow(GameNotFoundException::new);
    }

    private void lockCatalog() {
        // Faz 1'in tek Steam indeksiyle eşleme/ayırma işlemlerinin yarışmasını önler.
        states.lockProvider("STEAM").orElseThrow();
    }
    private void audit(GameExternalReference ref,UUID from,UUID to,String action,Verification verification) {
        GameIdentityAudit audit=new GameIdentityAudit();
        audit.setReferenceId(ref.getId());audit.setFromGameId(from);audit.setToGameId(to);
        audit.setActorId(verification.actorId());audit.setEvidence(verification.evidence());audit.setAction(action);
        audits.save(audit);
    }
    private void validate(String provider,String externalId,UUID expected,Verification verification) {
        if(provider==null || !provider.matches("[A-Z][A-Z0-9_]{0,31}") || externalId==null
                || externalId.isBlank() || externalId.length()>128 || expected==null || verification==null)
            throw new IllegalArgumentException("Geçersiz doğrulanmış eşleme isteği.");
    }
}
