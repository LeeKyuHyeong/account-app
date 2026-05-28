package com.kyuhyeong.account.api.config;

import com.kyuhyeong.account.api.security.SessionHouseholdContextFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;

/**
 * Spring Security 설정 — 세션 (SSR) 단일 체인.
 *
 * <p>formLogin + 세션 + CSRF (기본 활성). {@link SessionHouseholdContextFilter} 가 세션
 * principal 의 활성 가구 ID 로 {@code HouseholdContext} 를 채운다 (기존 JWT 필터의 역할 대체).
 *
 * <p>{@link SessionHouseholdContextFilter} 는 @Component 가 아니라 @Bean 으로 등록하되,
 * Spring Boot 가 글로벌 {@code /*} 매핑으로 자동 등록하면 SecurityFilterChain 과 글로벌에서
 * 두 번 실행되어 컨텍스트가 오염된다. {@link #sessionFilterRegistration} 가 enabled=false 로
 * 글로벌 자동 등록을 차단해 체인 안에서만 실행되도록 격리한다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain webChain(HttpSecurity http,
                                        SessionHouseholdContextFilter sessionHouseholdContextFilter)
            throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error", "/webjars/**",
                                "/css/**", "/js/**", "/favicon.ico").permitAll()
                        .requestMatchers("/web/admin/**").hasRole("OWNER")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/web/home", true)
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll())
                .addFilterAfter(sessionHouseholdContextFilter, SecurityContextHolderFilter.class);
        return http.build();
    }

    @Bean
    public SessionHouseholdContextFilter sessionHouseholdContextFilter() {
        return new SessionHouseholdContextFilter();
    }

    /** SessionHouseholdContextFilter 의 글로벌 자동 등록 차단 — webChain 안에서만 실행되도록. */
    @Bean
    public FilterRegistrationBean<SessionHouseholdContextFilter> sessionFilterRegistration(
            SessionHouseholdContextFilter filter) {
        FilterRegistrationBean<SessionHouseholdContextFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
