package com.ltz.game.infrastructure.persistence.repository;
import com.ltz.game.domain.entity.CatalogTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface CatalogTermRepository extends JpaRepository<CatalogTerm, UUID> { }
