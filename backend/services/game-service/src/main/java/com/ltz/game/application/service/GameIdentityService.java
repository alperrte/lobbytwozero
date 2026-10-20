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
        lockCatalog();
        var ref=references.lockReference(provider,externalId).orElseThrow(GameNotFoundException::new);
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
        lockCatalog();
        var ref=references.lockReference(provider,externalId).orElseThrow(GameNotFoundException::new);
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
