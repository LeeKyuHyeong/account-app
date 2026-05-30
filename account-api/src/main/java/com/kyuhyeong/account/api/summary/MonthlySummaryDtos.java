package com.kyuhyeong.account.api.summary;

import com.kyuhyeong.account.core.enums.CategoryType;

import java.math.BigDecimal;
import java.util.List;

/** 월별 집계 API DTO. */
public final class MonthlySummaryDtos {

    private MonthlySummaryDtos() {
    }

    /**
     * 월별 합계 응답.
     *
     * <p>잉여금 정의 (docs/account.md §3.3): 수입 - (고정지출 + 변동지출). 투자/저축은
     * 별도 라인으로 노출 — 잉여금 계산에서 제외해 "이번 달 얼마 남았나" 의 직관과 일치.
     *
     * @param yearMonth 대상 월 (ISO YYYY-MM 형식)
     * @param income          INCOME 카테고리 합계
     * @param expenseFixed    FIXED 카테고리 합계
     * @param expenseVariable VARIABLE 카테고리 합계
     * @param invest          INVEST 카테고리 합계
     * @param totalExpense    expenseFixed + expenseVariable
     * @param surplus         income - totalExpense
     * @param byCategory      카테고리별 합계 (sort_order 정렬, 거래 0 인 카테고리도 포함)
     */
    public record MonthlySummaryResponse(
            String yearMonth,
            BigDecimal income,
            BigDecimal expenseFixed,
            BigDecimal expenseVariable,
            BigDecimal invest,
            BigDecimal totalExpense,
            BigDecimal surplus,
            List<CategoryAmount> byCategory
    ) {
    }

    /**
     * 임의 기간(일 단위, 양끝 inclusive) 합계 응답 — 기간/연 결산 화면용.
     *
     * <p>{@link MonthlySummaryResponse} 와 필드 동일하나 {@code yearMonth} 대신 표기용
     * {@code label}(예: "2026-01-01 ~ 2026-12-31") 을 가진다. 잉여금 정의는 동일:
     * 수입 - (고정 + 변동), 투자/저축은 별도 라인.
     */
    public record PeriodSummaryResponse(
            String label,
            BigDecimal income,
            BigDecimal expenseFixed,
            BigDecimal expenseVariable,
            BigDecimal invest,
            BigDecimal totalExpense,
            BigDecimal surplus,
            List<CategoryAmount> byCategory
    ) {
    }

    public record CategoryAmount(
            Long categoryId,
            String name,
            CategoryType type,
            BigDecimal total,
            BigDecimal budgetMonthly,
            int sortOrder
    ) {
    }
}
