package com.kyuhyeong.account.batch.summary;

import com.kyuhyeong.account.core.entity.Household;
import com.kyuhyeong.account.core.repository.HouseholdRepository;
import com.kyuhyeong.account.core.tenant.HouseholdContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.List;

/**
 * 월별 집계 잡 — 매월 1일 03:00 KST 트리거.
 *
 * <p>모든 가구에 대해 직전 월의 카테고리 합계를 계산해 {@code monthly_summaries} 에
 * 적재. 가구별 트랜잭션 분리 ({@link MonthlyAggregationService} 가 자체
 * {@code @Transactional}) — 한 가구 실패가 다른 가구에 전파되지 않는다.
 *
 * <p>{@link HouseholdContext} 는 가구마다 set/clear — 격리 필터가 정확히 그 가구의
 * 거래만 보도록 한다.
 *
 * <p>03:00 선택 이유: 자정 직후는 (1) 일별 통계 잡과 겹칠 가능성 + (2) Flyway /
 * 배포 자동화가 자정에 몰리는 관행. 03:00 은 트래픽 최저 + 충돌 회피.
 */
@Component
@RequiredArgsConstructor
public class MonthlySummaryJob {

    private static final Logger log = LoggerFactory.getLogger(MonthlySummaryJob.class);

    private final HouseholdRepository householdRepository;
    private final MonthlyAggregationService aggregationService;

    /** 매월 1일 03:00 KST. 같은 잡을 수동 트리거하려면 {@link #runForMonth} 직접 호출. */
    @Scheduled(cron = "0 0 3 1 * *", zone = "Asia/Seoul")
    public void runMonthly() {
        YearMonth target = YearMonth.now().minusMonths(1);
        log.info("MonthlySummaryJob triggered for yearMonth={}", target);
        runForMonth(target);
    }

    /**
     * 가구 격리 미설정 상태에서 모든 가구 조회 — {@link HouseholdRepository} 자체는
     * 비격리 (Household 가 격리 단위 본체) 라 안전. 각 가구 처리 시 ctx 를 그 가구로
     * 바꿔주면 격리 필터가 거래에 정확히 적용된다.
     */
    public void runForMonth(YearMonth target) {
        List<Household> households = householdRepository.findAll();
        int totalProcessed = 0;
        for (Household h : households) {
            HouseholdContext.set(h.getId());
            try {
                aggregationService.aggregate(target);
                totalProcessed++;
            } catch (Exception e) {
                log.error("Aggregation failed for householdId={} yearMonth={}: ",
                        h.getId(), target, e);
            } finally {
                HouseholdContext.clear();
            }
        }
        log.info("MonthlySummaryJob completed: yearMonth={}, households={}/{}",
                target, totalProcessed, households.size());
    }
}
