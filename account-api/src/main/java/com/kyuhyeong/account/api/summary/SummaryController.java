package com.kyuhyeong.account.api.summary;

import com.kyuhyeong.account.api.summary.MonthlySummaryDtos.MonthlySummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 집계 / 통계 엔드포인트.
 *
 * <p>현재는 월별 합계만. 카테고리별 추이 / 분기 / 연 누적 등은 별도 PR 에서.
 */
@RestController
@RequestMapping("/api/summary")
@RequiredArgsConstructor
public class SummaryController {

    private final MonthlySummaryService monthlySummaryService;

    /**
     * 월별 합계.
     *
     * @param yearMonth ISO YYYY-MM 형식. 미지정 시 서버 기준 현재 월.
     */
    @GetMapping("/monthly")
    public MonthlySummaryResponse monthly(
            @RequestParam(required = false) String yearMonth) {
        YearMonth target = parseYearMonth(yearMonth, "yearMonth");
        return monthlySummaryService.get(target);
    }

    /**
     * 시계열 — {@code from} 부터 {@code to} 미만 (반-개구간). 차트 화면에서 6개월 추이 등에 사용.
     */
    @GetMapping("/monthly/series")
    public List<MonthlySummaryResponse> series(
            @RequestParam String from,
            @RequestParam String to) {
        YearMonth start = parseYearMonth(from, "from");
        YearMonth end = parseYearMonth(to, "to");
        return monthlySummaryService.series(start, end);
    }

    private YearMonth parseYearMonth(String raw, String paramName) {
        if (raw == null || raw.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    paramName + " must be in YYYY-MM format (e.g. 2026-05), got: " + raw);
        }
    }
}
