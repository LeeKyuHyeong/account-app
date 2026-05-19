package com.kyuhyeong.account.core.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/**
 * 가구 격리 Hibernate filter 활성화 advice.
 *
 * <p>{@code @Transactional} 메서드 진입 시점에 {@link HouseholdContext} 에 바인딩된 가구
 * ID 로 Hibernate {@code householdFilter} 를 활성화한다. {@link com.kyuhyeong.account.core.config.TransactionConfig}
 * 에서 트랜잭션 advice 의 order 를 100 으로 명시했으므로 본 aspect (기본 order =
 * LOWEST_PRECEDENCE) 는 트랜잭션 내부에서 실행된다 → 이 시점에 Session 이 바인딩되어
 * unwrap 가능.
 *
 * <p><strong>Fail-safe default</strong>: {@link HouseholdContext} 미바인딩 상태에서 격리
 * 엔티티를 쿼리하면 모든 가구 데이터가 노출될 수 있다 (격리 누수). 이를 막기 위해 본 aspect
 * 는 ctx 미설정 시 {@value NO_TENANT_SENTINEL} 로 filter 를 활성화 → 어떤 행도 매칭되지
 * 않아 빈 결과만 반환된다. Task 5 의 JWT 필터가 인증 미통과 요청을 401 로 끊으므로 본
 * sentinel 은 인증 단계 누수에 대한 두 번째 방어선.
 */
@Aspect
@Component
public class HouseholdFilterAspect {

    public static final String FILTER_NAME = "householdFilter";
    public static final String FILTER_PARAM = "currentHouseholdId";
    /** 어떤 household_id 도 매칭되지 않는 값 (PK 는 양수 AUTO_INCREMENT). */
    private static final long NO_TENANT_SENTINEL = -1L;

    @PersistenceContext
    private EntityManager entityManager;

    @Around(
            "@annotation(org.springframework.transaction.annotation.Transactional) "
                    + "|| @within(org.springframework.transaction.annotation.Transactional)"
    )
    public Object enableFilter(ProceedingJoinPoint pjp) throws Throwable {
        Session session = entityManager.unwrap(Session.class);
        long currentHouseholdId = HouseholdContext.isSet()
                ? HouseholdContext.get()
                : NO_TENANT_SENTINEL;
        session.enableFilter(FILTER_NAME)
                .setParameter(FILTER_PARAM, currentHouseholdId);
        return pjp.proceed();
    }
}
