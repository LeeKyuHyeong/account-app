package com.kyuhyeong.account.core.enums;

/**
 * 가구 내 사용자 역할 (docs/account.md §6.1 household_members).
 *
 * <p>MVP 에서는 둘 다 동일 권한 (거래 추가/수정 가능). v1.5 부터 OWNER 만
 * 예산 수정·멤버 초대 등의 권한 차등 부여.
 */
public enum HouseholdRole {
    OWNER,
    MEMBER
}
