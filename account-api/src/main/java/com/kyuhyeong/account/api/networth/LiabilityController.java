package com.kyuhyeong.account.api.networth;

import com.kyuhyeong.account.api.networth.NetWorthDtos.CreateRequest;
import com.kyuhyeong.account.api.networth.NetWorthDtos.LiabilityResponse;
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

/** 부채 CRUD — {@link AssetController} 와 대칭. 조회는 {@code GET /api/networth/snapshot} 에 통합. */
@RestController
@RequestMapping("/api/liabilities")
@RequiredArgsConstructor
public class LiabilityController {

    private final NetWorthService netWorthService;

    @PostMapping
    public ResponseEntity<LiabilityResponse> create(@Valid @RequestBody CreateRequest request) {
        LiabilityResponse created = netWorthService.createLiability(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    public LiabilityResponse update(@PathVariable Long id, @Valid @RequestBody UpdateRequest request) {
        return netWorthService.updateLiability(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        netWorthService.deleteLiability(id);
        return ResponseEntity.noContent().build();
    }
}
