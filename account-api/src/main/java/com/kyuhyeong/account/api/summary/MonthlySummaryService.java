package com.kyuhyeong.account.api.summary;

import com.kyuhyeong.account.api.summary.MonthlySummaryDtos.CategoryAmount;
import com.kyuhyeong.account.api.summary.MonthlySummaryDtos.MonthlySummaryResponse;
import com.kyuhyeong.account.api.summary.MonthlySummaryDtos.PeriodSummaryResponse;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
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

    /** 시계열 호출 한 번에 응답 가능한 최대 개월 수 (UI 차트가 의미있게 그릴 범위). */
    private static final int MAX_SERIES_MONTHS = 24;

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    /**
     * 시계열 합계 — {@code from} 부터 {@code to} 미만까지 (반-개구간). 차트 화면에서 6개월
     * 추이 등을 한 번에 요청하기 위함. 현재는 단순 루프로 {@link #get} 을 N 번 호출 —
     * 배치 잡이 {@code monthly_summaries} 를 채우기 시작하면 그 테이블 우선 조회로 전환.
     */
    @Transactional(readOnly = true)
    public List<MonthlySummaryResponse> series(YearMonth from, YearMonth to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from / to are required");
        }
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException(
                    "from (" + from + ") must be before to (" + to + "), to is exclusive");
        }
        long months = java.time.temporal.ChronoUnit.MONTHS.between(from, to);
        if (months > MAX_SERIES_MONTHS) {
            throw new IllegalArgumentException(
                    "series range exceeds " + MAX_SERIES_MONTHS + " months: " + months);
        }
        List<MonthlySummaryResponse> result = new ArrayList<>((int) months);
        for (YearMonth ym = from; ym.isBefore(to); ym = ym.plusMonths(1)) {
            result.add(get(ym));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public MonthlySummaryResponse get(YearMonth yearMonth) {
        LocalDateTime from = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime to = yearMonth.plusMonths(1).atDay(1).atStartOfDay();
        Aggregate agg = aggregate(from, to);
        return new MonthlySummaryResponse(
                yearMonth.toString(),
                agg.income(), agg.expenseFixed(), agg.expenseVariable(), agg.invest(),
                agg.totalExpense(), agg.surplus(), agg.byCategory());
    }

    /**
     * 임의 기간(일 단위, 양끝 inclusive) 합계 — 기간/연 결산 화면용.
     *
     * @param fromInclusive 시작일 (포함)
     * @param toInclusive   종료일 (포함) — 내부에서 +1일 해 반-개구간 [from, to) 으로 변환
     * @param label         화면 표기용 기간 라벨
     */
    @Transactional(readOnly = true)
    public PeriodSummaryResponse getRange(LocalDate fromInclusive, LocalDate toInclusive, String label) {
        if (fromInclusive == null || toInclusive == null) {
            throw new IllegalArgumentException("from / to are required");
        }
        if (toInclusive.isBefore(fromInclusive)) {
            throw new IllegalArgumentException(
                    "to (" + toInclusive + ") must not be before from (" + fromInclusive + ")");
        }
        Aggregate agg = aggregate(
                fromInclusive.atStartOfDay(),
                toInclusive.plusDays(1).atStartOfDay());
        return new PeriodSummaryResponse(
                label,
                agg.income(), agg.expenseFixed(), agg.expenseVariable(), agg.invest(),
                agg.totalExpense(), agg.surplus(), agg.byCategory());
    }

    /**
     * 한 {@code [from, to)} 구간의 카테고리별/타입별 합계 집계 (메모리). get/getRange 공용.
     * 가구 격리는 Hibernate {@code householdFilter} 가 자동 적용.
     */
    private Aggregate aggregate(LocalDateTime from, LocalDateTime to) {
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

        List<CategoryAmount> byCategory = new ArrayList<>(categories.size());
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
        return new Aggregate(income, expenseFixed, expenseVariable, invest,
                totalExpense, surplus, byCategory);
    }

    /** 집계 중간 결과 holder (내부 전용). */
    private record Aggregate(
            BigDecimal income, BigDecimal expenseFixed, BigDecimal expenseVariable,
            BigDecimal invest, BigDecimal totalExpense, BigDecimal surplus,
            List<CategoryAmount> byCategory) {}
}
