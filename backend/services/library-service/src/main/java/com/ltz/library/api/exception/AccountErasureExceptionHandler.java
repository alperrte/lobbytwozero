package com.ltz.library.api.exception;

import org.springframework.http.HttpStatus;
import com.ltz.library.domain.exception.CatalogUnavailableException;
import com.ltz.library.domain.exception.GameUnavailableException;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AccountErasureExceptionHandler extends org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler {
    @ExceptionHandler(GameUnavailableException.class)
    public ProblemDetail gameUnavailable() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Oyun bulunamadı veya koleksiyona eklenemiyor.");
    }

    @ExceptionHandler(CatalogUnavailableException.class)
    public ProblemDetail catalogUnavailable() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Oyun doğrulama servisi geçici olarak kullanılamıyor.");
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail forbidden() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Bu işlem için yetkiniz yok.");
    }

    @ExceptionHandler(com.ltz.library.domain.exception.LibraryPrivacyUnavailableException.class)
    public ProblemDetail privacyUnavailable() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Erişim kararı geçici olarak alınamıyor.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Beklenmeyen sunucu hatası.");
    }
}
