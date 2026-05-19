package com.kyuhyeong.account.core.enums;

/**
 * 거래 변경 이력의 변경 유형 (docs/account.md §6.1 transaction_history).
 *
 * <p>가구 멤버 모두 거래 수정 가능 결정 (§11 결정 #5) 에 따른 감사 로그 분류.
 *
 * <ul>
 *     <li>{@link #CREATE} 새 거래 생성 (after_json 만 존재)</li>
 *     <li>{@link #UPDATE} 기존 거래 수정 (before_json + after_json)</li>
 *     <li>{@link #DELETE} soft delete (before_json 만 존재)</li>
 * </ul>
 */
public enum ChangeType {
    CREATE,
    UPDATE,
    DELETE
}
