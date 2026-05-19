package com.kyuhyeong.account.api.controller;

import com.kyuhyeong.account.core.entity.Category;
import com.kyuhyeong.account.core.enums.CategoryType;
import com.kyuhyeong.account.core.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 카테고리 컨트롤러 — Task 4 격리 검증용 최소 스텁.
 *
 * <p>{@code GET /api/categories} 는 현재 가구의 카테고리만 반환해야 한다 (Hibernate
 * {@code householdFilter} 자동 적용). 본 컨트롤러는 Task 6 에서 본격 작성된다.
 */
@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;

    @GetMapping("/api/categories")
    @Transactional(readOnly = true)
    public List<CategoryDto> list() {
        return categoryRepository.findAll().stream()
                .map(CategoryDto::from)
                .toList();
    }

    public record CategoryDto(Long id, String name, CategoryType type, BigDecimal budgetMonthly) {
        static CategoryDto from(Category c) {
            return new CategoryDto(c.getId(), c.getName(), c.getType(), c.getBudgetMonthly());
        }
    }
}
