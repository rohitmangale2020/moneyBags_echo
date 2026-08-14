package com.training.platform.gateway.config;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

@RestControllerAdvice
class GatewayExceptionHandler {

    @ExceptionHandler({ResourceAccessException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> serviceUnavailable(Exception exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "service_unavailable",
                        "message", "A required banking service is temporarily unavailable. Please retry shortly."
                ));
    }
}
