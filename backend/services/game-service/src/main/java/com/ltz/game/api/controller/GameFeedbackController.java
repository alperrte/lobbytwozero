package com.ltz.game.api.controller;
import com.ltz.game.api.dto.request.FeedbackRequest;
import com.ltz.game.api.dto.response.*;
import com.ltz.game.application.port.Actor;
import com.ltz.game.application.service.GameFeedbackService;
import com.ltz.game.domain.enums.*;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/game/feedback") @RequiredArgsConstructor
public class GameFeedbackController {
    private final GameFeedbackService feedback;
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public FeedbackResponse create(Actor actor,@Valid @RequestBody FeedbackRequest request) {
        return feedback.create(actor,request.gameId(),request.type(),request.title(),request.body());
    }
    @GetMapping("/{id}")
    public FeedbackResponse own(Actor actor,@PathVariable UUID id) { return feedback.own(actor,id); }
    @GetMapping
    public FeedbackPageResponse list(Actor actor,@RequestParam(required=false) UUID gameId,
            @RequestParam(required=false) FeedbackType type,@RequestParam(required=false) FeedbackStatus status,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return feedback.ownList(actor,gameId,type,status,page,size);
    }
}
