package com.kyuhyeong.account.api.transaction;

import com.kyuhyeong.account.api.transaction.TransactionDtos.CreateTransactionRequest;
import com.kyuhyeong.account.api.transaction.TransactionDtos.PageResponse;
import com.kyuhyeong.account.api.transaction.TransactionDtos.TransactionResponse;
import com.kyuhyeong.account.core.entity.Category;
import com.kyuhyeong.account.core.entity.Household;
import com.kyuhyeong.account.core.entity.Transaction;
import com.kyuhyeong.account.core.entity.User;
import com.kyuhyeong.account.core.enums.CategoryType;
import com.kyuhyeong.account.core.enums.TransactionStatus;
import com.kyuhyeong.account.core.repository.CategoryRepository;
import com.kyuhyeong.account.core.repository.HouseholdRepository;
import com.kyuhyeong.account.core.repository.TransactionRepository;
import com.kyuhyeong.account.core.repository.UserRepository;
import com.kyuhyeong.account.core.tenant.HouseholdContext;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 거래 목록 조회 + 수동 생성.
 *
 * <p>가구 격리는 Hibernate {@code householdFilter} 가 모든 쿼리에 자동 적용된다.
 * 본 서비스는 {@code @Transactional} 메서드 안에서만 동작하면 격리 보장 — 별도 where
 * 절을 명시하지 않는다 (격리 검증은 [[task-4]] 의 통합 테스트가 담당).
 */
@Service
@RequiredArgsConstructor
public class TransactionService {

    /** 페이지 크기 상한 — 단일 응답이 너무 커지는 것을 막는다. */
    private static final int MAX_PAGE_SIZE = 100;

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final HouseholdRepository householdRepository;

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> list(TransactionListQuery query) {
        int safeSize = Math.min(Math.max(query.size(), 1), MAX_PAGE_SIZE);
        int safePage = Math.max(query.page(), 0);

        // occurred_at DESC, id DESC — 같은 시각 거래의 안정적 정렬을 위해 id 까지 포함.
        Sort sort = Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));
        PageRequest pageRequest = PageRequest.of(safePage, safeSize, sort);

        Specification<Transaction> spec = buildSpec(query);
        Page<Transaction> page = transactionRepository.findAll(spec, pageRequest);
        return PageResponse.of(page.map(TransactionResponse::from));
    }

    @Transactional
    public TransactionResponse create(CreateTransactionRequest request, Long authorUserId) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Category not found or not in current household: " + request.categoryId()));

        Long householdId = HouseholdContext.get();
        Household household = householdRepository.getReferenceById(householdId);
        User author = userRepository.getReferenceById(authorUserId);

        Transaction tx = Transaction.builder()
                .household(household)
                .author(author)
                .category(category)
                .amount(request.amount())
                .occurredAt(request.occurredAt())
                .merchant(request.merchant())
                .paymentMethod(request.paymentMethod())
                .memo(request.memo())
                // 수동 입력은 사용자가 직접 작성한 값이므로 컨펌 상태로 즉시 확정.
                .status(TransactionStatus.CONFIRMED)
                .build();
        tx = transactionRepository.save(tx);
        return TransactionResponse.from(tx);
    }

    private Specification<Transaction> buildSpec(TransactionListQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // soft-delete 제외
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (query.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), query.from().atStartOfDay()));
            }
            if (query.to() != null) {
                LocalDateTime exclusiveEnd = query.to().plusDays(1).atStartOfDay();
                predicates.add(cb.lessThan(root.get("occurredAt"), exclusiveEnd));
            }
            if (query.categoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), query.categoryId()));
            }
            if (query.type() != null) {
                predicates.add(cb.equal(root.get("category").get("type"), query.type()));
            }
            if (query.status() != null) {
                predicates.add(cb.equal(root.get("status"), query.status()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** 목록 조회 query 객체 — 컨트롤러에서 RequestParam 으로 받은 값을 그대로 전달. */
    public record TransactionListQuery(
            LocalDate from,
            LocalDate to,
            Long categoryId,
            CategoryType type,
            TransactionStatus status,
            int page,
            int size
    ) {
    }
}
