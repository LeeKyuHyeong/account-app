package com.kyuhyeong.account.core.repository;

import com.kyuhyeong.account.core.entity.MerchantHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 가맹점 학습 Repository (가구 격리 대상). */
public interface MerchantHistoryRepository extends JpaRepository<MerchantHistory, Long> {

    /**
     * 빈도 + 최근 사용순 상위 N개 조회. 가구 격리는 Hibernate {@code householdFilter} 가
     * 자동 적용 (호출자는 {@code @Transactional} 안에서 호출 필요).
     */
    List<MerchantHistory> findAllByOrderByCountDescLastUsedAtDesc(Pageable pageable);

    /** 가맹점명으로 학습 이력 UPSERT 용 lookup (가구 격리 자동 적용). */
    Optional<MerchantHistory> findByMerchantName(String merchantName);
}
