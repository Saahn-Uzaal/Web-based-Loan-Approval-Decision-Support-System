package com.loanapproval.dss.shared;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final Pattern SIZE_PATTERN = Pattern.compile("size must be between (\\d+) and (\\d+)");
    private static final Pattern MIN_PATTERN = Pattern.compile("must be greater than or equal to (.+)");
    private static final Pattern MAX_PATTERN = Pattern.compile("must be less than or equal to (.+)");

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(
        ResponseStatusException ex,
        HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return ResponseEntity.status(status).body(buildBody(status, ex.getReason(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
        MethodArgumentNotValidException ex,
        HttpServletRequest request
    ) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(fieldError -> localizeValidationMessage(fieldError.getDefaultMessage()))
            .orElse("Dữ liệu không hợp lệ");
        return ResponseEntity.badRequest().body(buildBody(HttpStatus.BAD_REQUEST, message, request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
        AccessDeniedException ex,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(buildBody(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập", request.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
        DataIntegrityViolationException ex,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(buildBody(HttpStatus.CONFLICT, "Xung đột dữ liệu", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
        Exception ex,
        HttpServletRequest request
    ) {
        log.error("Unhandled exception at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(buildBody(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi máy chủ nội bộ", request.getRequestURI()));
    }

    private Map<String, Object> buildBody(HttpStatus status, String message, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", localizedError(status));
        body.put("message", message);
        body.put("path", path);
        return body;
    }

    private String localizedError(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Yêu cầu không hợp lệ";
            case UNAUTHORIZED -> "Chưa xác thực";
            case FORBIDDEN -> "Bị từ chối truy cập";
            case NOT_FOUND -> "Không tìm thấy";
            case CONFLICT -> "Xung đột dữ liệu";
            case TOO_MANY_REQUESTS -> "Quá nhiều yêu cầu";
            case INTERNAL_SERVER_ERROR -> "Lỗi máy chủ nội bộ";
            default -> status.getReasonPhrase();
        };
    }

    private String localizeValidationMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return "Dữ liệu không hợp lệ";
        }

        String message = rawMessage.trim();
        if ("must not be blank".equals(message) || "must not be empty".equals(message) || "must not be null".equals(message)) {
            return "Dữ liệu nhập không được để trống.";
        }
        if ("must be a well-formed email address".equals(message)) {
            return "Email không đúng định dạng.";
        }

        Matcher sizeMatcher = SIZE_PATTERN.matcher(message);
        if (sizeMatcher.matches()) {
            int min = Integer.parseInt(sizeMatcher.group(1));
            int max = Integer.parseInt(sizeMatcher.group(2));
            if (min == 0) {
                return "Dữ liệu nhập không được vượt quá " + max + " ký tự.";
            }
            return "Dữ liệu nhập phải dài từ " + min + " đến " + max + " ký tự.";
        }

        Matcher minMatcher = MIN_PATTERN.matcher(message);
        if (minMatcher.matches()) {
            return "Dữ liệu nhập phải lớn hơn hoặc bằng " + minMatcher.group(1) + ".";
        }

        Matcher maxMatcher = MAX_PATTERN.matcher(message);
        if (maxMatcher.matches()) {
            return "Dữ liệu nhập phải nhỏ hơn hoặc bằng " + maxMatcher.group(1) + ".";
        }

        return message;
    }
}
