package com.ziyadsamhaoui.messagingchatservice.exception;

import com.ziyadsamhaoui.messagingchatservice.dto.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ChatServiceException.class)
    public ResponseEntity<ApiErrorResponse> handleDomainException(ChatServiceException exception,
            HttpServletRequest request) {

        if (exception.getStatus().is5xxServerError()) {
            log.error("Chat service failure on {} {}", request.getMethod(), request.getRequestURI(), exception);
        } else {
            log.debug("Rejected {} {} with {}", request.getMethod(), request.getRequestURI(), exception.getCode());
        }

        return ResponseEntity.status(exception.getStatus()).body(ApiErrorResponse.of(exception.getStatus().value(),
                exception.getCode(), exception.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        return ResponseEntity.badRequest().body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_FAILED", "The request payload is invalid", request.getRequestURI(), fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception,
            HttpServletRequest request) {

        return ResponseEntity.badRequest().body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST.value(),
                "MALFORMED_REQUEST_BODY", "The request body could not be parsed", request.getRequestURI()));
    }

    @ExceptionHandler({ MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class })
    public ResponseEntity<ApiErrorResponse> handleInvalidParameter(Exception exception,
            HttpServletRequest request) {

        return ResponseEntity.badRequest().body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST.value(),
                "INVALID_REQUEST_PARAMETER", "A request parameter is missing or malformed",
                request.getRequestURI()));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateKey(DuplicateKeyException exception,
            HttpServletRequest request) {

        log.debug("Unique constraint violation on {} {}", request.getMethod(), request.getRequestURI(), exception);

        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.of(HttpStatus.CONFLICT.value(),
                "DUPLICATE_RESOURCE", "The resource already exists", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception exception,
            HttpServletRequest request) {

        log.error("Unhandled failure on {} {}", request.getMethod(), request.getRequestURI(), exception);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR", "The request could not be processed",
                request.getRequestURI()));
    }
}
