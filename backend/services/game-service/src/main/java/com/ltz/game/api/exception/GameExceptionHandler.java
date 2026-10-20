package com.ltz.game.api.exception;
import com.ltz.game.domain.exception.GameNotFoundException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
public class GameExceptionHandler extends org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler {
    @ExceptionHandler(GameNotFoundException.class)
    public ProblemDetail notFound() { return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Oyun bulunamadı."); }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalidInput() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Geçersiz istek.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Beklenmeyen sunucu hatası.");
    }
}
