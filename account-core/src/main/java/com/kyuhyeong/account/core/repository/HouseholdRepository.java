package com.kyuhyeong.account.core.repository;

import com.kyuhyeong.account.core.entity.Household;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 가구 Repository.
 *
 * <p>가구 자체는 격리 단위이므로 {@code @Filter} 대상 아님.
 */
public interface HouseholdRepository extends JpaRepository<Household, Long> {
}
