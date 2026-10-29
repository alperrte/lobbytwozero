package com.ltz.game.application.service;

import com.ltz.game.PostgresTestSupport;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.entity.GameExternalReference;
import com.ltz.game.domain.enums.GameOrigin;
import com.ltz.game.domain.enums.MetadataCompleteness;
import com.ltz.game.domain.exception.ReferenceMappingConflictException;
import com.ltz.game.infrastructure.persistence.repository.GameExternalReferenceRepository;
import com.ltz.game.infrastructure.persistence.repository.GameIdentityAuditRepository;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest @Transactional
class GameIdentityServiceTest extends PostgresTestSupport {
    @Autowired GameIdentityService identity;
    @Autowired GameRepository games;
    @Autowired GameExternalReferenceRepository refs;
    @Autowired GameIdentityAuditRepository audits;
    @Autowired JdbcTemplate jdbc;
    final GameIdentityService.Verification proof=new GameIdentityService.Verification(UUID.randomUUID(),"Test: verified release and store identifiers");
    @Test void verifiedMappingKeepsIdsDoesNotMergeByNameAndIsIdempotent() {
        Game source=game("Same name",GameOrigin.EXTERNAL);Game target=game("Different verified title",GameOrigin.INTERNAL);
        var ref=reference(source,"STEAM","12345");long before=audits.count();
        assertEquals(target.getId(),identity.reassign("STEAM","12345",source.getId(),target.getId(),proof));refs.flush();
        assertEquals(target.getId(),refs.findById(ref.getId()).orElseThrow().getGame().getId());
        assertEquals(GameOrigin.INTERNAL,games.findById(target.getId()).orElseThrow().getOrigin());
        assertTrue(games.existsById(source.getId()));assertEquals(before+1,audits.count());
        identity.reassign("STEAM","12345",source.getId(),target.getId(),proof);assertEquals(before+1,audits.count());
    }
    @Test void splitCreatesNewExternalIdentityAndKeepsHistory() {
        Game source=game("Split source",GameOrigin.INTERNAL);source.setMetadataCompleteness(MetadataCompleteness.FULL);
        var ref=reference(source,"STEAM","12346");UUID split=identity.split("STEAM","12346",source.getId(),proof);refs.flush();
        assertNotEquals(source.getId(),split);assertEquals(split,refs.findById(ref.getId()).orElseThrow().getGame().getId());
        var separated=games.findById(split).orElseThrow();assertEquals(GameOrigin.EXTERNAL,separated.getOrigin());
        assertEquals(MetadataCompleteness.INDEX_ONLY,separated.getMetadataCompleteness());assertTrue(separated.getTerms().isEmpty());
        assertTrue(games.existsById(source.getId()));
        UUID sourceId = source.getId();
        assertThrows(ReferenceMappingConflictException.class,()->identity.split("STEAM","12346",sourceId,proof));
    }
    @Test void staleMappingAndMissingVerificationAreRejected() {
        Game source=game("Source",GameOrigin.EXTERNAL);Game target=game("Target",GameOrigin.INTERNAL);reference(source,"STEAM","12347");
        UUID sourceId = source.getId(); UUID targetId = target.getId(); UUID staleId = UUID.randomUUID();
        assertThrows(ReferenceMappingConflictException.class,()->identity.reassign("STEAM","12347",staleId,targetId,proof));
        assertThrows(IllegalArgumentException.class,()->new GameIdentityService.Verification(null,"verified"));
        assertThrows(IllegalArgumentException.class,()->identity.reassign("STEAM","12347",sourceId,targetId,null));
    }
    @Test void sameSteamAppIdIsUniqueAtDatabaseLevel() {
        Game source=game("A",GameOrigin.EXTERNAL);Game target=game("A",GameOrigin.EXTERNAL);reference(source,"STEAM","12348");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->reference(target,"STEAM","12348"));
    }
    @Test void leadingZeroSteamAliasIsRejectedAtDatabaseLevel() {
        Game source=game("A",GameOrigin.EXTERNAL);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->reference(source,"STEAM","012348"));
    }
    @Test void differentProvidersAreNotMergedByExternalIdOrTitle() {
        Game first=game("A",GameOrigin.EXTERNAL);Game second=game("A",GameOrigin.EXTERNAL);
        reference(first,"STEAM","12349");reference(second,"OTHER","12349");
        assertNotEquals(refs.findByProviderAndExternalId("STEAM","12349").orElseThrow().getGame().getId(),
                refs.findByProviderAndExternalId("OTHER","12349").orElseThrow().getGame().getId());
    }
    @Test void auditCannotBeUpdatedOrDeleted() {
        Game source=game("A",GameOrigin.EXTERNAL);Game target=game("B",GameOrigin.INTERNAL);reference(source,"STEAM","12350");
        identity.reassign("STEAM","12350",source.getId(),target.getId(),proof);audits.flush();
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("update game_identity_audits set evidence='tampered' where from_game_id=?",source.getId()));
    }
    @Test void auditCannotBeDeleted() {
        Game source=game("A",GameOrigin.EXTERNAL);Game target=game("B",GameOrigin.INTERNAL);reference(source,"STEAM","12351");
        identity.reassign("STEAM","12351",source.getId(),target.getId(),proof);audits.flush();
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("delete from game_identity_audits where from_game_id=?",source.getId()));
    }
    private Game game(String title,GameOrigin origin) { Game game=new Game();game.setTitle(title);game.setNormalizedTitle(title.toLowerCase(java.util.Locale.ROOT));game.setOrigin(origin);return games.saveAndFlush(game); }
    private GameExternalReference reference(Game game,String provider,String externalId) { GameExternalReference ref=new GameExternalReference();ref.setGame(game);ref.setProvider(provider);ref.setExternalId(externalId);return refs.saveAndFlush(ref); }
}
