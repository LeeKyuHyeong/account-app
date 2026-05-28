package com.kyuhyeong.account.api.web;

import com.kyuhyeong.account.core.entity.User;
import com.kyuhyeong.account.core.repository.HouseholdMemberRepository;
import com.kyuhyeong.account.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 관리자 페이지(OWNER 전용)의 사용자 관리 — 가구 멤버 조회 + 비밀번호 재설정.
 *
 * <p>raw-SQL 로 하던 운영 비밀번호 변경(data-cleaning.md §2)을 UI 로 대체한다.
 *
 * <p><b>격리 주의</b>: {@code User} / {@code HouseholdMember} 는 Hibernate {@code @Filter} 적용
 * 대상이 아니므로(전역 식별 단위) 가구 경계를 코드로 직접 강제한다 — 멤버십 조회 / 비번 변경 모두
 * householdId 를 명시 조건으로 검증해 타 가구 사용자 접근을 차단한다.
 *
 * <p>웹 컨트롤러는 @Transactional 경계가 없으므로(open-in-view=false) 지연 로딩 연관
 * (HouseholdMember → User) 접근을 본 서비스의 @Transactional 안에서 끝낸다.
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final HouseholdMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** 가구 멤버 목록 (userId 오름차순). */
    @Transactional(readOnly = true)
    public List<MemberView> listMembers(Long householdId) {
        return memberRepository.findByHouseholdId(householdId).stream()
                .map(m -> {
                    User u = m.getUser();
                    return new MemberView(u.getId(), u.getEmail(), u.getName(),
                            m.getRole().name(), u.getLastLoginAt());
                })
                .sorted(Comparator.comparing(MemberView::userId))
                .toList();
    }

    /**
     * 가구 멤버의 비밀번호 재설정. {@code rawPassword} 를 BCrypt 인코딩해 저장한다.
     *
     * <p>대상 사용자가 해당 가구의 멤버가 아니면 거부 — 타 가구 사용자 비번 변경 차단.
     */
    @Transactional
    public void resetPassword(Long householdId, Long userId, String rawPassword) {
        memberRepository.findByHouseholdIdAndUserId(householdId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User " + userId + " is not a member of household " + householdId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.changePassword(passwordEncoder.encode(rawPassword));
    }

    /** 멤버 목록 표시용 view. */
    public record MemberView(Long userId, String email, String name, String role,
                             LocalDateTime lastLoginAt) {
    }
}
