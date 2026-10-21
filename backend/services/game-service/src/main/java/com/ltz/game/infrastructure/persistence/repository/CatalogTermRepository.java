package com.ltz.game.infrastructure.persistence.repository;
import com.ltz.game.domain.entity.CatalogTerm;
import com.ltz.game.domain.enums.TermKind;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface CatalogTermRepository extends JpaRepository<CatalogTerm, UUID> {
    Optional<CatalogTerm> findByKindAndCode(TermKind kind, String code);
}
