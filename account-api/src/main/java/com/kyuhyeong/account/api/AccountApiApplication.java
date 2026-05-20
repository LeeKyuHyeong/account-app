package com.kyuhyeong.account.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * account-api 진입점.
 *
 * <p>account-core / account-ai / account-batch 의 Bean 을 함께 스캔하기 위해 패키지
 * 베이스를 {@code com.kyuhyeong.account} 로 확장. JPA Entity / Repository 스캔 설정은
 * {@code account-core} 의 {@code JpaConfig} 가 캡슐화하여 보유.
 *
 * <p>{@code @EnableScheduling} — account-batch 모듈의 {@code @Scheduled} 잡들이
 * 본 프로세스에서 같이 기동되도록 활성화. 별도 프로세스 운영으로 전환하려면 본 어노테이션
 * 을 제거하고 batch 모듈에 자체 main 을 둔다.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.kyuhyeong.account")
@EnableScheduling
public class AccountApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountApiApplication.class, args);
    }
}
