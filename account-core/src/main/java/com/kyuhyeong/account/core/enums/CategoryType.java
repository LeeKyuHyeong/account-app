package com.kyuhyeong.account.core.enums;

/**
 * 카테고리 분류 (docs/account.md §2.3).
 *
 * <ul>
 *     <li>{@link #INCOME}    수입</li>
 *     <li>{@link #FIXED}     고정지출</li>
 *     <li>{@link #VARIABLE}  변동지출 — AI 분류가 가장 많이 매핑되는 영역</li>
 *     <li>{@link #INVEST}    투자/저축</li>
 * </ul>
 */
public enum CategoryType {
    INCOME,
    FIXED,
    VARIABLE,
    INVEST
}
