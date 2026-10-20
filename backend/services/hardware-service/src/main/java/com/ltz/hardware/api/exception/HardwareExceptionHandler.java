package com.ltz.hardware.api.exception;

import com.ltz.hardware.domain.exception.HardwareConflictException;
import com.ltz.hardware.domain.exception.HardwareNotFoundException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class HardwareExceptionHandler extends ResponseEntityExceptionHandler {
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problem(status.value(), "INVALID_REQUEST", "İstek doğrulanamadı.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Object> invalidInput(IllegalArgumentException exception) {
        return problem(400, "INVALID_REQUEST", "İstek doğrulanamadı.");
    }

    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class,
            HardwareConflictException.class})
    ResponseEntity<Object> conflict(Exception exception) {
        return problem(409, "DATA_CONFLICT", "Kayıt değişti veya veri kuralıyla çakışıyor.");
    }

    @ExceptionHandler(HardwareNotFoundException.class)
    ResponseEntity<Object> notFound(HardwareNotFoundException exception) {
        return problem(404, "HARDWARE_NOT_FOUND", "İstenen kaynak bulunamadı.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception exception) {
        return problem(500, "INTERNAL_ERROR", "İşlem tamamlanamadı.");
    }

    private static ResponseEntity<Object> problem(int status, String code, String detail) {
        var body = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
        body.setProperty("code", code);
        return ResponseEntity.status(status).header("Cache-Control", "private, no-store").body(body);
    }
}
