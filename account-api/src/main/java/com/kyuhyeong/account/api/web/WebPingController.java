package com.kyuhyeong.account.api.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 셋업 검증용 임시 페이지.
 *
 * <p>PR1 의 인수 기준 — 로그인 후 {@code GET /web/ping} 이 Thymeleaf + Bootstrap 5 로 렌더링되고
 * 세션 principal 의 이메일이 navbar 에 표시된다. Task #4 (Home 화면) 가 완성되면 본 컨트롤러는
 * 제거.
 */
@Controller
public class WebPingController {

    @GetMapping("/web/ping")
    public String ping(Model model) {
        model.addAttribute("message", "OK");
        return "_ping";
    }
}
