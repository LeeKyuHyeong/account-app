package com.kyuhyeong.account.batch.summary;

import com.kyuhyeong.account.core.entity.Category;
import com.kyuhyeong.account.core.entity.Household;
import com.kyuhyeong.account.core.entity.MonthlySummary;
import com.kyuhyeong.account.core.entity.Transaction;
import com.kyuhyeong.account.core.repository.CategoryRepository;
import com.kyuhyeong.account.core.repository.HouseholdRepository;
import com.kyuhyeong.account.core.repository.MonthlySummaryRepository;
import com.kyuhyeong.account.core.repository.TransactionRepository;
import com.kyuhyeong.account.core.tenant.HouseholdContext;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 한 가구의 한 달치 카테고리별 합계를 계산해 {@code monthly_summaries} 에 UPSERT.
 *
 * <p>{@code @Transactional} — 가구 한 명 처리가 부분 실패하지 않도록 원자성 보장.
 * {@link HouseholdContext} 가 호출자에서 set 된 상태로 진입해야 한다 (Hibernate filter
 * 활성화 + repository 격리 필터 자동 적용).
 *
 * <p>월별 카테고리 1행 구조 (UNIQUE on household_id + year_month + category_id) 이므로
 * 거래 0 건 카테고리는 적재하지 않는다 — 차후 UI 에서 totalAmount=0 가 필요하면 join
 * 으로 채운다.
 */
@Service
@RequiredArgsConstructor
public class MonthlyAggregationService {

    private static final Logger log = LoggerFactory.getLogger(MonthlyAggregationService.class);

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final MonthlySummaryRepository monthlySummaryRepository;
    private final HouseholdRepository householdRepository;

    @Transactional
    public int aggregate(YearMonth target) {
        Long householdId = HouseholdContext.get();
        String yearMonthKey = target.toString();
        LocalDateTime from = target.atDay(1).atStartOfDay();
        LocalDateTime to = target.plusMonths(1).atDay(1).atStartOfDay();

        Specification<Transaction> spec = (root, cq, cb) -> {
            Predicate notDeleted = cb.isNull(root.get("deletedAt"));
            Predicate inRange = cb.and(
                    cb.greaterThanOrEqualTo(root.get("occurredAt"), from),
                    cb.lessThan(root.get("occurredAt"), to)
            );
            return cb.and(notDeleted, inRange);
        };
        List<Transaction> transactions = transactionRepository.findAll(spec);

        Map<Long, BigDecimal> sumByCategory = new HashMap<>();
        Map<Long, Integer> countByCategory = new HashMap<>();
        for (Transaction t : transactions) {
            Long cid = t.getCategory().getId();
            sumByCategory.merge(cid, t.getAmount(), BigDecimal::add);
            countByCategory.merge(cid, 1, Integer::sum);
        }

        Household household = householdRepository.getReferenceById(householdId);
        int upserted = 0;
        for (Map.Entry<Long, BigDecimal> e : sumByCategory.entrySet()) {
            Long categoryId = e.getKey();
            BigDecimal total = e.getValue();
            int count = countByCategory.getOrDefault(categoryId, 0);
            upsert(household, yearMonthKey, categoryId, total, count);
            upserted++;
        }
        log.info("Aggregation done: householdId={}, yearMonth={}, categories={}",
                householdId, yearMonthKey, upserted);
        return upserted;
    }

    private void upsert(Household household,
                        String yearMonthKey,
                        Long categoryId,
                        BigDecimal totalAmount,
                        int transactionCount) {
        monthlySummaryRepository.findByYearMonthAndCategoryId(yearMonthKey, categoryId)
                .ifPresentOrElse(
                        existing -> existing.refresh(totalAmount, transactionCount),
                        () -> {
                            Category category = categoryRepository.getReferenceById(categoryId);
                            MonthlySummary fresh = MonthlySummary.builder()
                                    .household(household)
                                    .yearMonth(yearMonthKey)
                                    .category(category)
                                    .totalAmount(totalAmount)
                                    .transactionCount(transactionCount)
                                    .build();
                            monthlySummaryRepository.save(fresh);
                        });
    }
}
