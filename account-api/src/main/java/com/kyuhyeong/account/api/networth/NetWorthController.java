package com.kyuhyeong.account.api.networth;

import com.kyuhyeong.account.api.networth.NetWorthDtos.HistoryPoint;
import com.kyuhyeong.account.api.networth.NetWorthDtos.SnapshotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

/** 순자산 조회 — 한 달치 스냅샷 + 월별 추이 (차트용). */
@RestController
@RequestMapping("/api/networth")
@RequiredArgsConstructor
public class NetWorthController {

    private final NetWorthService netWorthService;

    /**
     * 월별 스냅샷.
     *
     * @param yearMonth ISO YYYY-MM. 미지정 시 서버 기준 현재 월.
     */
    @GetMapping("/snapshot")
    public SnapshotResponse snapshot(@RequestParam(required = false) String yearMonth) {
        YearMonth target = (yearMonth == null || yearMonth.isBlank())
                ? YearMonth.now()
                : NetWorthService.parseYearMonth(yearMonth);
        return netWorthService.snapshot(target);
    }

    /**
     * 월별 추이 — {@code from}(포함) ~ {@code to}(미포함), 최대 24개월. 차트용 합계만.
     */
    @GetMapping("/history")
    public List<HistoryPoint> history(
            @RequestParam String from,
            @RequestParam String to) {
        YearMonth start = NetWorthService.parseYearMonth(from);
        YearMonth end = NetWorthService.parseYearMonth(to);
        return netWorthService.history(start, end);
    }
}
