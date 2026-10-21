package com.ltz.game.api.exception;
import com.ltz.game.domain.exception.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
public class GameExceptionHandler extends org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler {
    @ExceptionHandler(com.ltz.game.domain.exception.CatalogRatingUnavailableException.class)
    public ProblemDetail ratingUnavailable() {
        var problem=ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"LTZ puanı filtresi ve sıralaması Review Service entegrasyonu bekliyor.");
        problem.setProperty("code","RATING_INTEGRATION_PENDING");
        return problem;
    }
    @ExceptionHandler(GameNotFoundException.class)
    public ProblemDetail notFound() { return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Oyun bulunamadı."); }

    @ExceptionHandler(ContentNotFoundException.class)
    public ProblemDetail contentNotFound(ContentNotFoundException error) {
        return problem(HttpStatus.NOT_FOUND, "Kaynak bulunamadı.", error.code());
    }

    @ExceptionHandler(AccessForbiddenException.class)
    public ProblemDetail forbidden() { return problem(HttpStatus.FORBIDDEN, "Bu işlem için yetkiniz yok.", "ACCESS_FORBIDDEN"); }

    @ExceptionHandler(GameConflictException.class)
    public ProblemDetail conflict(GameConflictException error) { return problem(HttpStatus.CONFLICT, "İstek mevcut durumla çakışıyor.", error.code()); }

    /** Eşzamanlı değişiklik ve benzersizlik çakışmaları; ayrıntı (SQL, kısıt adı) istemciye verilmez. */
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ProblemDetail concurrentModification() {
        return problem(HttpStatus.CONFLICT, "İstek mevcut durumla çakışıyor.", "CONCURRENT_MODIFICATION");
    }

    @ExceptionHandler(InvalidGameRequestException.class)
    public ProblemDetail invalid(InvalidGameRequestException error) { return problem(HttpStatus.BAD_REQUEST, "Geçersiz istek.", error.code()); }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalidInput() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Geçersiz istek.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Beklenmeyen sunucu hatası.");
    }

    private static ProblemDetail problem(HttpStatus status, String detail, String code) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
