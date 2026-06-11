package com.commercetools.signature.web;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Fail-closed error handling: anything that goes wrong while evaluating a cart becomes a
 * <strong>400</strong> with a commercetools-shaped {@code errors} array, so commercetools rejects
 * the cart/order operation rather than letting a narcotic cart slip through unflagged. Stack traces
 * and internals are never returned to the caller (see security.md, Pattern 5).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, Object>> handleBadInput(Exception ex) {
        log.warn("Rejecting cart operation (bad input): {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, "InvalidInput", "Invalid extension request");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        // Fail-closed: an internal error must not silently allow the cart through.
        log.error("Unexpected error evaluating cart; failing closed", ex);
        return error(HttpStatus.BAD_REQUEST, "General",
                "Signature-required validation is temporarily unavailable");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("errors", List.of(Map.of("code", code, "message", message))));
    }
}
