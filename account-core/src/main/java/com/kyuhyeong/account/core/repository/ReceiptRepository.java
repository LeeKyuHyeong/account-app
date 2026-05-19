package com.kyuhyeong.account.core.repository;

import com.kyuhyeong.account.core.entity.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;

/** 영수증 Repository (가구 격리 대상). */
public interface ReceiptRepository extends JpaRepository<Receipt, Long> {
}
