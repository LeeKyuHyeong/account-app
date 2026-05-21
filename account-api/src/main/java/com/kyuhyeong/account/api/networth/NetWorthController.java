package com.kyuhyeong.account.api.networth;

import com.kyuhyeong.account.api.networth.NetWorthDtos.SnapshotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

/**
 * 순자산 조회 — 한 달치 스냅샷 (자산/부채 합계 + 항목 리스트).
 *
 * <p>월별 추이 (history) 는 별도 PR. 본 PR 은 입력 화면 (현재 월 snapshot) 까지만.
 */
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
}
