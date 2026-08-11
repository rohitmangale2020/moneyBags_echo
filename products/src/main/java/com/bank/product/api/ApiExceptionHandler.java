package com.bank.product.api;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;
@RestControllerAdvice public class ApiExceptionHandler {
    @ExceptionHandler({EntityNotFoundException.class}) ResponseEntity<?> notFound(RuntimeException e) { return error(HttpStatus.NOT_FOUND, e.getMessage()); }
    @ExceptionHandler({IllegalArgumentException.class}) ResponseEntity<?> badRequest(RuntimeException e) { return error(HttpStatus.BAD_REQUEST, e.getMessage()); }
    private ResponseEntity<?> error(HttpStatus status, String message) { return ResponseEntity.status(status).body(Map.of("timestamp", Instant.now(), "status", status.value(), "message", message)); }
}
