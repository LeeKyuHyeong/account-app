package com.kyuhyeong.account.api.summary;

import com.kyuhyeong.account.api.summary.MonthlySummaryDtos.CategoryAmount;
import com.kyuhyeong.account.api.summary.MonthlySummaryDtos.MonthlySummaryResponse;
import com.kyuhyeong.account.core.entity.Category;
import com.kyuhyeong.account.core.entity.Transaction;
import com.kyuhyeong.account.core.enums.CategoryType;
import com.kyuhyeong.account.core.repository.CategoryRepository;
import com.kyuhyeong.account.core.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 한 달치 거래 집계.
 *
 * <p>구현 전략: 메모리 집계.
 * <ul>
 *   <li>Specification 으로 한 달치 비-소프트삭제 거래만 fetch</li>
 *   <li>카테고리별 합계는 Map 으로 집계</li>
 *   <li>가구의 모든 카테고리를 sort_order 로 정렬해 응답 — 거래 0 인 카테고리도 포함해야
 *       UI 가 "예산 대비 사용 0%" 같은 정보를 표시할 수 있다</li>
 * </ul>
 *
 * <p>거래 0 인 가구의 첫 달은 매우 가볍지만 1년 후 한 달치 ~500건도 충분히 빠르다.
 * 만 거래/월 수준으로 커지면 Repository 에 {@code @Query} GROUP BY 합계로 교체.
 * 그 시점에는 사전 계산 (account-batch + monthly_summaries 테이블) 도 함께 도입.
 *
 * <p>가구 격리는 Hibernate {@code householdFilter} 가 자동 적용 — Specification 의
 * where 절은 가구 조건을 포함하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class MonthlySummaryService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public MonthlySummaryResponse get(YearMonth yearMonth) {
        LocalDateTime from = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime to = yearMonth.plusMonths(1).atDay(1).atStartOfDay();

        Specification<Transaction> spec = (root, cq, cb) -> cb.and(
                cb.isNull(root.get("deletedAt")),
                cb.greaterThanOrEqualTo(root.get("occurredAt"), from),
                cb.lessThan(root.get("occurredAt"), to)
        );
        List<Transaction> transactions = transactionRepository.findAll(spec);

        Map<Long, BigDecimal> sumByCategoryId = new HashMap<>();
        for (Transaction t : transactions) {
            sumByCategoryId.merge(t.getCategory().getId(), t.getAmount(), BigDecimal::add);
        }

        List<Category> categories = categoryRepository.findAll();

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expenseFixed = BigDecimal.ZERO;
        BigDecimal expenseVariable = BigDecimal.ZERO;
        BigDecimal invest = BigDecimal.ZERO;

        List<CategoryAmount> byCategory = new java.util.ArrayList<>(categories.size());
        for (Category c : categories) {
            BigDecimal sum = sumByCategoryId.getOrDefault(c.getId(), BigDecimal.ZERO);
            byCategory.add(new CategoryAmount(
                    c.getId(), c.getName(), c.getType(), sum,
                    c.getBudgetMonthly(), c.getSortOrder()));
            switch (c.getType()) {
                case INCOME   -> income = income.add(sum);
                case FIXED    -> expenseFixed = expenseFixed.add(sum);
                case VARIABLE -> expenseVariable = expenseVariable.add(sum);
                case INVEST   -> invest = invest.add(sum);
            }
        }
        byCategory.sort(Comparator.comparingInt(CategoryAmount::sortOrder));

        BigDecimal totalExpense = expenseFixed.add(expenseVariable);
        BigDecimal surplus = income.subtract(totalExpense);

        return new MonthlySummaryResponse(
                yearMonth.toString(),
                income, expenseFixed, expenseVariable, invest,
                totalExpense, surplus,
                byCategory
        );
    }
}
