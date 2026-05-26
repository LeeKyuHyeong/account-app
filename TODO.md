# Account-App TODO

> **방향 전환 (2026-05-26):** 클라이언트를 **Flutter → Thymeleaf SSR 로 마이그레이션** 중.
> 사유: Java 백엔드 개발자로 커리어 방향을 확고히 하기 위함.
>
> **확정 결정 (2026-05-26):**
> - **전부 마이그레이션** — Flutter 8개 화면 그룹(로그인/홈/거래목록·입력/영수증/추이/예산/순자산) 전체를 Thymeleaf 로.
> - **순수 SSR 단일화** — 완료 후 `flutter_app/` + JWT/REST `apiChain` 모두 제거, 세션+Thymeleaf 단일 구조.
> - **Thymeleaf + 최소 JS** — 차트는 Chart.js(CDN), 목록은 페이지네이션. SPA/HTMX 도입 안 함.
> - 단계 진행 중에는 `apiChain` 유지(안 깨지게) → 마지막 정리 단계(M4)에서 일괄 제거.

작업 백로그 — 우선순위 + 마이그레이션 단계 기반.

- **P0**: 마이그레이션 핵심 경로 — 지금 진행
- **P1**: 대시보드 / 부가 화면
- **P2**: 정리 (순수 SSR 단일화) + 문서 최신화
- 상세 도메인 컨텍스트는 [`docs/account.md`](docs/account.md) (아직 Flutter 기준 서술 → 마이그레이션 완료 후 최신화 예정).

---

## 진행 현황 (요약)

```
M0 기반:    ▓▓▓▓▓▓▓  레이아웃 + app.css + 에러페이지 완료
M1 핵심:    ▓▓▓▓▓▓▓  홈 + 거래 목록/입력/수정 완료 (findById 격리 누수 수정 포함)
M2 영수증:  ░░░░░░░  업로드 → 분석 → 컨펌
M3 대시보드:░░░░░░░  추이 차트 + 예산 + 순자산
M4 정리:    ░░░░░░░  apiChain/flutter_app 제거 + 문서 최신화 (전체 완료 후)
```

---

## 재사용하는 백엔드 기반 (그대로 둠)

마이그레이션은 **뷰 계층 교체**다. 아래는 이미 완성되어 그대로 재사용한다 — 서비스/도메인 재작성 금지.

- 도메인 Entity 12개 + Multi-tenant 격리(`HouseholdContext` + Hibernate `@Filter`) + Flyway(V1~V3).
- 비즈니스 서비스: `TransactionService`(목록 Specification / 생성 / PATCH / soft-delete), `ReceiptIngestionService`(영수증 → DRAFT 거래), `MonthlySummaryService`(월별 / 시계열 집계), `MerchantHistoryService`(학습 UPSERT), 순자산 / 예산 서비스.
- `account-ai`(Claude Vision 분석), `account-batch`(월말 집계 잡).
- **Web 컨트롤러는 이 서비스들을 그대로 호출**하고 Thymeleaf 뷰만 반환한다.

> 세션 진입 시 `SessionHouseholdContextFilter` 가 세션 principal 의 활성 가구 ID 로 `HouseholdContext` 를 채운다 (기존 JWT 필터의 역할 대체). 격리 동작은 동일.

---

## P0 — M0. 마이그레이션 기반

- [x] `webChain` 추가 (`SecurityConfig` 듀얼 체인, formLogin + 세션 + CSRF)
- [x] `CustomUserDetails` / `CustomUserDetailsService` (email 로그인, BCrypt 검증)
- [x] `SessionHouseholdContextFilter` — 세션 principal 의 활성 가구 ID → `HouseholdContext`
- [x] `WebAuthController` + `templates/auth/login.html` + fragments(head / navbar / scripts)
- [x] 공통 base 레이아웃 데코레이터 `fragments/layout.html` — `page(title, content)` (head + navbar + main + scripts 합성). Chart.js 는 차트 페이지(M3)에서 페이지별 include
- [x] 정적 리소스 — `static/css/app.css` (모바일 우선, main max-width 640px). 앱 전용 JS 는 필요 시점에 추가 (현재 Bootstrap 번들로 충분)
- [x] 에러 페이지 (`templates/error.html`, 독립형) + 로그아웃 동작 확인
- [ ] `WebPingController`(PR1 검증 스텁) → M1 홈으로 교체 후 제거
- 검증: ✅ owner1@example.com 로그인 → `/web/ping` 레이아웃 렌더 + principal 이메일 표시, `/css/app.css` 200, 404/500 → error.html, 로그아웃 302 (별도 8081 인스턴스로 curl 검증)

