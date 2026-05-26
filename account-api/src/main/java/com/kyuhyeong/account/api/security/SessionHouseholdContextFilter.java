package com.kyuhyeong.account.api.security;

import com.kyuhyeong.account.core.tenant.HouseholdContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 세션 인증 경로(/web/**) 용 {@link HouseholdContext} 주입 필터.
 *
 * <p>JWT 경로의 {@link JwtAuthenticationFilter} 가 토큰 클레임에서 householdId 를 꺼내는 것과
 * 동일하게, 여기서는 세션에 저장된 {@link CustomUserDetails} 의 activeHouseholdId 를 꺼낸다.
 *
 * <p>finally 블록에서 ThreadLocal clear — 가상 스레드 재사용 시 누수 방지.
 *
 * <p>SecurityConfig 가 본 필터를 web SecurityFilterChain 의 SecurityContextHolderFilter 직후에
 * 배치한다. Spring Boot 의 자동 Filter 등록은 SecurityConfig 의 FilterRegistrationBean
 * (enabled=false) 으로 차단한다.
 */
public class SessionHouseholdContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        boolean contextSet = false;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails ud
                && ud.getActiveHouseholdId() != null) {
            HouseholdContext.set(ud.getActiveHouseholdId());
            contextSet = true;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            if (contextSet) {
                HouseholdContext.clear();
            }
        }
    }
}
