package top.cywu.magicops.console.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import top.cywu.magicops.console.service.ApprovalSeparationException;
import top.cywu.magicops.governance.lifecycle.IllegalStateTransitionException;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理。
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Not Found", "message", ex.getMessage(), "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalTransition(IllegalStateTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Conflict", "message", ex.getMessage(),
                        "from", ex.getFrom().name(), "to", ex.getTo().name(),
                        "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(ApprovalSeparationException.class)
    public ResponseEntity<Map<String, Object>> handleApprovalSeparation(ApprovalSeparationException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Forbidden", "message", ex.getMessage(),
                        "timestamp", Instant.now().toString()));
    }

    /**
     * 安全异常必须交还 Spring Security 过滤链处理（403/401），
     * 不能被通用处理器吞掉变成 500。
     */
    @ExceptionHandler({org.springframework.security.access.AccessDeniedException.class,
            org.springframework.security.core.AuthenticationException.class})
    public void handleSecurityException(RuntimeException ex) throws RuntimeException {
        throw ex;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Bad Request", "message", message, "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Not Found", "message", ex.getMessage(), "timestamp", Instant.now().toString()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("unexpected_error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal Server Error", "timestamp", Instant.now().toString()));
    }
}
