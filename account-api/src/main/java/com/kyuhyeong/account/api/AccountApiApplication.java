package com.kyuhyeong.account.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * account-api 진입점.
 *
 * <p>account-core / account-ai 의 Bean 을 함께 스캔하기 위해 패키지 베이스를
 * {@code com.kyuhyeong.account} 로 확장. JPA Entity / Repository 스캔 설정은
 * {@code account-core} 의 {@code JpaConfig} 가 캡슐화하여 보유.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.kyuhyeong.account")
public class AccountApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountApiApplication.class, args);
    }
}
