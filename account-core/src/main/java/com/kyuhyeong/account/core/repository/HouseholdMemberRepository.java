package com.kyuhyeong.account.core.repository;

import com.kyuhyeong.account.core.entity.HouseholdMember;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 가구-사용자 멤버십 Repository.
 *
 * <p>HouseholdMember 자체는 격리의 메커니즘이지 격리 대상이 아님 ({@code @Filter} 미적용).
 * 사용자가 어느 가구에 속하는지 조회 (Task 5 로그인 시 활성 가구 결정) 가 주 용도.
 */
public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, Long> {
}
