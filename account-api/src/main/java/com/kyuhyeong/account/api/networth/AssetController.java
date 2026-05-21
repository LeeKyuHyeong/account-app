package com.kyuhyeong.account.api.networth;

import com.kyuhyeong.account.api.networth.NetWorthDtos.AssetResponse;
import com.kyuhyeong.account.api.networth.NetWorthDtos.CreateRequest;
import com.kyuhyeong.account.api.networth.NetWorthDtos.UpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 자산 CRUD — 조회는 {@link NetWorthController#snapshot} 의 응답에 통합되어 있으므로 본
 * 컨트롤러는 mutation 전용 (POST / PATCH / DELETE).
 *
 * <p>가구 격리는 JWT 클레임에서 추출된 {@code HouseholdContext} 가 Hibernate filter 로
 * 자동 적용.
 */
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final NetWorthService netWorthService;

    @PostMapping
    public ResponseEntity<AssetResponse> create(@Valid @RequestBody CreateRequest request) {
        AssetResponse created = netWorthService.createAsset(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    public AssetResponse update(@PathVariable Long id, @Valid @RequestBody UpdateRequest request) {
        return netWorthService.updateAsset(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        netWorthService.deleteAsset(id);
        return ResponseEntity.noContent().build();
    }
}
