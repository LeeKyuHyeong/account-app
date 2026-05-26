package com.kyuhyeong.account.api.config;

import com.kyuhyeong.account.api.security.JwtAuthenticationFilter;
import com.kyuhyeong.account.api.security.SessionHouseholdContextFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;

/**
 * Spring Security 설정 — JWT (REST) + 세션 (SSR) 듀얼 체인.
 *
 * <p>두 개의 {@link SecurityFilterChain} 을 등록한다.
 * <ul>
 *   <li>{@link #apiChain} — {@code @Order(1)}, {@code /api/**} 전용. STATELESS + Bearer JWT.
 *       기존 동작 유지 (Flutter / 외부 클라이언트 호환).</li>
 *   <li>{@link #webChain} — {@code @Order(2)}, 나머지 모든 요청. formLogin + 세션 + CSRF.
 *       {@link SessionHouseholdContextFilter} 가 세션 principal 의 활성 가구 ID 로
 *       {@code HouseholdContext} 를 채운다.</li>
 * </ul>
 *
 * <p>{@link JwtAuthenticationFilter} 와 {@link SessionHouseholdContextFilter} 모두 Spring Boot
 * 의 글로벌 Filter 자동 등록을 차단해야 한다 — 그렇지 않으면 두 체인 모두 거친 후 글로벌로
 * 한 번 더 실행되어 다른 체인의 컨텍스트를 오염시킨다. {@link #jwtFilterRegistration} 및
 * {@link #sessionHouseholdContextFilter} 가 enabled=false 로 등록한다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    @Order(1)
    public SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webChain(HttpSecurity http,
                                        SessionHouseholdContextFilter sessionHouseholdContextFilter)
            throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error", "/webjars/**",
                                "/css/**", "/js/**", "/favicon.ico").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        // PR1 검증용 — Task #4 에서 /web/home 으로 교체.
                        .defaultSuccessUrl("/web/ping", true)
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

    /**
     * {@link JwtAuthenticationFilter} 가 @Component 라서 Spring Boot 가 글로벌 /* 매핑으로
     * 자동 등록하려 한다. 본 등록을 disable 시켜 apiChain 안에서만 실행되도록 격리.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    /** SessionHouseholdContextFilter 도 동일 사유로 글로벌 자동 등록 차단. */
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
