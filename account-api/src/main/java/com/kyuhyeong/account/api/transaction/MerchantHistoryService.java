package com.kyuhyeong.account.api.transaction;

import com.kyuhyeong.account.core.entity.Category;
import com.kyuhyeong.account.core.entity.Household;
import com.kyuhyeong.account.core.entity.MerchantHistory;
import com.kyuhyeong.account.core.repository.HouseholdRepository;
import com.kyuhyeong.account.core.repository.MerchantHistoryRepository;
import com.kyuhyeong.account.core.tenant.HouseholdContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 가구별 가맹점 학습 UPSERT (docs/account.md §3.2 학습 피드백 루프).
 *
 * <p>사용자가 거래의 카테고리를 컨펌하거나 직접 입력할 때, 같은 가맹점이 다음에 다시
 * 등장하면 같은 카테고리로 분류되도록 {@code merchant_history} 에 누적한다. 본 학습
 * 데이터는 {@code MerchantHistoryProvider} 가 Claude 프롬프트 컨텍스트에 주입한다.
 *
 * <p>학습 시점:
 * <ul>
 *   <li>{@code POST /api/transactions} (수동 입력) — 사용자가 직접 카테고리 선택했으므로 학습</li>
 *   <li>{@code PATCH /api/transactions/{id}} (DRAFT → CONFIRMED) — 사용자가 컨펌</li>
 *   <li>{@code PATCH /api/transactions/{id}} (categoryId 변경) — 사용자가 AI 분류를 수정</li>
 * </ul>
 *
 * <p>학습 대상이 아닌 경우:
 * <ul>
 *   <li>영수증 업로드 직후 자동 생성된 DRAFT 거래 (사용자 컨펌 전)</li>
 *   <li>가맹점명이 비어 있는 거래</li>
 * </ul>
 *
 * <p>가구 격리는 Hibernate {@code householdFilter} 가 자동 적용 — 호출자의
 * {@code @Transactional} 안에서 동작하므로 lookup 도 같은 가구 한정.
 */
@Service
@RequiredArgsConstructor
public class MerchantHistoryService {

    private static final Logger log = LoggerFactory.getLogger(MerchantHistoryService.class);

    private final MerchantHistoryRepository merchantHistoryRepository;
    private final HouseholdRepository householdRepository;

    /**
     * 가맹점-카테고리 쌍을 UPSERT.
     *
     * @param merchant 가맹점명. null/blank 면 학습 생략 (영수증 OCR 가 가맹점을 못 잡은 경우).
     * @param category 사용자가 선택/확정한 카테고리. 같은 가구에 속해야 함 (호출자 검증 책임).
     */
    public void upsert(String merchant, Category category) {
        if (merchant == null || merchant.isBlank()) {
            return;
        }
        merchantHistoryRepository.findByMerchantName(merchant).ifPresentOrElse(
                existing -> existing.touchUsage(category),
                () -> insertNew(merchant, category)
        );
    }

    private void insertNew(String merchant, Category category) {
        Long householdId = HouseholdContext.get();
        Household household = householdRepository.getReferenceById(householdId);
        MerchantHistory created = MerchantHistory.builder()
                .household(household)
                .merchantName(merchant)
                .category(category)
                .count(1)
                .lastUsedAt(LocalDateTime.now())
                .build();
        merchantHistoryRepository.save(created);
        log.debug("New merchant_history: householdId={}, merchant='{}', categoryId={}",
                householdId, merchant, category.getId());
    }
}
