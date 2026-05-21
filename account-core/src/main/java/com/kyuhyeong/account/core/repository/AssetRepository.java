package com.kyuhyeong.account.core.repository;

import com.kyuhyeong.account.core.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 자산 Repository (가구 격리 대상, v1.1).
 *
 * <p>{@link JpaSpecificationExecutor} 는 월별 필터 (recorded_at = YYYY-MM-01) 조립용.
 * 가구 격리는 Hibernate {@code householdFilter} 가 SQL 레벨에서 자동 적용.
 */
public interface AssetRepository
        extends JpaRepository<Asset, Long>, JpaSpecificationExecutor<Asset> {
}
