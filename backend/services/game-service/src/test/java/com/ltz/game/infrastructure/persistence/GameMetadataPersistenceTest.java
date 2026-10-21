package com.ltz.game.infrastructure.persistence;
import com.ltz.game.PostgresTestSupport;
import com.ltz.game.application.service.GameMetadataService;
import com.ltz.game.domain.entity.*;
import com.ltz.game.domain.enums.*;
import com.ltz.game.domain.exception.GameNotFoundException;
import com.ltz.game.infrastructure.persistence.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @Transactional
class GameMetadataPersistenceTest extends PostgresTestSupport {
    @Autowired GameRepository games;
    @Autowired CatalogTermRepository terms;
    @Autowired GameMetadataService service;
    @Autowired EntityManager em;
    @Test void separateTermsAndOptionalMetadataSurviveRoundTrip() {
        Game game = game("Metadata test");
        for (TermKind kind : TermKind.values()) {
            CatalogTerm term = new CatalogTerm(); term.setKind(kind); term.setCode("same-code");
            term.setNameTr(kind.name()); game.getTerms().add(terms.save(term));
        }
        game.getDevelopers().add("Test Studio"); game.getPublishers().add("Test Publisher");
        GameMedia media = new GameMedia(); media.setGame(game); media.setType(MediaType.COVER);
        media.setUrl("https://example.com/cover.png"); game.getMedia().add(media);
        GameSystemRequirement requirement = new GameSystemRequirement(); requirement.setGame(game);
        requirement.setLevel(RequirementLevel.MINIMUM); requirement.setPlatform("windows");
        requirement.setRawText("Provider supplied text"); game.getSystemRequirements().add(requirement);
        UUID id = games.saveAndFlush(game).getId(); em.clear();
        var result = service.get(id);
        assertEquals(1, result.genres().size()); assertEquals(1, result.categories().size());
        assertEquals(1, result.tags().size()); assertEquals(1, result.platforms().size());
        assertNotEquals(result.genres().getFirst().id(), result.categories().getFirst().id());
        assertEquals("Test Studio", result.developers().getFirst());
        assertEquals("Test Publisher", result.publishers().getFirst());
        assertEquals(MediaType.COVER, result.media().getFirst().type());
        assertNull(result.systemRequirements().getFirst().ramMb());
        assertNull(result.systemRequirements().getFirst().cpu());
        game = games.findById(id).orElseThrow(); game.getMedia().clear();
        game.getSystemRequirements().clear(); games.saveAndFlush(game); em.clear();
        assertTrue(service.get(id).media().isEmpty());
        assertTrue(service.get(id).systemRequirements().isEmpty());
    }
    @Test void sparseGameAndMissingGame() {
        UUID id = games.saveAndFlush(game("Index only")).getId(); em.clear();
        var result = service.get(id); assertTrue(result.genres().isEmpty()); assertTrue(result.media().isEmpty());
        UUID missingId = UUID.randomUUID();
        assertThrows(GameNotFoundException.class, () -> service.get(missingId));
    }
    @Test void duplicateTermIsRejectedByDatabase() {
        CatalogTerm first = new CatalogTerm(); first.setKind(TermKind.TAG); first.setCode("duplicate");
        terms.saveAndFlush(first);
        CatalogTerm duplicate = new CatalogTerm(); duplicate.setKind(TermKind.TAG); duplicate.setCode("duplicate");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> terms.saveAndFlush(duplicate));
    }
    private Game game(String title) { Game g=new Game();g.setTitle(title);g.setNormalizedTitle(title.toLowerCase(java.util.Locale.ROOT));return g; }
}
