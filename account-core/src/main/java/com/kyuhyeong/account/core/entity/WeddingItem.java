package com.kyuhyeong.account.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 결혼 일시 지출 (v1.1, docs/account.md §6.1).
 *
 * <p>예산 vs 실제 진행률, 부모 지원 분리 표시용. 해당 가구만 사용.
 */
@Entity
@Table(
        name = "wedding_items",
        indexes = @Index(name = "idx_wedding_household", columnList = "household_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class WeddingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @Column(name = "section", nullable = false, length = 100)
    private String section;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "budget", nullable = false, precision = 15, scale = 2)
    private BigDecimal budget;

    @Column(name = "actual", nullable = false, precision = 15, scale = 2)
    private BigDecimal actual;

    @Column(name = "parent_support", nullable = false, precision = 15, scale = 2)
    private BigDecimal parentSupport;

    @Column(name = "memo", length = 500)
    private String memo;

    @Column(name = "paid_at")
    private LocalDate paidAt;
}
