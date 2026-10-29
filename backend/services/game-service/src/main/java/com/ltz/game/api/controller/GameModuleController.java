package com.ltz.game.api.controller;
import com.ltz.game.api.dto.response.GameModuleResponse;
import com.ltz.game.application.service.GameModuleService;
import com.ltz.game.domain.enums.GameModuleType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
@RestController @RequestMapping("/api/v1/game/games") @RequiredArgsConstructor
public class GameModuleController {
    private final GameModuleService modules;
    @GetMapping("/{gameId}/modules/{type}")
    public GameModuleResponse read(@PathVariable UUID gameId,@PathVariable GameModuleType type,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return modules.read(gameId,type,page,size);
    }
}