## P0 — M1. 홈 + 거래 (핵심 경로)

대체 대상: `home_screen` / `transaction_list_screen` / `transaction_form_screen`

- [x] **홈** (`GET /web/home`, `WebHomeController`) — 이번 달 수입 / 지출 / 잉여 / 투자 카드 + 예산 초과 경고 banner + 거래 목록/입력 내비. `MonthlySummaryService.get` 재사용
- [x] **거래 목록** (`GET /web/transactions`) — 날짜별 그룹, 필터(from / to / categoryId / status), **페이지네이션**, DRAFT 뱃지. `TransactionService.list` 재사용
- [x] **거래 입력** (`GET/POST /web/transactions/new`) — amount / category / occurredAt / merchant / paymentMethod / memo. record DTO @ModelAttribute + @Valid → BindingResult, 에러 시 제출 원본값(`form` Map) + 필드에러(`errors` Map) 재렌더 (th:field/setter 미사용)
- [x] **거래 수정** (`GET/POST /web/transactions/{id}`) — 카테고리 변경 + DRAFT→CONFIRMED 확정. `TransactionService.get`(신규) + `update` 재사용
- [x] 통화 / 날짜 포맷 — 서버 측 `#numbers` / `#temporals`
- [x] `WebPingController` + `_ping.html` 제거, `defaultSuccessUrl` → `/web/home`. navbar 에 거래 링크
- [x] **🔒 격리 누수 수정**: `TransactionService.get`/`update` 가 `findById`(=`EntityManager.find`, PK 직접 로드라 Hibernate `@Filter` 미적용 → 타 가구 거래 노출/수정 가능) 사용하던 것을 `findOne(Specification)`(criteria 쿼리 → 필터 적용) 로 교체. **`update` 는 기존 REST PATCH 에도 있던 누수였음**
- 검증: ✅ owner1 입력 → 목록/홈 반영 → 수정. 검증 에러 필드별 표시. 한글 merchant/memo 정상(기본 UTF-8). 격리: owner2 → owner1 거래 조회/수정 시 0건/404·400, 카테고리 22 vs 5

> **폼 바인딩 컨벤션 (M1~M3 공통)**: th:field/setter 안 씀. record DTO 로 받고, 에러 시 제출 원본 Map(`form`) 으로 재표시 + `errors` Map 으로 필드 에러. Map 접근은 대괄호 인덱서(`${form['x']}`) — 점 접근은 없는 키에서 SpEL 예외.

## P1 — M2. 영수증

대체 대상: `receipt_capture_screen` / `receipt_confirmation_screen`

- [ ] **업로드 폼** (`GET /web/receipts/new`) — `<input type="file" accept="image/*" capture>` (모바일 브라우저 카메라). 서버 multipart 10MB 제한 활용. 클라 압축은 일단 생략(필요 시 canvas 리사이즈 최소 JS 로 추가)
- [ ] **분석 → 컨펌** (`POST /web/receipts`) — `ReceiptIngestionService.ingest` 호출 → 컨펌 페이지 렌더
- [ ] 신뢰도 분기 UI — ≥0.8 자동확정 권장 / 0.5~0.8 확인요청 / <0.5 수동변경 강제 (서버 렌더 + 버튼 활성/비활성)
- [ ] 컨펌 → DRAFT→CONFIRMED → 거래 목록으로 리다이렉트
- 검증: 실제 영수증 이미지 + Claude 키 환경에서 업로드 → DRAFT 생성 → 컨펌 (사용자 수동).

