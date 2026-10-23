package com.ltz.game.application.service;

import com.ltz.game.api.dto.response.*;
import com.ltz.game.application.mapper.GameViewMapper;
import com.ltz.game.domain.entity.Game;
import com.ltz.game.domain.exception.GameNotFoundException;
import com.ltz.game.infrastructure.persistence.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class GameViewService {
    private final GameRepository games;
    private final GameViewMapper mapper;
    public GameCardResponse card(UUID id) { return mapper.card(active(id)); }
    public GameDetailResponse detail(UUID id) { return mapper.detail(active(id)); }
    private Game active(UUID id) {
        return games.findById(id).filter(Game::isActive).orElseThrow(GameNotFoundException::new);
    }
}
