package com.kyuhyeong.account.api.networth;

import com.kyuhyeong.account.api.networth.NetWorthDtos.CreateRequest;
import com.kyuhyeong.account.api.networth.NetWorthDtos.SnapshotResponse;
import com.kyuhyeong.account.api.networth.NetWorthDtos.UpdateRequest;
import com.kyuhyeong.account.core.entity.Asset;
import com.kyuhyeong.account.core.entity.Household;
import com.kyuhyeong.account.core.entity.Liability;
import com.kyuhyeong.account.core.repository.AssetRepository;
import com.kyuhyeong.account.core.repository.HouseholdRepository;
import com.kyuhyeong.account.core.repository.LiabilityRepository;
import com.kyuhyeong.account.core.tenant.HouseholdContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link NetWorthService} 단위 테스트 — Repository 모킹.
 *
 * <p>가구 격리 자체는 Hibernate {@code householdFilter} 가 책임지며 본 서비스의 책임이
 * 아니다 (격리 검증은 {@code HouseholdIsolationIntegrationTest} 가 담당, 현재 Disabled).
 * 여기서는 합계 계산 / 부분 갱신 / 입력 검증만 확인.
 */
@ExtendWith(MockitoExtension.class)
class NetWorthServiceTest {

    @Mock AssetRepository assetRepository;
    @Mock LiabilityRepository liabilityRepository;
    @Mock HouseholdRepository householdRepository;

    @InjectMocks NetWorthService service;

    @BeforeEach
    void bindHousehold() {
        HouseholdContext.set(1L);
    }

    @AfterEach
    void clearHousehold() {
        HouseholdContext.clear();
    }

    @Test
    @DisplayName("snapshot: 자산/부채 합계 + 순자산(=자산-부채) 계산")
    void snapshotComputesTotals() {
        when(assetRepository.findAll(any(Specification.class))).thenReturn(List.of(
                assetWith(1L, "예금", new BigDecimal("1000000.00")),
                assetWith(2L, "주식", new BigDecimal("2500000.50"))
        ));
        when(liabilityRepository.findAll(any(Specification.class))).thenReturn(List.of(
                liabilityWith(10L, "전세대출", new BigDecimal("500000.25"))
        ));

        SnapshotResponse snapshot = service.snapshot(YearMonth.of(2026, 5));

        assertThat(snapshot.yearMonth()).isEqualTo("2026-05");
        assertThat(snapshot.assetsTotal()).isEqualByComparingTo("3500000.50");
        assertThat(snapshot.liabilitiesTotal()).isEqualByComparingTo("500000.25");
        assertThat(snapshot.netWorth()).isEqualByComparingTo("3000000.25");
        assertThat(snapshot.assets()).hasSize(2);
        assertThat(snapshot.liabilities()).hasSize(1);
    }

