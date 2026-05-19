package com.kyuhyeong.account.core.enums;

/**
 * 거래 상태 (docs/account.md §3.2 신뢰도 기반 처리).
 *
 * <ul>
 *     <li>{@link #DRAFT}     영수증 AI 분석 직후 또는 사용자 컨펌 대기 상태</li>
 *     <li>{@link #CONFIRMED} 사용자 컨펌 완료 또는 confidence ≥ 0.8 자동 확정</li>
 * </ul>
 */
public enum TransactionStatus {
    DRAFT,
    CONFIRMED
}
