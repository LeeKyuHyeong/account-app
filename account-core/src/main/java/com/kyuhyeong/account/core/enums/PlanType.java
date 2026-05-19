package com.kyuhyeong.account.core.enums;

/**
 * 가구의 플랜 티어 (docs/account.md §6.1 households.plan_type).
 *
 * <p>MVP 는 전부 PERSONAL. v2 사업화 단계에서 In-App Purchase 와 매핑.
 *
 * <ul>
 *     <li>{@link #PERSONAL} 본인/부부 단계 (기본)</li>
 *     <li>{@link #FAMILY}   친구·가족 가구 (~20명, v1.5)</li>
 *     <li>{@link #PRO}      유료 티어 (v2)</li>
 * </ul>
 */
public enum PlanType {
    PERSONAL,
    FAMILY,
    PRO
}
