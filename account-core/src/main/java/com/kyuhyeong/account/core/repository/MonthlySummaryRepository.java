package com.kyuhyeong.account.core.repository;

import com.kyuhyeong.account.core.entity.MonthlySummary;
import org.springframework.data.jpa.repository.JpaRepository;

/** 월간 집계 Repository (가구 격리 대상). */
public interface MonthlySummaryRepository extends JpaRepository<MonthlySummary, Long> {
}
