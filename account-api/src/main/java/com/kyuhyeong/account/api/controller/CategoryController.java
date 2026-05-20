package com.kyuhyeong.account.api.controller;

import com.kyuhyeong.account.core.entity.Category;
import com.kyuhyeong.account.core.enums.CategoryType;
import com.kyuhyeong.account.core.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * 카테고리 컨트롤러.
 *
 * <p>{@code GET /api/categories} 는 현재 가구의 카테고리만 반환 (Hibernate
 * {@code householdFilter} 자동 적용). 선택적 {@code type} 쿼리로 입력 폼에서
 * 지출 카테고리만 필터링 가능. 응답은 {@code sortOrder} 오름차순.
 */
@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;

    @GetMapping("/api/categories")
    @Transactional(readOnly = true)
    public List<CategoryDto> list(@RequestParam(required = false) CategoryType type) {
        return categoryRepository.findAll().stream()
                .filter(c -> type == null || c.getType() == type)
                .sorted(Comparator.comparingInt(Category::getSortOrder))
                .map(CategoryDto::from)
                .toList();
    }

    public record CategoryDto(Long id, String name, CategoryType type, BigDecimal budgetMonthly) {
        static CategoryDto from(Category c) {
            return new CategoryDto(c.getId(), c.getName(), c.getType(), c.getBudgetMonthly());
        }
    }
}
