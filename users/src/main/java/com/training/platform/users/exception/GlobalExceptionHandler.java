package com.training.platform.users.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(UserNotFoundException exception) {
        log.warn("User not found: {}", exception.getMessage());
        return problem(HttpStatus.NOT_FOUND, "User not found", exception.getMessage(), "user-not-found");
    }

    @ExceptionHandler({UserConflictException.class, DataIntegrityViolationException.class})
    ResponseEntity<ProblemDetail> handleConflict(Exception exception) {
        log.warn("User conflict: {}", exception.getMessage());
        String detail = exception instanceof UserConflictException
                ? exception.getMessage()
                : "The username or email is already in use";
        return problem(HttpStatus.CONFLICT, "User conflict", detail, "user-conflict");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        log.warn("Request validation failed");
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "One or more request fields are invalid"
        );
        detail.setTitle("Validation failed");
        detail.setType(URI.create("urn:moneybags:problem:validation-failed"));
        detail.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadableRequest(HttpMessageNotReadableException exception) {
        log.warn("Malformed request received");
        return problem(
                HttpStatus.BAD_REQUEST,
                "Malformed request",
                "The request body is missing or contains an invalid value",
                "malformed-request"
        );
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String title,
            String detail,
            String type
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("urn:moneybags:problem:" + type));
        return ResponseEntity.status(status).body(problem);
    }
}
