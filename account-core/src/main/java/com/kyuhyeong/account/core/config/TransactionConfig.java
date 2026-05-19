package com.kyuhyeong.account.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 명시적으로 트랜잭션 어드바이스 order 를 지정해 AOP advice 순서를 제어한다.
 *
 * <p>Spring Boot 의 트랜잭션 자동 설정은 order 를 {@code LOWEST_PRECEDENCE} (Integer.MAX_VALUE)
 * 로 두는데, 그러면 {@code HouseholdFilterAspect} 가 (기본 order = LOWEST_PRECEDENCE) 어느
 * 쪽에 위치할지 결정적이지 않다. {@code order = 100} 으로 트랜잭션을 더 외곽 (outer wrapper)
 * 로 명시하면 내 aspect 가 tx 내부에서 실행되어 Session 이 바인딩된 상태로 filter 활성화 가능.
 *
 * <p>실행 순서:
 * <pre>
 *   Controller call
 *     → TransactionInterceptor(order=100) opens session
 *       → HouseholdFilterAspect enables householdFilter on session
 *         → Repository.findAll() — SQL has WHERE household_id = ?
 *       ← aspect end
 *     ← tx commit / session close
 * </pre>
 */
@Configuration
@EnableTransactionManagement(order = 100)
public class TransactionConfig {
}
