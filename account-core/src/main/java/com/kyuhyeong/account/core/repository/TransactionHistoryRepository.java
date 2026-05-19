package com.kyuhyeong.account.core.repository;

import com.kyuhyeong.account.core.entity.TransactionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

/** 거래 변경 이력 Repository (가구 격리 대상). */
public interface TransactionHistoryRepository extends JpaRepository<TransactionHistory, Long> {
}
