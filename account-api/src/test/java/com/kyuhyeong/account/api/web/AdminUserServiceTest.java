package com.kyuhyeong.account.api.web;

import com.kyuhyeong.account.api.web.AdminUserService.MemberView;
import com.kyuhyeong.account.core.entity.Household;
import com.kyuhyeong.account.core.entity.HouseholdMember;
import com.kyuhyeong.account.core.entity.User;
import com.kyuhyeong.account.core.enums.HouseholdRole;
import com.kyuhyeong.account.core.repository.HouseholdMemberRepository;
import com.kyuhyeong.account.core.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdminUserService} 단위 테스트 — Repository / PasswordEncoder 모킹.
 *
 * <p>핵심은 가구 격리 가드: {@code User} / {@code HouseholdMember} 는 {@code @Filter} 미적용이라
 * 서비스가 householdId 멤버십을 직접 검증해 타 가구 사용자 비번 변경을 차단해야 한다.
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock HouseholdMemberRepository memberRepository;
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks AdminUserService service;

    private static User user(long id, String email, String name, String passwordHash) {
        return User.builder().id(id).email(email).name(name).passwordHash(passwordHash).build();
    }

    private static HouseholdMember member(User u, HouseholdRole role) {
        return HouseholdMember.builder()
                .household(Household.builder().id(1L).build())
                .user(u)
                .role(role)
                .build();
    }

    @Test
    @DisplayName("listMembers — 멤버를 userId 오름차순 view 로 매핑")
    void listMembersMapsAndSorts() {
        User member1 = user(2L, "member1@example.com", "배우자", "h2");
        User owner = user(1L, "owner1@example.com", "본인", "h1");
        when(memberRepository.findByHouseholdId(1L)).thenReturn(List.of(
                member(member1, HouseholdRole.MEMBER),
                member(owner, HouseholdRole.OWNER)));

        List<MemberView> result = service.listMembers(1L);

        assertThat(result).extracting(MemberView::userId).containsExactly(1L, 2L);
        assertThat(result.get(0).role()).isEqualTo("OWNER");
        assertThat(result.get(1).email()).isEqualTo("member1@example.com");
    }

    @Test
    @DisplayName("resetPassword — 가구 멤버면 BCrypt 인코딩 후 해시 교체")
    void resetPasswordEncodesForMember() {
        User target = user(2L, "member1@example.com", "배우자", "old-hash");
        when(memberRepository.findByHouseholdIdAndUserId(1L, 2L))
                .thenReturn(Optional.of(member(target, HouseholdRole.MEMBER)));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(passwordEncoder.encode("newStrongPw")).thenReturn("$2a$10$encoded");

        service.resetPassword(1L, 2L, "newStrongPw");

        assertThat(target.getPasswordHash()).isEqualTo("$2a$10$encoded");
    }

    @Test
    @DisplayName("resetPassword — 타 가구 사용자면 거부 + 비번 변경 없음 (격리 가드)")
    void resetPasswordRejectsNonMember() {
        when(memberRepository.findByHouseholdIdAndUserId(1L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword(1L, 99L, "newStrongPw"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a member");

        verify(userRepository, never()).findById(99L);
        verify(passwordEncoder, never()).encode("newStrongPw");
    }
}