    @Test
    @DisplayName("snapshot: 데이터 없는 달은 합계 0, 빈 리스트")
    void snapshotHandlesEmpty() {
        when(assetRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(liabilityRepository.findAll(any(Specification.class))).thenReturn(List.of());

        SnapshotResponse snapshot = service.snapshot(YearMonth.of(2026, 1));

        assertThat(snapshot.assetsTotal()).isEqualByComparingTo("0");
        assertThat(snapshot.liabilitiesTotal()).isEqualByComparingTo("0");
        assertThat(snapshot.netWorth()).isEqualByComparingTo("0");
        assertThat(snapshot.assets()).isEmpty();
        assertThat(snapshot.liabilities()).isEmpty();
    }

    @Test
    @DisplayName("snapshot: 부채가 자산보다 크면 순자산 음수")
    void snapshotAllowsNegativeNetWorth() {
        when(assetRepository.findAll(any(Specification.class))).thenReturn(List.of(
                assetWith(1L, "현금", new BigDecimal("100000"))
        ));
        when(liabilityRepository.findAll(any(Specification.class))).thenReturn(List.of(
                liabilityWith(10L, "카드빚", new BigDecimal("300000"))
        ));

        SnapshotResponse snapshot = service.snapshot(YearMonth.of(2026, 5));

        assertThat(snapshot.netWorth()).isEqualByComparingTo("-200000");
    }

    @Test
    @DisplayName("createAsset: 가구 컨텍스트 자산을 recorded_at = YYYY-MM-01 로 저장")
    void createAssetNormalizesRecordedAt() {
        Household household = Household.builder().name("우리집").build();
        ReflectionTestUtils.setField(household, "id", 1L);
        when(householdRepository.getReferenceById(1L)).thenReturn(household);
        when(assetRepository.save(any(Asset.class))).thenAnswer(inv -> {
            Asset a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", 99L);
            return a;
        });

        var response = service.createAsset(new CreateRequest(
                "신한 적금", "예금", new BigDecimal("1500000"), "2026-05"));

        assertThat(response.id()).isEqualTo(99L);
        assertThat(response.name()).isEqualTo("신한 적금");
        assertThat(response.recordedAt()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(response.balance()).isEqualByComparingTo("1500000");
    }

    @Test
    @DisplayName("updateAsset: null 필드는 변경 없음, yearMonth 지정 시 1일로 정규화")
    void updateAssetPartialPatch() {
        Asset asset = Asset.builder()
                .name("기존")
                .type("기존타입")
                .balance(new BigDecimal("100"))
                .recordedAt(LocalDate.of(2026, 5, 1))
                .build();
        ReflectionTestUtils.setField(asset, "id", 7L);
        when(assetRepository.findById(7L)).thenReturn(Optional.of(asset));

        // 잔액만 변경, yearMonth 도 변경. 이름/타입은 그대로.
        var response = service.updateAsset(7L, new UpdateRequest(
                null, null, new BigDecimal("250"), "2026-06"));

        assertThat(response.name()).isEqualTo("기존");
        assertThat(response.type()).isEqualTo("기존타입");
        assertThat(response.balance()).isEqualByComparingTo("250");
        assertThat(response.recordedAt()).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    @DisplayName("updateAsset: 존재하지 않거나 다른 가구면 IllegalArgumentException")
    void updateAssetRejectsMissing() {
        when(assetRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateAsset(404L,
                new UpdateRequest(null, null, new BigDecimal("1"), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("404");
    }

    @Test
    @DisplayName("deleteAsset: 존재하면 repository.delete 호출")
    void deleteAssetCallsRepository() {
        Asset asset = Asset.builder().build();
        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
        service.deleteAsset(1L);
        verify(assetRepository, times(1)).delete(asset);
    }

    @Test
    @DisplayName("deleteAsset: 다른 가구이거나 미존재면 IllegalArgumentException + delete 호출 X")
    void deleteAssetRejectsMissing() {
        when(assetRepository.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteAsset(2L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(assetRepository, never()).delete(any(Asset.class));
    }

    @Test
    @DisplayName("history: from~to 미포함 구간을 월별로 합계 — 3개월")
    void historyReturnsPerMonthTotals() {
        // 2026-03 자산만 / 2026-04 부채만 / 2026-05 둘 다 — 가지각색 케이스. Mockito thenReturn
        // 체이닝은 호출 횟수 순서대로 다른 응답을 돌려준다.
        when(assetRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(assetWith(1L, "예금", new BigDecimal("1000000"))))   // 2026-03
                .thenReturn(List.of())                                                       // 2026-04
                .thenReturn(List.of(assetWith(2L, "주식", new BigDecimal("2500000"))));  // 2026-05
        when(liabilityRepository.findAll(any(Specification.class)))
                .thenReturn(List.of())                                                          // 2026-03
                .thenReturn(List.of(liabilityWith(10L, "카드", new BigDecimal("300000"))))  // 2026-04
                .thenReturn(List.of(liabilityWith(11L, "대출", new BigDecimal("500000"))));// 2026-05

        var points = service.history(YearMonth.of(2026, 3), YearMonth.of(2026, 6));

        assertThat(points).hasSize(3);
        assertThat(points.get(0).yearMonth()).isEqualTo("2026-03");
        assertThat(points.get(0).assetsTotal()).isEqualByComparingTo("1000000");
        assertThat(points.get(0).liabilitiesTotal()).isEqualByComparingTo("0");
        assertThat(points.get(0).netWorth()).isEqualByComparingTo("1000000");

        assertThat(points.get(1).yearMonth()).isEqualTo("2026-04");
        assertThat(points.get(1).assetsTotal()).isEqualByComparingTo("0");
        assertThat(points.get(1).liabilitiesTotal()).isEqualByComparingTo("300000");
        assertThat(points.get(1).netWorth()).isEqualByComparingTo("-300000");

        assertThat(points.get(2).yearMonth()).isEqualTo("2026-05");
        assertThat(points.get(2).assetsTotal()).isEqualByComparingTo("2500000");
        assertThat(points.get(2).liabilitiesTotal()).isEqualByComparingTo("500000");
        assertThat(points.get(2).netWorth()).isEqualByComparingTo("2000000");
    }

    @Test
    @DisplayName("history: from >= to 면 IllegalArgumentException")
    void historyRejectsInvertedRange() {
        assertThatThrownBy(() -> service.history(YearMonth.of(2026, 5), YearMonth.of(2026, 5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be before");
    }

    @Test
    @DisplayName("history: 24개월 초과 요청은 IllegalArgumentException")
    void historyRejectsTooLongRange() {
        assertThatThrownBy(() -> service.history(YearMonth.of(2024, 1), YearMonth.of(2026, 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("24 months");
    }

    @Test
    @DisplayName("yearMonth 형식 오류는 IllegalArgumentException + 메시지에 입력값 포함")
    void yearMonthParseFails() {
        assertThatThrownBy(() -> NetWorthService.parseYearMonth("2026/05"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2026/05");
    }

    private static Asset assetWith(Long id, String name, BigDecimal balance) {
        Asset a = Asset.builder()
                .name(name).type("예금").balance(balance)
                .recordedAt(LocalDate.of(2026, 5, 1))
                .build();
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    private static Liability liabilityWith(Long id, String name, BigDecimal balance) {
        Liability l = Liability.builder()
                .name(name).type("대출").balance(balance)
                .recordedAt(LocalDate.of(2026, 5, 1))
                .build();
        ReflectionTestUtils.setField(l, "id", id);
        return l;
    }
}
