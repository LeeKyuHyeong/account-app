package com.kyuhyeong.account.api.config;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 전역 예외 → JSON 에러 응답 변환.
 *
 * <p>거래 / 카테고리 / 영수증 컨트롤러가 공통으로 사용. 영수증 컨트롤러는 자체
 * {@code @ExceptionHandler} 로 {@code AnalysisException} 등 도메인 예외를 처리하므로
 * 본 advice 보다 우선한다 (Spring 의 컨트롤러 로컬 핸들러가 advice 보다 먼저 매칭).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ErrorResponse(String code, String message, Map<String, String> fieldErrors) {
        public static ErrorResponse of(String code, String message) {
            return new ErrorResponse(code, message, null);
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.badRequest().body(
                new ErrorResponse("VALIDATION_FAILED", "Request body validation failed", fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException e) {
        return ResponseEntity.badRequest().body(
                ErrorResponse.of("CONSTRAINT_VIOLATION", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse.of("INVALID_REQUEST", e.getMessage()));
    }
}
