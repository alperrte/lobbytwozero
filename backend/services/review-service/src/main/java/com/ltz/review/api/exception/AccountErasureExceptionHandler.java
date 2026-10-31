package com.ltz.review.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.ltz.review.domain.exception.ReviewException;
import org.springframework.dao.DataIntegrityViolationException;

@RestControllerAdvice
public class AccountErasureExceptionHandler extends org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler {
    @ExceptionHandler(ReviewException.class)
    public ProblemDetail review(ReviewException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(exception.status()), exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflict() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Kayıt mevcut durumla çakışıyor.");
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail forbidden() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Bu silme işlemi için yetkiniz yok.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Beklenmeyen sunucu hatası.");
    }
}
