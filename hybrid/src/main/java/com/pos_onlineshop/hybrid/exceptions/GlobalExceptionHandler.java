package com.pos_onlineshop.hybrid.exceptions;

import com.pos_onlineshop.hybrid.dtos.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * The audit's P2 finding: there was no global exception handler at all, so any exception that
 * escaped a controller uncaught fell through to Spring Boot's default error handling - an
 * inconsistent, framework-shaped body that, for a genuinely unexpected exception (an NPE, a
 * DataIntegrityViolationException naming a raw column/constraint, and similar), can include the
 * exception's own message and class name in the response.
 *
 * This deliberately does NOT change how most controllers already behave: the overwhelming
 * majority of controllers in this codebase already wrap their own try/catch and shape their own
 * response body (frequently including a deliberately-written, safe business message via
 * e.getMessage(), e.g. "Username already exists: X" or "Accounting period already closed" -
 * exceptions thrown specifically so the caller sees that exact text). Those are untouched here;
 * this advice only fires for whatever a controller lets propagate uncaught. Its two jobs are:
 * (1) give the app one consistent error shape for cases with no local handling (bean validation
 * failures, ResourceNotFoundException/InsufficientInventoryException left uncaught, security
 * denials), and (2) make sure a truly unexpected exception can never leak its raw message to the
 * client - it is logged in full server-side and the client gets a generic message instead.
 *
 * Reuses dtos.ErrorResponse, which InventoryTransferController already builds by hand in every
 * one of its own catch blocks for exactly this {timestamp, status, error, message, path} shape -
 * this is that same shape applied globally instead of duplicated per controller.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException e, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler(InsufficientInventoryException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientInventory(InsufficientInventoryException e, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, e.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message.isBlank() ? "Validation failed" : message, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        // Never relay e.getMessage() here - Spring Security's own denial messages can include
        // the specific expression or authority that failed, which is an authorization
        // implementation detail an unauthorized caller shouldn't see.
        return build(HttpStatus.FORBIDDEN, "You do not have permission to perform this action", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
