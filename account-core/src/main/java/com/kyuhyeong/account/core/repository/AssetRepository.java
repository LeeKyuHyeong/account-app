package com.kyuhyeong.account.core.repository;

import com.kyuhyeong.account.core.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

/** 자산 Repository (가구 격리 대상, v1.1). */
public interface AssetRepository extends JpaRepository<Asset, Long> {
}
