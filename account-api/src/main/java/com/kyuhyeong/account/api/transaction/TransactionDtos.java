package com.kyuhyeong.account.api.transaction;

import com.kyuhyeong.account.core.entity.Transaction;
import com.kyuhyeong.account.core.enums.CategoryType;
import com.kyuhyeong.account.core.enums.TransactionStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 거래 API 의 입출력 DTO 모음. */
public final class TransactionDtos {

    private TransactionDtos() {
    }

    /** 거래 응답 DTO — 가구 컨텍스트에서 안전한 정보만 노출 (다른 가구 데이터는 격리 필터로 차단됨). */
    public record TransactionResponse(
            Long id,
            Long categoryId,
            String categoryName,
            CategoryType categoryType,
            BigDecimal amount,
            LocalDateTime occurredAt,
            String merchant,
            String paymentMethod,
            String memo,
            Long receiptId,
            BigDecimal confidence,
            TransactionStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static TransactionResponse from(Transaction t) {
            return new TransactionResponse(
                    t.getId(),
                    t.getCategory().getId(),
                    t.getCategory().getName(),
                    t.getCategory().getType(),
                    t.getAmount(),
                    t.getOccurredAt(),
                    t.getMerchant(),
                    t.getPaymentMethod(),
                    t.getMemo(),
                    t.getReceipt() == null ? null : t.getReceipt().getId(),
                    t.getConfidence(),
                    t.getStatus(),
                    t.getCreatedAt(),
                    t.getUpdatedAt()
            );
        }
    }

    /** 거래 생성 (수동 입력) 요청. */
    public record CreateTransactionRequest(
            @NotNull Long categoryId,
            @NotNull @DecimalMin(value = "0.01", message = "amount must be positive")
            @Digits(integer = 13, fraction = 2) BigDecimal amount,
            @NotNull LocalDateTime occurredAt,
            @Size(max = 200) String merchant,
            @Size(max = 50) String paymentMethod,
            @Size(max = 500) String memo
    ) {
    }

    /** 페이지 응답 — Spring Data {@link Page} 를 클라이언트 친화 형식으로 노출. */
    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
        public static <T> PageResponse<T> of(Page<T> page) {
            return new PageResponse<>(
                    page.getContent(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages(),
                    page.hasNext()
            );
        }
    }
}
