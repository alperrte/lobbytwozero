package com.ltz.game.api.controller;
import com.ltz.game.api.dto.request.FeedbackStatusRequest;
import com.ltz.game.api.dto.response.FeedbackPageResponse;
import com.ltz.game.api.dto.response.FeedbackResponse;
import com.ltz.game.application.port.Actor;
import com.ltz.game.application.service.GameFeedbackService;
import com.ltz.game.domain.enums.FeedbackStatus;
import com.ltz.game.domain.enums.FeedbackType;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
@RestController @RequestMapping("/api/v1/game/developer/games/{gameId}/feedback") @RequiredArgsConstructor
public class DeveloperFeedbackController {
    private final GameFeedbackService feedback;
    @GetMapping
    public FeedbackPageResponse list(Actor actor,@PathVariable UUID gameId,
            @RequestParam(required=false) FeedbackType type,@RequestParam(required=false) FeedbackStatus status,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return feedback.developerList(actor,gameId,type,status,page,size);
    }
    @GetMapping("/{id}")
    public FeedbackResponse get(Actor actor,@PathVariable UUID gameId,@PathVariable UUID id) {
        return feedback.developer(actor,gameId,id);
    }
    @PatchMapping("/{id}/status")
    public FeedbackResponse status(Actor actor,@PathVariable UUID gameId,@PathVariable UUID id,
            @Valid @RequestBody FeedbackStatusRequest request) {
        return feedback.transition(actor,gameId,id,request.status(),request.version());
    }
}
