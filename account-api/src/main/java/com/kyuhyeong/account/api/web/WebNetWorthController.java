package com.kyuhyeong.account.api.web;

import com.kyuhyeong.account.api.networth.NetWorthDtos.CreateRequest;
import com.kyuhyeong.account.api.networth.NetWorthDtos.HistoryPoint;
import com.kyuhyeong.account.api.networth.NetWorthDtos.SnapshotResponse;
import com.kyuhyeong.account.api.networth.NetWorthDtos.UpdateRequest;
import com.kyuhyeong.account.api.networth.NetWorthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 순자산 — 한 달 스냅샷(자산/부채 목록 + 합계) + 12개월 추이 차트 + 자산/부채 추가·삭제.
 *
 * <p>자산/부채 행은 인라인 폼으로 이름·종류·잔액을 바로 수정할 수 있다 (월 변경은 미지원 —
 * 월을 잘못 입력한 경우엔 삭제 후 재추가). 모든 CRUD 는 {@link NetWorthService} 재사용이며
 * 가구 격리는 Hibernate filter 가 자동 적용한다.
 */
@Controller
@RequestMapping("/web/networth")
@RequiredArgsConstructor
public class WebNetWorthController {

    private static final int HISTORY_MONTHS = 12;

    private final NetWorthService netWorthService;

    @GetMapping
    public String networth(@RequestParam(required = false) String ym, Model model) {
        YearMonth month = safeYearMonth(ym);
        SnapshotResponse snapshot = netWorthService.snapshot(month);

        YearMonth to = month.plusMonths(1);
        List<HistoryPoint> history = netWorthService.history(to.minusMonths(HISTORY_MONTHS), to);
        List<String> labels = new ArrayList<>();
        List<BigDecimal> assetsSeries = new ArrayList<>();
        List<BigDecimal> liabilitiesSeries = new ArrayList<>();
        List<BigDecimal> netWorthSeries = new ArrayList<>();
        for (HistoryPoint h : history) {
            labels.add(h.yearMonth());
            assetsSeries.add(h.assetsTotal());
            liabilitiesSeries.add(h.liabilitiesTotal());
            netWorthSeries.add(h.netWorth());
        }

        model.addAttribute("month", month.toString());
        model.addAttribute("snapshot", snapshot);
        model.addAttribute("labels", labels);
        model.addAttribute("assetsSeries", assetsSeries);
        model.addAttribute("liabilitiesSeries", liabilitiesSeries);
        model.addAttribute("netWorthSeries", netWorthSeries);
        return "networth";
    }

    @PostMapping("/assets")
    public String createAsset(@RequestParam String name, @RequestParam String type,
                              @RequestParam BigDecimal balance, @RequestParam String yearMonth,
                              RedirectAttributes ra) {
        netWorthService.createAsset(new CreateRequest(name, type, balance, yearMonth));
        ra.addFlashAttribute("message", "자산이 추가되었습니다.");
        return "redirect:/web/networth?ym=" + yearMonth;
    }

    /**
     * 자산 인라인 편집 — 이름·종류·잔액만. {@code ym} 은 redirect 복귀용 view 파라미터로
     * 엔티티의 월에는 영향 없음 ({@link UpdateRequest#yearMonth()} 를 null 로 전달).
     */
    @PostMapping("/assets/{id}")
    public String updateAsset(@PathVariable Long id,
                              @RequestParam String name,
                              @RequestParam String type,
                              @RequestParam BigDecimal balance,
                              @RequestParam String ym,
                              RedirectAttributes ra) {
        netWorthService.updateAsset(id, new UpdateRequest(name, type, balance, null));
        ra.addFlashAttribute("message", "자산이 수정되었습니다.");
        // 긴 페이지에서 편집한 행 위치로 돌아가도록 fragment 부여 (브라우저 native scroll-to-anchor)
        return "redirect:/web/networth?ym=" + ym + "#asset-" + id;
    }

    @PostMapping("/assets/{id}/delete")
    public String deleteAsset(@PathVariable Long id, @RequestParam String ym,
                              RedirectAttributes ra) {
        netWorthService.deleteAsset(id);
        ra.addFlashAttribute("message", "자산이 삭제되었습니다.");
        return "redirect:/web/networth?ym=" + ym;
    }

    @PostMapping("/liabilities")
    public String createLiability(@RequestParam String name, @RequestParam String type,
                                  @RequestParam BigDecimal balance, @RequestParam String yearMonth,
                                  RedirectAttributes ra) {
        netWorthService.createLiability(new CreateRequest(name, type, balance, yearMonth));
        ra.addFlashAttribute("message", "부채가 추가되었습니다.");
        return "redirect:/web/networth?ym=" + yearMonth;
    }

    /** 부채 인라인 편집 — 자산과 동일 정책 (월 변경 미지원, 편집 후 해당 행으로 스크롤). */
    @PostMapping("/liabilities/{id}")
    public String updateLiability(@PathVariable Long id,
                                  @RequestParam String name,
                                  @RequestParam String type,
                                  @RequestParam BigDecimal balance,
                                  @RequestParam String ym,
                                  RedirectAttributes ra) {
        netWorthService.updateLiability(id, new UpdateRequest(name, type, balance, null));
        ra.addFlashAttribute("message", "부채가 수정되었습니다.");
        return "redirect:/web/networth?ym=" + ym + "#liability-" + id;
    }

    @PostMapping("/liabilities/{id}/delete")
    public String deleteLiability(@PathVariable Long id, @RequestParam String ym,
                                  RedirectAttributes ra) {
        netWorthService.deleteLiability(id);
        ra.addFlashAttribute("message", "부채가 삭제되었습니다.");
        return "redirect:/web/networth?ym=" + ym;
    }

    private static YearMonth safeYearMonth(String ym) {
        if (ym == null || ym.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(ym);
        } catch (RuntimeException e) {
            return YearMonth.now();
        }
    }
}
