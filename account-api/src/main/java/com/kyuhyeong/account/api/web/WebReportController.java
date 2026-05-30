package com.kyuhyeong.account.api.web;

import com.kyuhyeong.account.api.summary.MonthlySummaryDtos.PeriodSummaryResponse;
import com.kyuhyeong.account.api.summary.MonthlySummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

/**
 * 기간/연 결산 — 임의 {@code from~to}(또는 프리셋: 이번 달/지난 달/올해/작년) 구간의
 * 수입·지출·잉여·투자 합계 + 카테고리별 표. {@link MonthlySummaryService#getRange} 재사용.
 *
 * <p>기본 구간은 올해 전체(1/1~12/31) — 미래일 거래는 0 이라 누계와 동일하게 보인다.
 */
@Controller
@RequiredArgsConstructor
public class WebReportController {

    private final MonthlySummaryService monthlySummaryService;

    @GetMapping("/web/report")
    public String report(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {

        LocalDate today = LocalDate.now();
        if (from == null) {
            from = today.withDayOfYear(1);
        }
        if (to == null) {
            to = today.withDayOfYear(1).plusYears(1).minusDays(1);   // 올해 12/31
        }
        if (to.isBefore(from)) {
            to = from;
        }

        String label = from + " ~ " + to;
        PeriodSummaryResponse report = monthlySummaryService.getRange(from, to, label);

        model.addAttribute("report", report);
        model.addAttribute("fFrom", from);
        model.addAttribute("fTo", to);
        model.addAttribute("presets", buildPresets(today, from, to));
        return "report/report";
    }

    /** 이번 달 / 지난 달 / 올해 / 작년 프리셋 (전체 구간). active 는 현재 선택과 일치 시. */
    private List<Preset> buildPresets(LocalDate today, LocalDate from, LocalDate to) {
        LocalDate thisMonthStart = today.withDayOfMonth(1);
        LocalDate thisYearStart = today.withDayOfYear(1);
        return List.of(
                preset("이번 달", thisMonthStart, thisMonthStart.plusMonths(1).minusDays(1), from, to),
                preset("지난 달", thisMonthStart.minusMonths(1), thisMonthStart.minusDays(1), from, to),
                preset("올해", thisYearStart, thisYearStart.plusYears(1).minusDays(1), from, to),
                preset("작년", thisYearStart.minusYears(1), thisYearStart.minusDays(1), from, to)
        );
    }

    private Preset preset(String label, LocalDate pf, LocalDate pt, LocalDate from, LocalDate to) {
        return new Preset(label, pf, pt, pf.equals(from) && pt.equals(to));
    }

    public record Preset(String label, LocalDate from, LocalDate to, boolean active) {}
}
