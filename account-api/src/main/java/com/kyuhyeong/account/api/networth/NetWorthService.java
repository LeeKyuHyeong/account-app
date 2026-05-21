package com.kyuhyeong.account.api.networth;

import com.kyuhyeong.account.api.networth.NetWorthDtos.AssetResponse;
import com.kyuhyeong.account.api.networth.NetWorthDtos.CreateRequest;
import com.kyuhyeong.account.api.networth.NetWorthDtos.HistoryPoint;
import com.kyuhyeong.account.api.networth.NetWorthDtos.LiabilityResponse;
import com.kyuhyeong.account.api.networth.NetWorthDtos.SnapshotResponse;
import com.kyuhyeong.account.api.networth.NetWorthDtos.UpdateRequest;
import com.kyuhyeong.account.core.entity.Asset;
import com.kyuhyeong.account.core.entity.Household;
import com.kyuhyeong.account.core.entity.Liability;
import com.kyuhyeong.account.core.repository.AssetRepository;
import com.kyuhyeong.account.core.repository.HouseholdRepository;
import com.kyuhyeong.account.core.repository.LiabilityRepository;
import com.kyuhyeong.account.core.tenant.HouseholdContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 순자산 (자산 + 부채) 의 CRUD + 월별 스냅샷 집계.
 *
 * <p>가구 격리는 Hibernate {@code householdFilter} 가 자동 적용 — Specification 의 where
 * 절은 가구 조건을 포함하지 않는다. 월별 스냅샷은 한 달 (YYYY-MM-01) 의 자산/부채 row 합계를
 * 메모리에서 계산한다. 한 가구의 자산/부채 개수가 적어 (~수십 개) 충분히 빠르다.
 */
@Service
@RequiredArgsConstructor
public class NetWorthService {

    /** 추이 호출 한 번에 응답 가능한 최대 개월 수 (UI 차트가 의미있게 그릴 범위). */
    private static final int MAX_HISTORY_MONTHS = 24;

    private final AssetRepository assetRepository;
    private final LiabilityRepository liabilityRepository;
    private final HouseholdRepository householdRepository;

    @Transactional(readOnly = true)
    public SnapshotResponse snapshot(YearMonth yearMonth) {
        LocalDate recordedAt = yearMonth.atDay(1);

        List<Asset> assets = assetRepository.findAll(monthMatches("recordedAt", recordedAt));
        List<Liability> liabilities = liabilityRepository.findAll(monthMatches("recordedAt", recordedAt));

        BigDecimal assetsTotal = sum(assets, Asset::getBalance);
        BigDecimal liabilitiesTotal = sum(liabilities, Liability::getBalance);
        BigDecimal netWorth = assetsTotal.subtract(liabilitiesTotal);

        List<AssetResponse> assetItems = assets.stream()
                .sorted(Comparator.comparing(Asset::getId))
                .map(AssetResponse::from)
                .toList();
        List<LiabilityResponse> liabilityItems = liabilities.stream()
                .sorted(Comparator.comparing(Liability::getId))
                .map(LiabilityResponse::from)
                .toList();

        return new SnapshotResponse(
                yearMonth.toString(),
                assetsTotal, liabilitiesTotal, netWorth,
                assetItems, liabilityItems
        );
    }

    /**
     * 월별 합계 추이 — {@code from} 부터 {@code to} 미만까지 (반-개구간). 차트용으로 항목
     * 리스트는 제외하고 합계만. {@link MonthlySummaryService#series} 와 동일한 호출 패턴이다 —
     * 한 달치 합계를 N 번 조회 (매 호출이 2 쿼리). 자산/부채 row 수가 작아 (~수십) 충분.
     */
    @Transactional(readOnly = true)
    public List<HistoryPoint> history(YearMonth from, YearMonth to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from / to are required");
        }
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException(
                    "from (" + from + ") must be before to (" + to + "), to is exclusive");
        }
        long months = ChronoUnit.MONTHS.between(from, to);
        if (months > MAX_HISTORY_MONTHS) {
            throw new IllegalArgumentException(
                    "history range exceeds " + MAX_HISTORY_MONTHS + " months: " + months);
        }
        List<HistoryPoint> result = new ArrayList<>((int) months);
        for (YearMonth ym = from; ym.isBefore(to); ym = ym.plusMonths(1)) {
            LocalDate recordedAt = ym.atDay(1);
            BigDecimal a = sum(
                    assetRepository.findAll(monthMatches("recordedAt", recordedAt)),
                    Asset::getBalance);
            BigDecimal l = sum(
                    liabilityRepository.findAll(monthMatches("recordedAt", recordedAt)),
                    Liability::getBalance);
            result.add(new HistoryPoint(ym.toString(), a, l, a.subtract(l)));
        }
        return result;
    }

    @Transactional
    public AssetResponse createAsset(CreateRequest request) {
        Long householdId = HouseholdContext.get();
        Household household = householdRepository.getReferenceById(householdId);
        Asset asset = Asset.builder()
                .household(household)
                .name(request.name())
                .type(request.type())
                .balance(request.balance())
                .recordedAt(parseYearMonth(request.yearMonth()).atDay(1))
                .build();
        return AssetResponse.from(assetRepository.save(asset));
    }

    @Transactional
    public AssetResponse updateAsset(Long id, UpdateRequest request) {
        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Asset not found or not in current household: " + id));
        asset.edit(
                request.name(), request.type(), request.balance(),
                request.yearMonth() == null ? null : parseYearMonth(request.yearMonth()).atDay(1)
        );
        return AssetResponse.from(asset);
    }

    @Transactional
    public void deleteAsset(Long id) {
        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Asset not found or not in current household: " + id));
        assetRepository.delete(asset);
    }

    @Transactional
    public LiabilityResponse createLiability(CreateRequest request) {
        Long householdId = HouseholdContext.get();
        Household household = householdRepository.getReferenceById(householdId);
        Liability liability = Liability.builder()
                .household(household)
                .name(request.name())
                .type(request.type())
                .balance(request.balance())
                .recordedAt(parseYearMonth(request.yearMonth()).atDay(1))
                .build();
        return LiabilityResponse.from(liabilityRepository.save(liability));
    }

    @Transactional
    public LiabilityResponse updateLiability(Long id, UpdateRequest request) {
        Liability liability = liabilityRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Liability not found or not in current household: " + id));
        liability.edit(
                request.name(), request.type(), request.balance(),
                request.yearMonth() == null ? null : parseYearMonth(request.yearMonth()).atDay(1)
        );
        return LiabilityResponse.from(liability);
    }

    @Transactional
    public void deleteLiability(Long id) {
        Liability liability = liabilityRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Liability not found or not in current household: " + id));
        liabilityRepository.delete(liability);
    }

    private static <T> Specification<T> monthMatches(String field, LocalDate firstDayOfMonth) {
        return (root, cq, cb) -> cb.equal(root.get(field), firstDayOfMonth);
    }

    private static <T> BigDecimal sum(List<T> items, java.util.function.Function<T, BigDecimal> mapper) {
        return items.stream().map(mapper).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static YearMonth parseYearMonth(String raw) {
        try {
            return YearMonth.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "yearMonth must be YYYY-MM format (e.g. 2026-05), got: " + raw);
        }
    }
}
