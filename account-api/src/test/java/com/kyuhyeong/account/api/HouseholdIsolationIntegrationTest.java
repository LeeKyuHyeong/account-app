package com.kyuhyeong.account.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyuhyeong.account.api.controller.CategoryController.CategoryDto;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 본 프로젝트 가장 중요한 검증 (docs/account.md §8.2 Task 4).
 *
 * <p>가구#1 / 가구#2 두 가구에 의도적으로 다른 카테고리 수를 시드한 상태 (V2: 22 vs 5) 에서
 * 같은 엔드포인트를 다른 가구 헤더로 호출해 결과가 격리되는지 검증한다. 둘의 ID 교집합이
 * 0 임도 확인 — 행 수만 맞으면 통과하는 약한 검증을 피하기 위함.
 *
 * <p>Testcontainers 로 매 빌드 새 MariaDB 인스턴스에 V1/V2 마이그레이션 자동 적용.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("integration")
@Disabled(
        "Docker Desktop on Windows 와 Testcontainers 의 알려진 비호환 — Desktop 의 CLI 프록시가 "
                + "named-pipe 응답을 가로채 docker-java 가 BadRequest 로 인식한다. Linux CI 또는 "
                + "Docker Desktop TCP 노출 활성화 환경에서 본 어노테이션 제거하면 자동 실행."
)
class HouseholdIsolationIntegrationTest {

    @Container
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.4");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mariadb::getJdbcUrl);
        r.add("spring.datasource.username", mariadb::getUsername);
        r.add("spring.datasource.password", mariadb::getPassword);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void categoriesAreIsolatedByHousehold() throws Exception {
        List<CategoryDto> h1 = fetch(1L);
        List<CategoryDto> h2 = fetch(2L);

        assertThat(h1).as("우리집 카테고리는 V2 시드대로 22개").hasSize(22);
        assertThat(h2).as("테스트가구 카테고리는 V2 시드대로 5개").hasSize(5);

        Set<Long> h1Ids = h1.stream().map(CategoryDto::id).collect(java.util.stream.Collectors.toSet());
        Set<Long> h2Ids = h2.stream().map(CategoryDto::id).collect(java.util.stream.Collectors.toSet());
        assertThat(h1Ids).as("가구 간 ID 교집합 = 0 (격리 누수 없음)")
                .doesNotContainAnyElementsOf(h2Ids);
    }

    @Test
    void missingHouseholdHeaderYieldsEmptyResult() throws Exception {
        // HouseholdContext 미바인딩 상태 → aspect 가 sentinel (-1) 로 filter 활성화 →
        // 어느 household_id 와도 매칭 안 됨 → 빈 결과. Fail-safe default.
        // Task 5 의 JWT 필터가 인증 미통과 요청을 401 로 차단하면 본 경로는 실제 트래픽에서
        // 사라지지만, 본 sentinel 은 두 번째 방어선으로 남는다.
        List<CategoryDto> result = json.readValue(
                mvc.perform(get("/api/categories"))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                new TypeReference<List<CategoryDto>>() {
                });
        assertThat(result).as("ctx 미설정 시 격리 엔티티는 빈 결과만 노출").isEmpty();
    }

    private List<CategoryDto> fetch(long householdId) throws Exception {
        String body = mvc.perform(get("/api/categories")
                        .header("X-Household-Id", String.valueOf(householdId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readValue(body, new TypeReference<List<CategoryDto>>() {
        });
    }
}
