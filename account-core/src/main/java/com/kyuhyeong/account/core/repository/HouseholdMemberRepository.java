package com.kyuhyeong.account.core.repository;

import com.kyuhyeong.account.core.entity.HouseholdMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 가구-사용자 멤버십 Repository.
 *
 * <p>HouseholdMember 자체는 격리의 메커니즘이지 격리 대상이 아님 ({@code @Filter} 미적용).
 */
public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, Long> {

    /**
     * 사용자가 속한 모든 가구 멤버십 (Task 5 로그인 시 활성 가구 결정 + /api/auth/me).
     * MVP 는 1 사용자 = 1 가구지만 v1.5 다중 가구 대비 List 반환.
     */
    List<HouseholdMember> findByUserId(Long userId);

    /** 가구의 모든 멤버십 (관리자 페이지 멤버 목록). HouseholdMember 는 @Filter 미적용이라 명시 조건 필수. */
    List<HouseholdMember> findByHouseholdId(Long householdId);

    /** 특정 가구 + 사용자의 멤버십 (관리자 비번 재설정 시 가구 소속 검증). */
    Optional<HouseholdMember> findByHouseholdIdAndUserId(Long householdId, Long userId);
}
