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
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 순자산 — 자산 (v1.1, docs/account.md §6.1).
 *
 * <p>{@code recorded_at} 은 월 단위 스냅샷 (YYYY-MM-01). 화면은 v1.1 에서 추가.
 * 본 페이즈에서는 스키마/엔티티만.
 */
@Entity
@Table(
        name = "assets",
        indexes = @Index(name = "idx_assets_hid_recorded", columnList = "household_id, recorded_at")
)
@Filter(name = "householdFilter", condition = "household_id = :currentHouseholdId")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;

    @Column(name = "recorded_at", nullable = false)
    private LocalDate recordedAt;
}
