package com.ltz.game.application.service;
import com.ltz.game.api.dto.response.GameMetadataResponse;
import com.ltz.game.application.mapper.GameMetadataMapper;
import com.ltz.game.domain.exception.GameNotFoundException;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Service @RequiredArgsConstructor
public class GameMetadataService {
    private final GameRepository games;
    private final GameMetadataMapper mapper;
    @Transactional(readOnly=true)
    public GameMetadataResponse get(UUID gameId) {
        return mapper.toResponse(games.findById(gameId).filter(com.ltz.game.domain.entity.Game::isActive).orElseThrow(GameNotFoundException::new));
    }
}
