package com.kyuhyeong.account.api.web;

import com.kyuhyeong.account.api.summary.MonthlySummaryDtos.CategoryAmount;
import com.kyuhyeong.account.api.summary.MonthlySummaryDtos.MonthlySummaryResponse;
import com.kyuhyeong.account.api.summary.MonthlySummaryService;
import com.kyuhyeong.account.core.enums.CategoryType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * 홈 화면 — 이번 달 요약 카드 + 예산 초과 경고.
 *
 * <p>집계는 {@link MonthlySummaryService#get} 을 그대로 재사용한다 (기존 /api/summary 와 동일).
 * 예산 초과 판정만 본 컨트롤러에서 한다 — 지출 카테고리 중 예산(&gt;0) 을 초과한 것.
 */
@Controller
@RequiredArgsConstructor
public class WebHomeController {

    private final MonthlySummaryService monthlySummaryService;

    @GetMapping("/web/home")
    public String home(Model model) {
        YearMonth month = YearMonth.now();
        MonthlySummaryResponse summary = monthlySummaryService.get(month);

        List<CategoryAmount> overBudget = summary.byCategory().stream()
                .filter(WebHomeController::isExpense)
                .filter(c -> c.budgetMonthly().compareTo(BigDecimal.ZERO) > 0
                        && c.total().compareTo(c.budgetMonthly()) > 0)
                .toList();

        model.addAttribute("monthLabel", month.getYear() + "년 " + month.getMonthValue() + "월");
        model.addAttribute("summary", summary);
        model.addAttribute("overBudget", overBudget);
        return "home";
    }

    private static boolean isExpense(CategoryAmount c) {
        return c.type() == CategoryType.FIXED || c.type() == CategoryType.VARIABLE;
    }
}
