package com.kyuhyeong.account.api.filter;

import com.kyuhyeong.account.core.tenant.HouseholdContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 요청 진입 시 가구 ID 를 {@link HouseholdContext} ThreadLocal 에 바인딩.
 *
 * <p>Task 4 임시 진입점 — {@code X-Household-Id} 헤더에서 ID 를 받는다. Task 5 에서 JWT
 * 검증 필터로 교체되며, 본 필터는 그때 제거되거나 JWT 처리 안 된 경로용 fallback 으로만 남는다.
 *
 * <p>실제 Hibernate {@code householdFilter} 활성화는 본 필터가 아니라
 * {@code HouseholdFilterAspect} 가 @Transactional 진입 시점에 수행한다. 본 필터는 ThreadLocal
 * 만 관리.
 *
 * <p>finally 블록에서 ThreadLocal 을 반드시 clear — 가상 스레드 풀에서 동일 스레드 재사용 시
 * 직전 요청 컨텍스트 누수 방지.
 */
@Component
public class HouseholdContextFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Household-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String headerVal = request.getHeader(HEADER);
        if (headerVal == null || headerVal.isBlank()) {
            // 비격리 경로 (health, /api/auth/** 등). HouseholdContext 미설정 상태로 진행.
            chain.doFilter(request, response);
            return;
        }
        Long householdId;
        try {
            householdId = Long.parseLong(headerVal);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid X-Household-Id");
            return;
        }
        try {
            HouseholdContext.set(householdId);
            chain.doFilter(request, response);
        } finally {
            HouseholdContext.clear();
        }
    }
}
