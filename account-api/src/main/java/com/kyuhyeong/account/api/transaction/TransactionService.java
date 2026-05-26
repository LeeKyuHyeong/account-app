package com.kyuhyeong.account.api.transaction;

import com.kyuhyeong.account.api.transaction.TransactionDtos.CreateTransactionRequest;
import com.kyuhyeong.account.api.transaction.TransactionDtos.PageResponse;
import com.kyuhyeong.account.api.transaction.TransactionDtos.TransactionResponse;
import com.kyuhyeong.account.api.transaction.TransactionDtos.UpdateTransactionRequest;
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
    private final TransactionHistoryService historyService;
    private final MerchantHistoryService merchantHistoryService;

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

    /** 단건 조회 — 웹 수정 화면 진입용. soft-delete 된 거래는 없는 것으로 취급. */
    @Transactional(readOnly = true)
    public TransactionResponse get(Long id) {
        Transaction tx = findOwnedById(id);
        if (tx.getDeletedAt() != null) {
            throw new IllegalArgumentException(
                    "Transaction not found or not in current household: " + id);
        }
        return TransactionResponse.from(tx);
    }

    /**
     * id 단건 조회. <b>{@link org.springframework.data.jpa.repository.JpaSpecificationExecutor#findOne}
     * (criteria 쿼리) 를 쓴다 — Hibernate {@code @Filter} 는 PK 직접 로드
     * ({@code JpaRepository.findById} = {@code EntityManager.find}) 에는 적용되지 않아 다른 가구의
     * 거래도 그대로 로드되어 가구 격리가 새기 때문</b>. criteria 쿼리에는 householdFilter 가
     * 자동 적용되어 다른 가구 거래는 0건이 된다.
     */
    private Transaction findOwnedById(Long id) {
        return transactionRepository.findOne(
                        (root, cq, cb) -> cb.equal(root.get("id"), id))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transaction not found or not in current household: " + id));
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
        historyService.logCreate(tx, authorUserId);
        // 수동 입력은 즉시 CONFIRMED 이므로 가맹점 학습 대상.
        merchantHistoryService.upsert(tx.getMerchant(), tx.getCategory());
        return TransactionResponse.from(tx);
    }

    /**
     * 부분 수정 (PATCH). 현재는 categoryId / status 만 지원 — 영수증 컨펌 흐름에 한정.
     *
     * <p>status 는 DRAFT → CONFIRMED 일방향만 허용 (역방향은 데이터 무결성 위험).
     * 카테고리 변경은 같은 가구의 카테고리여야 한다 (Hibernate filter 가 자동 강제).
     * soft-delete 된 거래는 수정 불가.
     */
    @Transactional
    public TransactionResponse update(Long id, UpdateTransactionRequest request, Long actorUserId) {
        // findOwnedById = criteria 쿼리 (householdFilter 적용). findById 는 PK 직접 로드라
        // 필터가 안 걸려 다른 가구 거래도 수정 가능해지는 격리 누수가 있었다.
        Transaction tx = findOwnedById(id);
        if (tx.getDeletedAt() != null) {
            throw new IllegalArgumentException("Cannot update deleted transaction: " + id);
        }

        TransactionHistoryService.Snapshot before = TransactionHistoryService.Snapshot.from(tx);
        boolean changed = false;
        User actor = userRepository.getReferenceById(actorUserId);

        if (request.categoryId() != null
                && !request.categoryId().equals(tx.getCategory().getId())) {
            Category newCategory = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Category not found or not in current household: " + request.categoryId()));
            tx.reassignCategory(newCategory, actor);
            changed = true;
        }
        if (request.status() != null && request.status() != tx.getStatus()) {
            if (request.status() != TransactionStatus.CONFIRMED) {
                throw new IllegalArgumentException(
                        "Only DRAFT → CONFIRMED transition is allowed via PATCH");
            }
            tx.confirm(actor);
            changed = true;
        }
        if (changed) {
            historyService.logUpdate(tx, before, actorUserId);
            // PATCH 이후 상태가 CONFIRMED 면 학습 — 영수증 컨펌 흐름의 학습 진입점.
            // (DRAFT 그대로면 학습 X. status 변경 없이 categoryId 만 바꾼 경우에도 학습 — 사용자가
            // 명시적으로 카테고리를 수정한 것이므로.)
            if (tx.getStatus() == TransactionStatus.CONFIRMED) {
                merchantHistoryService.upsert(tx.getMerchant(), tx.getCategory());
            }
        }
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
