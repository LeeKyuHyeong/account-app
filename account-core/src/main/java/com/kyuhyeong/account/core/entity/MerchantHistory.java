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

import java.time.LocalDateTime;

/**
 * 가구별 가맹점 학습 (docs/account.md §3.2).
 *
 * <p>{@code (household_id, merchant_name)} 유니크. 사용자가 거래 카테고리를 수정하면
 * UPSERT 되어 다음 영수증 분석 시 프롬프트 컨텍스트로 주입된다.
 */
@Entity
@Table(
        name = "merchant_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mh_household_merchant",
                columnNames = {"household_id", "merchant_name"}
        )
)
@Filter(name = "householdFilter", condition = "household_id = :currentHouseholdId")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MerchantHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @Column(name = "merchant_name", nullable = false, length = 200)
    private String merchantName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "count", nullable = false)
    private int count;

    @Column(name = "last_used_at", nullable = false)
    private LocalDateTime lastUsedAt;

    public void touchUsage(Category category) {
        this.category = category;
        this.count++;
        this.lastUsedAt = LocalDateTime.now();
    }
}
