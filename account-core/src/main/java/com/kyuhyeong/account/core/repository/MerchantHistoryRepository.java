package com.kyuhyeong.account.core.repository;

import com.kyuhyeong.account.core.entity.MerchantHistory;
import org.springframework.data.jpa.repository.JpaRepository;

/** 가맹점 학습 Repository (가구 격리 대상). */
public interface MerchantHistoryRepository extends JpaRepository<MerchantHistory, Long> {
}
