package com.kyuhyeong.account.api.web;

import com.kyuhyeong.account.api.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 관리자 페이지 (OWNER 전용) — 가구 멤버 목록 + 비밀번호 재설정.
 *
 * <p>경로 접근 제어(OWNER)는 {@code SecurityConfig} 의 {@code /web/admin/**} → {@code hasRole("OWNER")}
 * 가 담당. 비번 변경 대상이 본인 가구 멤버인지의 격리 검증은 {@link AdminUserService} 가 담당.
 */
@Controller
@RequiredArgsConstructor
public class WebAdminController {

    /** 비밀번호 최소 길이. */
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final AdminUserService adminUserService;

    @GetMapping("/web/admin")
    public String admin(@AuthenticationPrincipal CustomUserDetails user, Model model) {
        model.addAttribute("members", adminUserService.listMembers(user.getActiveHouseholdId()));
        return "admin/users";
    }

    @PostMapping("/web/admin/users/{userId}/password")
    public String resetPassword(@PathVariable Long userId,
                                @RequestParam String newPassword,
                                @AuthenticationPrincipal CustomUserDetails user,
                                RedirectAttributes ra) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            ra.addFlashAttribute("error", "비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다.");
            return "redirect:/web/admin";
        }
        adminUserService.resetPassword(user.getActiveHouseholdId(), userId, newPassword);
        ra.addFlashAttribute("message", "비밀번호가 변경되었습니다.");
        return "redirect:/web/admin";
    }
}
