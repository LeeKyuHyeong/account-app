package com.kyuhyeong.account.api.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 세션 인증 경로의 진입점 컨트롤러.
 *
 * <ul>
 *   <li>{@code GET /login} — 로그인 폼 렌더링. POST 처리는 Spring Security 의 formLogin 이 담당.</li>
 *   <li>{@code GET /} — 루트 진입 시 /web/home 으로 리다이렉트 (인증 안 되어 있으면 Security 가 다시
 *       /login 으로 보낸다).</li>
 * </ul>
 *
 * <p>로그아웃은 POST /logout 으로 Spring Security 가 자동 처리하므로 별도 메서드 없음.
 */
@Controller
public class WebAuthController {

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/web/home";
    }
}
