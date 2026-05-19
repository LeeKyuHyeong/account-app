package com.kyuhyeong.account.core.repository;

import com.kyuhyeong.account.core.entity.WeddingItem;
import org.springframework.data.jpa.repository.JpaRepository;

/** 결혼 일시 지출 Repository (가구 격리 대상, v1.1). */
public interface WeddingItemRepository extends JpaRepository<WeddingItem, Long> {
}
