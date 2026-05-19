package com.kyuhyeong.account.core.repository;

import com.kyuhyeong.account.core.entity.Liability;
import org.springframework.data.jpa.repository.JpaRepository;

/** 부채 Repository (가구 격리 대상, v1.1). */
public interface LiabilityRepository extends JpaRepository<Liability, Long> {
}
