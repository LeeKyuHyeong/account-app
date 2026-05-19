package com.kyuhyeong.account.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;

/**
 * 월별 카테고리 집계 (docs/account.md §6.1).
 *
 * <p>Spring Batch 잡으로 사전 계산. 대시보드 화면 빠른 응답 + 통계 쿼리 부하 분산 용도.
 *
 * <p>주의: {@code year_month} 컬럼은 MariaDB 예약어라 {@code @Column} 에서 백틱 인용.
 * Flyway SQL 도 동일.
 */
@Entity
@Table(
        name = "monthly_summaries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ms",
                columnNames = {"household_id", "year_month", "category_id"}
        )
)
@Filter(name = "householdFilter", condition = "household_id = :currentHouseholdId")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MonthlySummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @Column(name = "`year_month`", nullable = false, columnDefinition = "CHAR(7)")
    private String yearMonth;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "transaction_count", nullable = false)
    private int transactionCount;

    public void refresh(BigDecimal totalAmount, int transactionCount) {
        this.totalAmount = totalAmount;
        this.transactionCount = transactionCount;
    }
}
