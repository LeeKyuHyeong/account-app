package com.kyuhyeong.account.api.receipt;

import com.kyuhyeong.account.ai.model.ReceiptAnalysisResult;
import com.kyuhyeong.account.ai.service.ReceiptAnalysisService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 영수증 업로드 REST 엔드포인트 (Task 6 — account-ai 에서 이전).
 *
 * <p>인증/가구 식별은 JWT 클레임 단일 진입점이다 ({@code X-Household-Id} 헤더는
 * Task 5 에서 폐기됨). {@link Authentication#getPrincipal()} 가 user_id 를 반환하고,
 * 가구 ID 는 {@link com.kyuhyeong.account.core.tenant.HouseholdContext} 에 JWT 필터가
 * 이미 set 한 값을 {@link ReceiptIngestionService} 가 직접 조회한다.
 *
 * <p>흐름:
 * <ol>
 *   <li>multipart 이미지 업로드 + 검증 (size / content-type)</li>
 *   <li>{@link ReceiptIngestionService#ingest} 호출 — 디스크 저장 + AI 분석 + Receipt + DRAFT Transaction</li>
 *   <li>응답: 분석 결과 + receiptId/transactionId + 후속 처리 힌트</li>
 * </ol>
 */
@RestController
@RequestMapping(value = "/api/receipts", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ReceiptController {

    private static final Logger log = LoggerFactory.getLogger(ReceiptController.class);
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;  // 10MB

    private final ReceiptIngestionService ingestionService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnalyzeResponse> upload(
            @RequestPart("image") MultipartFile image,
            Authentication authentication) throws IOException {

        validateImage(image);
        Long userId = (Long) authentication.getPrincipal();

        log.info("Receipt upload: userId={}, filename='{}', size={} bytes, contentType={}",
                userId, image.getOriginalFilename(), image.getSize(), image.getContentType());

        ReceiptIngestionService.IngestResult ingested = ingestionService.ingest(image, userId);
        ReceiptAnalysisResult result = ingested.analysis();

        AnalyzeResponse body = new AnalyzeResponse(
                ingested.receiptId(),
                ingested.transactionId(),
                result,
                result.isAutoConfirmable(),
                result.requiresManualClassification()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("Image file is required");
        }
        if (image.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "Image too large: " + image.getSize() + " bytes (max " + MAX_FILE_SIZE_BYTES + ")");
        }
        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException(
                    "Unsupported content type: " + contentType + " (must be image/*)");
        }
    }

    /**
     * 업로드 응답.
     *
     * @param receiptId                     생성된 Receipt row id
     * @param transactionId                 자동 생성된 DRAFT Transaction id
     * @param analysis                      Claude 분석 결과
     * @param autoConfirmable               true → 클라이언트가 즉시 CONFIRMED 로 승격해도 됨 (≥ 0.8)
     * @param requiresManualClassification  true → 사용자에게 카테고리 수동 선택 요구 (< 0.5)
     */
    public record AnalyzeResponse(
            Long receiptId,
            Long transactionId,
            ReceiptAnalysisResult analysis,
            boolean autoConfirmable,
            boolean requiresManualClassification
    ) {}

    public record ErrorResponse(String code, String message) {}

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(ReceiptAnalysisService.AnalysisException.class)
    public ResponseEntity<ErrorResponse> handleAnalysisFailure(
            ReceiptAnalysisService.AnalysisException e) {
        log.warn("Receipt analysis failed", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("AI_ANALYSIS_FAILED", e.getMessage()));
    }
}
