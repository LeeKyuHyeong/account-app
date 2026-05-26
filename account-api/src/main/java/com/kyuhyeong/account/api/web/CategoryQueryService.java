package com.kyuhyeong.account.api.web;

import com.kyuhyeong.account.core.entity.Category;
import com.kyuhyeong.account.core.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 웹 폼/목록의 카테고리 드롭다운용 조회 — sort_order 정렬.
 *
 * <p>가구 격리는 Hibernate {@code householdFilter} 가 자동 적용. 웹 컨트롤러는 @Transactional
 * 경계가 없으므로(open-in-view=false) 카테고리 조회를 본 서비스의 @Transactional 안에서 한다.
 * CategoryController(REST) 의 조회와 동일 로직이나, M4 에서 REST 제거 시 이쪽으로 일원화한다.
 */
@Service
@RequiredArgsConstructor
public class CategoryQueryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<Category> findAllSorted() {
        return categoryRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Category::getSortOrder))
                .toList();
    }
}
