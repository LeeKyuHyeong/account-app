package com.kyuhyeong.account.core.repository;

import com.kyuhyeong.account.core.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사용자 Repository.
 *
 * <p>가구 격리 대상 외 (사용자 자체는 글로벌 식별 단위). 쿼리 메서드는 필요 시점
 * (Task 5 JWT 로그인 등) 에 추가.
 */
public interface UserRepository extends JpaRepository<User, Long> {
}
