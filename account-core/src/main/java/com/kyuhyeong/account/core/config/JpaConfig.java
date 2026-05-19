package com.kyuhyeong.account.core.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * account-core 의 JPA 스캔 설정.
 *
 * <p>JPA 관련 의존성 ({@code spring-data-jpa}) 은 account-core 의 {@code implementation}
 * 스코프라 소비 모듈 (account-api 등) 의 컴파일 클래스패스에서 직접 보이지 않는다.
 * 그래서 {@code @EntityScan} / {@code @EnableJpaRepositories} 를 account-core 안의
 * Configuration 으로 캡슐화하고, account-api 는 component-scan 만으로 본 클래스를 자동
 * 등록한다.
 *
 * <p>이 패턴 덕분에 account-api 의 build.gradle.kts 에 별도 JPA starter 의존성을 중복
 * 선언할 필요가 없다.
 */
@Configuration
@EntityScan(basePackages = "com.kyuhyeong.account.core.entity")
@EnableJpaRepositories(basePackages = "com.kyuhyeong.account.core.repository")
public class JpaConfig {
}
