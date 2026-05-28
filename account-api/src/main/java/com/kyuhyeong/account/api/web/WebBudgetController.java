package com.kyuhyeong.account.api.web;

import com.kyuhyeong.account.api.summary.MonthlySummaryDtos.CategoryAmount;
import com.kyuhyeong.account.api.summary.MonthlySummaryDtos.MonthlySummaryResponse;
import com.kyuhyeong.account.api.summary.MonthlySummaryService;
import com.kyuhyeong.account.core.enums.CategoryType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 카테고리별 예산 설정 + 이번 달 진행률. 지출 카테고리(FIXED/VARIABLE)만 대상.
 * 집계는 {@link MonthlySummaryService#get} 재사용, 예산 수정은 {@link CategoryQueryService#updateBudget}.
 */
@Controller
@RequiredArgsConstructor
public class WebBudgetController {

    private final MonthlySummaryService monthlySummaryService;
    private final CategoryQueryService categoryQueryService;

    @GetMapping("/web/budget")
    public String budget(Model model) {
        YearMonth month = YearMonth.now();
        MonthlySummaryResponse summary = monthlySummaryService.get(month);

        List<BudgetRow> rows = new ArrayList<>();
        for (CategoryAmount c : summary.byCategory()) {
            if (c.type() != CategoryType.FIXED && c.type() != CategoryType.VARIABLE) {
                continue;
            }
            BigDecimal budget = c.budgetMonthly() == null ? BigDecimal.ZERO : c.budgetMonthly();
            int pct = 0;
            boolean over = false;
            if (budget.signum() > 0) {
                pct = c.total().multiply(BigDecimal.valueOf(100))
                        .divide(budget, 0, RoundingMode.HALF_UP).intValue();
                over = c.total().compareTo(budget) > 0;
            }
            rows.add(new BudgetRow(c.categoryId(), c.name(), c.total(), budget, pct, over));
        }
        model.addAttribute("monthLabel", month.getYear() + "년 " + month.getMonthValue() + "월");
        model.addAttribute("rows", rows);
        return "budget";
    }

    @PostMapping("/web/budget")
    public String update(@RequestParam Long categoryId,
                         @RequestParam BigDecimal budgetMonthly,
                         RedirectAttributes ra) {
        categoryQueryService.updateBudget(categoryId, budgetMonthly);
        ra.addFlashAttribute("message", "예산이 저장되었습니다.");
        // 긴 카테고리 목록에서 편집한 행으로 돌아가도록 fragment 부여
        return "redirect:/web/budget#category-" + categoryId;
    }

    /** 한 카테고리의 예산 진행률 표시용 view row. pct 는 실제 비율(>100 가능), over 는 초과 여부. */
    public record BudgetRow(Long categoryId, String name, BigDecimal total, BigDecimal budget,
                            int pct, boolean over) {
    }
}
