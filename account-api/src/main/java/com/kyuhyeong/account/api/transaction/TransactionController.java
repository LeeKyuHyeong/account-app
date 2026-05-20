package com.kyuhyeong.account.api.transaction;

import com.kyuhyeong.account.api.transaction.TransactionDtos.CreateTransactionRequest;
import com.kyuhyeong.account.api.transaction.TransactionDtos.PageResponse;
import com.kyuhyeong.account.api.transaction.TransactionDtos.TransactionResponse;
import com.kyuhyeong.account.api.transaction.TransactionService.TransactionListQuery;
import com.kyuhyeong.account.core.enums.CategoryType;
import com.kyuhyeong.account.core.enums.TransactionStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 거래 REST 엔드포인트.
 *
 * <ul>
 *   <li>{@code GET  /api/transactions} — 필터 + 페이징 (from/to/categoryId/type/status)</li>
 *   <li>{@code POST /api/transactions} — 수동 입력 (CONFIRMED 로 즉시 확정)</li>
 * </ul>
 *
 * <p>가구 격리는 JWT 클레임에서 추출된 {@code HouseholdContext} 가 Hibernate filter 로
 * 자동 적용. 본 컨트롤러는 별도 가구 ID 처리 없음.
 */
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    public PageResponse<TransactionResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) CategoryType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return transactionService.list(new TransactionListQuery(
                from, to, categoryId, type, status, page, size));
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @Valid @RequestBody CreateTransactionRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        TransactionResponse created = transactionService.create(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
