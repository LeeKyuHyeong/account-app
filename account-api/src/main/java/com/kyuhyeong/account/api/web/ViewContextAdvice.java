package com.kyuhyeong.account.api.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 모든 SSR 컨트롤러 응답 모델에 공통 view 컨텍스트를 주입한다.
 *
 * <p>현재는 {@code currentUri} 만 — 템플릿 (navbar active 표시, FAB 숨김 등) 에서 현재 경로를
 * 판단하기 위해 사용한다.
 *
 * <p>왜 model attribute 로 주입하나: Thymeleaf 3.1 (Spring Boot 3.3 번들) 에서 표현식 안의
 * {@code #httpServletRequest} / {@code #request} 같은 servlet implicit object 직접 접근이
 * 더 이상 동작하지 않아 ({@code EL1007E: Property 'requestURI' cannot be found on null}),
 * 컨트롤러 측에서 명시적으로 주입한다.
 *
 * <p>{@code @ControllerAdvice} 는 모든 {@code @Controller} (= 본 프로젝트의 모든 Web*Controller)
 * 에 자동 적용되며 REST 컨트롤러는 M4 에서 제거됐으므로 부수효과 없음.
 */
@ControllerAdvice
public class ViewContextAdvice {

    /** 현재 요청 URI — 템플릿에서 {@code ${currentUri}} 로 사용. */
    @ModelAttribute("currentUri")
    public String currentUri(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
