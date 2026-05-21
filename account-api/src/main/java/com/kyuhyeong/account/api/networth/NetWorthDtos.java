package com.kyuhyeong.account.api.networth;

import com.kyuhyeong.account.core.entity.Asset;
import com.kyuhyeong.account.core.entity.Liability;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 순자산 API (자산 / 부채 / 스냅샷) 의 입출력 DTO 모음. */
public final class NetWorthDtos {

    /** YYYY-MM (e.g. 2026-05). 서비스에서 YYYY-MM-01 의 {@link LocalDate} 로 정규화. */
    static final String YEAR_MONTH_PATTERN = "^\\d{4}-(0[1-9]|1[0-2])$";

    private NetWorthDtos() {
    }

    public record AssetResponse(
            Long id,
            String name,
            String type,
            BigDecimal balance,
            LocalDate recordedAt
    ) {
        public static AssetResponse from(Asset a) {
            return new AssetResponse(
                    a.getId(), a.getName(), a.getType(), a.getBalance(), a.getRecordedAt());
        }
    }

    public record LiabilityResponse(
            Long id,
            String name,
            String type,
            BigDecimal balance,
            LocalDate recordedAt
    ) {
        public static LiabilityResponse from(Liability l) {
            return new LiabilityResponse(
                    l.getId(), l.getName(), l.getType(), l.getBalance(), l.getRecordedAt());
        }
    }

    /**
     * 자산 / 부채 생성 — 잔액은 0 이상 (값평가가 음수가 될 수 있더라도 v1.1 에서는 비-음수만
     * 허용한다. 정말 음수가 필요해지면 삭제 후 부채로 옮기거나 별도 PR 에서 확장).
     */
    public record CreateRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 50) String type,
            @NotNull @DecimalMin("0.00")
            @Digits(integer = 13, fraction = 2) BigDecimal balance,
            @NotBlank @Pattern(regexp = YEAR_MONTH_PATTERN,
                    message = "yearMonth must be YYYY-MM (e.g. 2026-05)")
            String yearMonth
    ) {
    }

    /** 부분 수정 — null 인 필드는 변경 없음. yearMonth 도 변경 가능 (월을 잘못 입력했을 때). */
    public record UpdateRequest(
            @Size(max = 100) String name,
            @Size(max = 50) String type,
            @DecimalMin("0.00")
            @Digits(integer = 13, fraction = 2) BigDecimal balance,
            @Pattern(regexp = YEAR_MONTH_PATTERN,
                    message = "yearMonth must be YYYY-MM (e.g. 2026-05)")
            String yearMonth
    ) {
    }

    /**
     * 순자산 스냅샷 — 한 달의 자산/부채 합계 + 항목 리스트.
     *
     * @param yearMonth         대상 월 (ISO YYYY-MM)
     * @param assetsTotal       자산 합계 (>= 0)
     * @param liabilitiesTotal  부채 합계 (>= 0)
     * @param netWorth          assetsTotal - liabilitiesTotal (음수 가능)
     */
    public record SnapshotResponse(
            String yearMonth,
            BigDecimal assetsTotal,
            BigDecimal liabilitiesTotal,
            BigDecimal netWorth,
            List<AssetResponse> assets,
            List<LiabilityResponse> liabilities
    ) {
    }
}
