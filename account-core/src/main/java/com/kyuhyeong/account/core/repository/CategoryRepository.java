package com.kyuhyeong.account.core.repository;

import com.kyuhyeong.account.core.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 카테고리 Repository (가구 격리 대상).
 *
 * <p>Task 4 에서 {@code @Filter} 가 활성화되면 {@link #findAll()} 등 모든 기본 메서드가
 * 자동으로 현재 가구 한정 결과를 반환한다.
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {
}
