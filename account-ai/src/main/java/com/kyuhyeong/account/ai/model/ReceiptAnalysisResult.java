package com.kyuhyeong.account.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Claude Vision API가 영수증 이미지를 분석한 결과.
 *
 * <p>Claude가 반환한 JSON과 1:1 매핑되는 immutable record. Jackson이
 * snake_case → camelCase 자동 변환하도록 {@link JsonProperty} 명시.
 * 알 수 없는 필드는 무시하여 SDK 변경에 강건하게 대응.
 *
 * <p>이 객체는 백엔드 내부 표현이며, 사용자 컨펌 전까지는 DRAFT 상태의
 * 거래 레코드를 만드는 데만 사용된다. 사용자가 수정/확정한 최종 결과는
 * {@code transactions} 테이블에 저장되고, 이 원본 JSON은
 * {@code receipts.ocr_raw_json}에 그대로 보관된다 (감사/재학습용).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReceiptAnalysisResult(

        @JsonProperty("date")
        LocalDate date,

        @JsonProperty("time")
        LocalTime time,

        @JsonProperty("merchant")
        String merchant,

        @JsonProperty("merchant_type")
        String merchantType,

        @JsonProperty("category")
        String category,

        @JsonProperty("subcategory")
        String subcategory,

        @JsonProperty("total")
        BigDecimal total,

        @JsonProperty("payment_method")
        String paymentMethod,

        @JsonProperty("items")
        List<ReceiptItem> items,

        @JsonProperty("confidence")
        double confidence

) {

    /** 자동 확정 임계값 — 이 이상이면 사용자 컨펌 없이 바로 거래 생성. */
    public static final double AUTO_CONFIRM_THRESHOLD = 0.8;

    /** 수동 분류 임계값 — 이 미만이면 사용자가 수동으로 카테고리 선택. */
    public static final double MANUAL_CLASSIFY_THRESHOLD = 0.5;

    /**
     * 사용자 컨펌 없이 자동으로 거래 생성 가능한 신뢰도인지.
     * <p>호출자가 이 값으로 분기하여 DRAFT → CONFIRMED 자동 승격 여부 결정.
     */
    public boolean isAutoConfirmable() {
        return confidence >= AUTO_CONFIRM_THRESHOLD;
    }

    /**
     * 사용자가 수동으로 카테고리를 선택해야 할 만큼 신뢰도가 낮은지.
     * <p>true면 앱에서 "이 영수증은 카테고리를 직접 선택해주세요" UI 표시.
     */
    public boolean requiresManualClassification() {
        return confidence < MANUAL_CLASSIFY_THRESHOLD;
    }

    /**
     * 영수증의 개별 항목.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReceiptItem(
            @JsonProperty("name") String name,
            @JsonProperty("price") BigDecimal price,
            @JsonProperty("quantity") Integer quantity
    ) {}
}
