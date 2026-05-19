package com.kyuhyeong.account.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 임시 설정 — Task 4 단계.
 *
 * <p>Spring Security 가 classpath 에 있어 기본적으로 모든 엔드포인트를 차단한다. Task 4 의
 * 격리 검증 통합 테스트는 JWT 가 아직 없으므로 임시로 permitAll. CSRF / formLogin / httpBasic
 * 도 비활성.
 *
 * <p>Task 5 에서 본 클래스가 JWT 기반 인증 + 인가 체계로 전면 교체된다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());
        return http.build();
    }
}
