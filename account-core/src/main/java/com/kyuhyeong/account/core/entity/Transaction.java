package com.kyuhyeong.account.core.entity;

import com.kyuhyeong.account.core.enums.TransactionStatus;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 거래 (가계부 본체, docs/account.md §6.1).
 *
 * <p>영수증 업로드 → AI 분석으로 자동 생성되거나, 사용자가 직접 입력. 신뢰도에 따라
 * DRAFT/CONFIRMED 상태 (§3.2). 가구 멤버 모두 수정 가능 (§11 결정 #5) 하며, 모든 변경은
 * {@link TransactionHistory} 에 적재된다. 삭제는 soft (deletedAt 세팅).
 */
@Entity
@Table(
        name = "transactions",
        indexes = {
                @Index(name = "idx_tx_hid_occurred",
                       columnList = "household_id, occurred_at"),
                @Index(name = "idx_tx_hid_cat_occurred",
                       columnList = "household_id, category_id, occurred_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "merchant", length = 200)
    private String merchant;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "memo", length = 500)
    private String memo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id")
    private Receipt receipt;

    @Column(name = "confidence", precision = 4, scale = 3)
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private User updatedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public void confirm(User by) {
        this.status = TransactionStatus.CONFIRMED;
        this.updatedBy = by;
    }

    public void reassignCategory(Category category, User by) {
        this.category = category;
        this.updatedBy = by;
    }

    public void softDelete(User by) {
        this.deletedAt = LocalDateTime.now();
        this.updatedBy = by;
    }
}
