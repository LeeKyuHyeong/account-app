package com.kyuhyeong.account.api.ai;

import com.kyuhyeong.account.ai.model.MerchantHistoryContext;
import com.kyuhyeong.account.ai.service.MerchantHistoryProvider;
import com.kyuhyeong.account.core.entity.MerchantHistory;
import com.kyuhyeong.account.core.repository.MerchantHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * {@link MerchantHistoryProvider} 의 JPA 구현 — {@code merchant_history} 테이블에서
 * 가구별 학습 이력을 빈도 + 최근성 순으로 조회한다.
 *
 * <p>본 어댑터는 {@code account-api} 에 둔다. {@code account-core} 의 build.gradle.kts
 * 가 "다른 어떤 account-* 모듈에도 의존하지 않음" 을 명시하므로 core 가 ai 의
 * 인터페이스를 implements 할 수 없다. {@code account-api} 는 양쪽 모듈에 의존하는
 * 합성 모듈이라 어댑터를 두기에 적절하다.
 *
 * <p>{@code @Transactional(readOnly=true)} — {@code HouseholdFilterAspect} 가 tx 진입
 * 시점에 Hibernate {@code householdFilter} 를 활성화하므로 별도 where 절 없이도 현재
 * 가구 한정 결과만 반환된다. 메서드 인자 {@code householdId} 는 JWT 클레임에서 추출된
 * 값이고 {@link com.kyuhyeong.account.core.tenant.HouseholdContext} 와 항상 동일하므로
 * 본 구현은 ctx 기반 필터만으로 충분 (validation 은 상위 인증 계층 책임).
 */
@Component
@RequiredArgsConstructor
public class JpaMerchantHistoryProvider implements MerchantHistoryProvider {

    private final MerchantHistoryRepository merchantHistoryRepository;

    @Override
    @Transactional(readOnly = true)
    public MerchantHistoryContext getRecentHistory(Long householdId, int maxEntries) {
        if (maxEntries <= 0) {
            return MerchantHistoryContext.empty(householdId);
        }
        List<MerchantHistory> rows = merchantHistoryRepository
                .findAllByOrderByCountDescLastUsedAtDesc(PageRequest.of(0, maxEntries));

        List<MerchantHistoryContext.Entry> entries = rows.stream()
                .map(mh -> new MerchantHistoryContext.Entry(
                        mh.getMerchantName(),
                        mh.getCategory().getName(),
                        mh.getCount(),
                        mh.getLastUsedAt()))
                .toList();

        return new MerchantHistoryContext(householdId, entries);
    }
}
