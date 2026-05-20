package com.kyuhyeong.account.core.repository;

import com.kyuhyeong.account.core.entity.MonthlySummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 월간 집계 Repository (가구 격리 대상).
 *
 * <p>가구 격리는 Hibernate {@code householdFilter} 자동 적용 — 메서드 시그니처에
 * householdId 를 명시하지 않는다.
 */
public interface MonthlySummaryRepository extends JpaRepository<MonthlySummary, Long> {

    /** 배치 잡의 UPSERT 용. 같은 월/카테고리 조합이 이미 있는지 확인. */
    Optional<MonthlySummary> findByYearMonthAndCategoryId(String yearMonth, Long categoryId);

    /** 차후 PR: 시계열 API 가 monthly_summaries 우선 조회로 전환할 때 사용. */
    List<MonthlySummary> findByYearMonth(String yearMonth);
}
