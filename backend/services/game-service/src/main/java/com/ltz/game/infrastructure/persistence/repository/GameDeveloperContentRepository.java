package com.ltz.game.infrastructure.persistence.repository;

import com.ltz.game.domain.entity.GameDeveloperContent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameDeveloperContentRepository extends JpaRepository<GameDeveloperContent, UUID> { }
