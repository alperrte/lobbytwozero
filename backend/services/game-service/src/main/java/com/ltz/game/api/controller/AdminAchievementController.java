package com.ltz.game.api.controller;
import com.ltz.game.api.dto.request.AchievementRequest;
import com.ltz.game.api.dto.response.GameModuleResponse;
import com.ltz.game.application.port.Actor;
import com.ltz.game.application.service.GameModuleService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/game/admin/games/{gameId}/achievements") @RequiredArgsConstructor
public class AdminAchievementController {
    private final GameModuleService modules;
    @GetMapping
    public GameModuleResponse list(Actor actor,@PathVariable UUID gameId,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) { return modules.management(actor,gameId,page,size); }
    @PutMapping("/{code}")
    public GameModuleResponse.Achievement put(Actor actor,@PathVariable UUID gameId,@PathVariable String code,
            @Valid @RequestBody AchievementRequest request) {
        return modules.put(actor,gameId,code,request.toEdit());
    }
}
