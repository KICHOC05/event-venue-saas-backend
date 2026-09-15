package com.example.demo.common.exception;

import com.example.demo.auth.exception.InvalidCredentialsException;
import com.example.demo.auth.ratelimit.LoginRateLimitExceededException;
import com.example.demo.event.exception.ScheduleConflictException;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

        @ExceptionHandler(InvalidCredentialsException.class)
        public ResponseEntity<?> handleInvalidCredentials(InvalidCredentialsException ex) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(Map.of(
                                                "timestamp", LocalDateTime.now(),
                                                "status", 401,
                                                "error", "Unauthorized",
                                                "code", "INVALID_CREDENTIALS",
                                                "message", ex.getMessage()));
        }

        @ExceptionHandler(LoginRateLimitExceededException.class)
        public ResponseEntity<?> handleLoginRateLimit(LoginRateLimitExceededException ex) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                                .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.getRetryAfterSeconds()))
                                .body(Map.of(
                                                "timestamp", LocalDateTime.now(),
                                                "status", 429,
                                                "error", "Too Many Requests",
                                                "code", "LOGIN_RATE_LIMITED",
                                                "message", "Demasiados intentos de inicio de sesión. Inténtalo de nuevo más tarde."));
        }

        // Manejar conflictos de horario de eventos
        @ExceptionHandler(ScheduleConflictException.class)
        public ResponseEntity<?> handleScheduleConflict(ScheduleConflictException ex) {
                log.warn("Schedule conflict: {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(Map.of(
                                                "timestamp", LocalDateTime.now(),
                                                "status", 409,
                                                "error", "Conflict",
                                                "code", "EVENT_TIME_CONFLICT",
                                                "message", ex.getMessage()));
        }

        @ExceptionHandler(EntityNotFoundException.class)
        public ResponseEntity<?> handleNotFound(EntityNotFoundException ex) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(Map.of(
                                                "timestamp", LocalDateTime.now(),
                                                "status", 404,
                                                "error", "Not Found",
                                                "message", ex.getMessage()));
        }

        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<?> handleBadRequest(IllegalArgumentException ex) {
                return ResponseEntity.badRequest()
                                .body(Map.of(
                                                "timestamp", LocalDateTime.now(),
                                                "status", 400,
                                                "error", "Bad Request",
                                                "message", ex.getMessage()));
        }

        @ExceptionHandler(IllegalStateException.class)
        public ResponseEntity<?> handleIllegalState(IllegalStateException ex) {
                return ResponseEntity.badRequest()
                                .body(Map.of(
                                                "timestamp", LocalDateTime.now(),
                                                "status", 400,
                                                "error", "Bad Request",
                                                "message", ex.getMessage()));
        }

        @ExceptionHandler(SecurityException.class)
        public ResponseEntity<?> handleSecurity(SecurityException ex) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(Map.of(
                                                "timestamp", LocalDateTime.now(),
                                                "status", 403,
                                                "error", "Forbidden",
                                                "message", ex.getMessage()));
        }

        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<?> handleAccessDenied(AccessDeniedException ex) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(Map.of(
                                                "timestamp", LocalDateTime.now(),
                                                "status", 403,
                                                "error", "Forbidden",
                                                "message", "Acceso denegado"));
        }

        @ExceptionHandler(MaxUploadSizeExceededException.class)
        public ResponseEntity<?> handleMaxSize(MaxUploadSizeExceededException ex) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(Map.of(
                                                "timestamp", LocalDateTime.now(),
                                                "status", 400,
                                                "error", "Bad Request",
                                                "message", "El archivo excede el tamaño máximo permitido de 2MB"));
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
                String message = ex.getBindingResult()
                                .getFieldErrors()
                                .stream()
                                .findFirst()
                                .map(error -> error.getDefaultMessage())
                                .orElse("Validation error");

                return ResponseEntity.badRequest().body(Map.of(
                                "timestamp", LocalDateTime.now(),
                                "status", 400,
                                "error", "Bad Request",
                                "message", message));
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<?> handleGeneral(Exception ex) {
                log.error("Error no manejado: ", ex);

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(Map.of(
                                                "timestamp", LocalDateTime.now(),
                                                "status", 500,
                                                "error", "Internal Server Error",
                                                "message", "No fue posible cargar la información."));
        }
}
