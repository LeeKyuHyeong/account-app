package com.kyuhyeong.account.core.entity;

import com.kyuhyeong.account.core.enums.ChangeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

/**
 * 거래 변경 이력 / 감사 로그 (docs/account.md §6.1, §11 결정 #5).
 *
 * <p>가구 멤버 모두 거래 수정 가능 결정에 대응하는 추적 메커니즘. 모든 거래 변경 시 1행 적재.
 */
@Entity
@Table(
        name = "transaction_history",
        indexes = @Index(name = "idx_th_tx_changed", columnList = "transaction_id, changed_at")
)
@Filter(name = "householdFilter", condition = "household_id = :currentHouseholdId")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class TransactionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_user_id", nullable = false)
    private User changedBy;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    private ChangeType changeType;

    @Lob
    @Column(name = "before_json", columnDefinition = "LONGTEXT")
    private String beforeJson;

    @Lob
    @Column(name = "after_json", columnDefinition = "LONGTEXT")
    private String afterJson;
}