## P1 — M3. 대시보드

대체 대상: `trend_screen` / `budget_screen` / `networth_screen` / `networth_form_screen` / `networth_trend_screen`

- [ ] **추이 차트** (`GET /web/trend`) — 시계열 집계 → Chart.js 라인(수입 / 지출 / 잉여), 최근 6개월 기본
- [ ] **예산 설정** (`GET/POST /web/budget`) — 카테고리별 예산 편집 + 진행률 bar
- [ ] **순자산** (`GET /web/networth`) — 자산 / 부채 목록·CRUD + 스냅샷 + 12개월 추이(Chart.js, 자산 / 부채 / 순자산 3선)
- 검증: 각 페이지 데이터 정확성 + 차트 렌더 + 가구 격리.

## P2 — M4. 정리 (순수 SSR 단일화)

**모든 화면 마이그레이션 완료 후 진행. 순서 중요 — 화면 다 옮긴 뒤에.**

- [ ] `SecurityConfig` 에서 `apiChain` 제거 → `webChain` 단일 체인
- [ ] JWT 인프라 제거 — `JwtAuthenticationFilter` / `JwtTokenProvider` / `JwtProperties` / `jwtFilterRegistration`
- [ ] REST 컨트롤러 + 전용 서비스 제거 — `/api/**` (`AuthController` / `AuthService` / `TransactionController` / `ReceiptController` / `SummaryController` / `NetWorthController` / `AssetController` / `LiabilityController` / `CategoryController`). 로직은 Web 컨트롤러로 이전 완료 후 `@RestController` 버전 삭제
- [ ] `AuthDtos` / REST 전용 DTO 정리 (Web 폼 DTO 와 중복 시)
- [ ] `GlobalExceptionHandler`(@RestControllerAdvice) 를 `/api` 로 범위 제한 — 현재 웹 컨트롤러의 `IllegalArgumentException`(없는 거래 id 조회 등) 도 JSON 400 으로 처리됨. 웹은 error.html 로 가도록
- [ ] `account.jwt.*` 설정 + `application-secret.yml` 의 jwt 블록 제거
- [ ] **`flutter_app/` 디렉터리 삭제**
- [ ] CI 에서 Flutter 잡 제거 (`.github/workflows/ci.yml`)
- [ ] Android signing 자산 제거 (`key.properties` 패턴, `docs/deployment.md` §7)
- [ ] **(사용자) CLAUDE.md + `docs/account.md` 최신화** — Flutter 기준 서술 → SSR 기준으로. *전체 완료 시점에 사용자가 직접 진행.*

---

## 상시 / 가로지르는 항목

### 보안 / 시크릿 (모든 커밋 전)
- [ ] `git diff --cached | grep -iE "sk-ant|password.*=.*[a-zA-Z0-9]{8}|secret.*=.*[a-zA-Z0-9]{16}"` 결과 비어있는지 확인
- [ ] `application-secret.yml` 미커밋 확인
- [ ] **CSRF** — webChain 은 CSRF 활성. 모든 POST 폼에 `_csrf` 히든 필드 포함. 임의로 끄지 말 것

### 테스트 정책
- [ ] 컨트롤러 추가 시 MockMvc / `@SpringBootTest` 통합 테스트 (DB 는 Testcontainers, H2 금지)
- [ ] 격리 검증은 가구 2개 세션으로 (owner1 → 22, owner2 → 5, 익명 → `/login` 리다이렉트)
- [ ] AssertJ

### 보류 (환경 이슈)
- [ ] `HouseholdIsolationIntegrationTest` `@Disabled` — Windows + Docker Desktop + Testcontainers 비호환. Linux CI 시 재활성화

---

*이 파일은 마이그레이션 백로그다. 단계 완료 시 체크박스 + 진행 현황 막대를 갱신한다. 전체 완료 후 CLAUDE.md / `docs/account.md` 최신화 (사용자).*
