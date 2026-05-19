package com.kyuhyeong.account.core.repository;

import com.kyuhyeong.account.core.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

/** 거래 Repository (가구 격리 대상). */
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
}
