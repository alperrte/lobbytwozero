package com.ltz.game.api.controller;

import com.ltz.game.api.dto.request.RecommendationMetadataBatchRequest;
import com.ltz.game.api.dto.response.RecommendationMetadataBatchResponse;
import com.ltz.game.application.service.RecommendationMetadataBatchService;
import com.ltz.game.domain.exception.InvalidGameRequestException;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.PayloadTooLargeException;

@RestController
@RequiredArgsConstructor
public class RecommendationMetadataBatchController {
    public static final String PATH = "/api/v1/game/games/recommendation-metadata/batch";
    private static final int MAX_BODY_BYTES = 8192;
    private final RecommendationMetadataBatchService service;

    @PostMapping(path = PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RecommendationMetadataBatchResponse> batch(InputStream input) {
        byte[] body;
        try {
            body = input.readNBytes(MAX_BODY_BYTES + 1);
        } catch (IOException _) {
            throw new InvalidGameRequestException("INVALID_GAME_IDS");
        }
        if (body.length > MAX_BODY_BYTES) {
            throw new PayloadTooLargeException(null);
        }
        var request = RecommendationMetadataBatchRequest.parse(body);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.read(request.gameIds()));
    }

    @ExceptionHandler({DataAccessResourceFailureException.class, TransientDataAccessException.class,
            CannotCreateTransactionException.class, TransactionTimedOutException.class})
    ProblemDetail unavailable() {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Oyun metadatası geçici olarak alınamadı.");
        problem.setProperty("code", "GAME_METADATA_UNAVAILABLE");
        return problem;
    }
}
