package com.kyuhyeong.account.api.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * 세션 인증 경로 (/web/**) 의 principal.
 *
 * <p>JWT 경로(/api/**) 의 principal 은 여전히 {@code Long userId} 만 사용한다
 * ({@link JwtAuthenticationFilter} 참조). 세션 경로에서는 매 요청마다 DB 를 다시 치지 않도록
 * 활성 가구 ID + 역할을 principal 에 동봉하고, {@link SessionHouseholdContextFilter} 가
 * 이 값으로 {@code HouseholdContext} 를 채운다.
 *
 * <p>Serializable — Spring Security 가 HttpSession 에 SecurityContext 를 직렬화 저장.
 */
public final class CustomUserDetails implements UserDetails, Serializable {

    private final Long userId;
    private final Long activeHouseholdId;
    private final String role;
    private final String email;
    private final String passwordHash;

    public CustomUserDetails(Long userId,
                             Long activeHouseholdId,
                             String role,
                             String email,
                             String passwordHash) {
        this.userId = userId;
        this.activeHouseholdId = activeHouseholdId;
        this.role = role;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getActiveHouseholdId() {
        return activeHouseholdId;
    }

    public String getRole() {
        return role;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return true; }
}
