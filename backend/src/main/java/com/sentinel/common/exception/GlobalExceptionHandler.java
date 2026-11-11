package com.sentinel.common.exception;

import com.sentinel.common.dto.ApiResponse;
import com.sentinel.common.dto.ErrorDetails;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        ErrorDetails error = buildError(ex.getCode(), ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(error));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.error("Business exception: {} [{}]", ex.getMessage(), MDC.get("requestId"));
        ErrorDetails error = ErrorDetails.builder()
                .code(ex.getCode())
                .message(ex.getMessage())
                .details(ex.getDetails())
                .timestamp(Instant.now().toString())
                .requestId(MDC.get("requestId"))
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiResponse.error(error));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errors.put(e.getField(), e.getDefaultMessage()));
        ErrorDetails error = ErrorDetails.builder()
                .code("VALIDATION_ERROR")
                .message("Invalid request data")
                .details(errors)
                .timestamp(Instant.now().toString())
                .requestId(MDC.get("requestId"))
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUpload(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        ErrorDetails error = buildError("FILE_TOO_LARGE", "File size exceeds maximum allowed size", request);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
    }

    /**
     * A role check that rejects the caller is an answer, not a failure. Without
     * this the catch-all below turns it into a 500 and the client cannot tell a
     * permissions problem from an outage.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ErrorDetails error = buildError("FORBIDDEN",
                "Your role does not permit this action", request);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(error));
    }

    /**
     * Authorised, but not recently enough. Carries its own code so the console
     * can open the re-authentication prompt instead of showing a dead end.
     */
    @ExceptionHandler(StepUpRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleStepUp(StepUpRequiredException ex, HttpServletRequest request) {
        ErrorDetails error = buildError("STEP_UP_REQUIRED", ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(error));
    }

    /** A path variable that is not the declared type is a bad request, not a server fault. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        ErrorDetails error = buildError("INVALID_PARAMETER",
                "'" + ex.getName() + "' is not a valid value", request);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
    }

    /** Right path, wrong verb. 405 tells the caller that; a 500 sends them hunting an outage. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        ErrorDetails error = buildError("METHOD_NOT_ALLOWED",
                "This endpoint does not accept " + ex.getMethod(), request);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiResponse.error(error));
    }

    /** An unmapped path is a 404. The catch-all would otherwise report it as an outage. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        ErrorDetails error = buildError("NOT_FOUND", "No endpoint matches this path", request);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error [{}]: ", MDC.get("requestId"), ex);
        ErrorDetails error = buildError("INTERNAL_ERROR", "An unexpected error occurred", request);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(error));
    }

    private ErrorDetails buildError(String code, String message, HttpServletRequest request) {
        return ErrorDetails.of(code, message, MDC.get("requestId"), request.getRequestURI());
    }
}
