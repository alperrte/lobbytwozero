package com.ltz.game.infrastructure.persistence.repository;
import com.ltz.game.domain.entity.CatalogTerm;
import com.ltz.game.domain.enums.TermKind;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface CatalogTermRepository extends JpaRepository<CatalogTerm, UUID> {
    Optional<CatalogTerm> findByKindAndCode(TermKind kind, String code);

    /** Herkese açık terim listesi satırı: yalnız ACTIVE + MAIN_GAME oyunlara bağlı terimler, oyun sayısıyla. */
    interface TermCount { String getCode(); String getNameTr(); String getNameEn(); long getGameCount(); }

    @org.springframework.data.jpa.repository.Query("""
        select t.code as code, t.nameTr as nameTr, t.nameEn as nameEn, count(g) as gameCount
        from Game g join g.terms t
        where t.kind = :kind and g.status = com.ltz.game.domain.enums.GameStatus.ACTIVE
        and g.gameType = com.ltz.game.domain.enums.GameType.MAIN_GAME
        group by t.id, t.code, t.nameTr, t.nameEn
        order by count(g) desc, t.code asc
        """)
    java.util.List<TermCount> publicTerms(@org.springframework.data.repository.query.Param("kind") TermKind kind,
            org.springframework.data.domain.Pageable pageable);
}
