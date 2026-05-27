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
M2 영수증:  ▓▓▓▓▓▓▓  업로드 → 실제 Claude 분석 → 컨펌 검증 완료 (직렬화 버그 수정 + 거래 전체필드 편집 추가)
M3 대시보드:▓▓▓▓▓▓▓  추이/예산/순자산 완료 (자산/부채 편집만 유예) + 순자산 findById 격리 누수 수정
M4 정리:    ▓▓▓▓▓▓▓  apiChain/JWT/REST/flutter_app 제거 완료 — 순수 SSR 단일화. 문서는 deployment.md/README 정리됨, account.md/CLAUDE.md 만 사용자 몫
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
- [x] `WebPingController`(PR1 검증 스텁) → M1 에서 `/web/home` 으로 교체 후 제거 완료
- 검증: ✅ owner1@example.com 로그인 → 레이아웃 렌더 + principal 이메일 표시, `/css/app.css` 200, 404/500 → error.html, 로그아웃 302 (별도 8081 인스턴스로 curl 검증)

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

- [x] **업로드 폼** (`GET /web/receipts/new`, `WebReceiptController`) — `<input type="file" accept="image/*" capture>` (모바일 브라우저 카메라). 서버 multipart 10MB. 클라 압축 생략. 홈에 "영수증 촬영" 진입 버튼
- [x] **분석 → 컨펌** (`POST /web/receipts`) — `ReceiptIngestionService.ingest` 재사용 → DRAFT 생성 → `receipts/confirm.html` 렌더. 검증 실패/`AnalysisException` 시 폼 재렌더(에러 표시)
- [x] 신뢰도 분기 UI — ≥0.8 success / 0.5~0.8 warning / <0.5 danger banner. <0.5 는 카테고리 변경 전 확정 버튼 비활성(최소 JS)
- [x] 컨펌 → **기존 `POST /web/transactions/{id}`(update, confirm=true) 재사용** (별도 엔드포인트 X) → DRAFT→CONFIRMED → 목록 리다이렉트
- [x] **🐛 Claude 호출 직렬화 버그 수정**: `ClaudeVisionClient` 의 image content 블록에 `"text":null` 이 섞여 나가 Anthropic 400("Extra inputs are not permitted"). content 블록 record 에 `@JsonInclude(NON_NULL)` 적용 + 직렬화 회귀 테스트. (실제 Claude 호출이 이번이 처음이라 그동안 미발견)
- 검증: ✅ 업로드 폼, 비이미지 거부(멀티파트+CSRF+검증+에러 재렌더), 컨펌 3개 신뢰도 분기. ✅ **실제 영수증 happy-path 수동 검증 완료** (업로드 → Claude 분석(신뢰도 75% → 확인 banner) → 컨펌). 인코딩 정상 (Claude raw JSON = DB = 표시 일치; 한글 OCR 오독은 모델 정확도 이슈, 코드 무관)
- [x] **전체 필드 편집** (실사용 요청) — 거래 수정 + 영수증 컨펌 화면에서 금액·일시·상점·결제수단·메모·카테고리 모두 편집 가능. `Transaction.edit()` 비즈니스 메서드 + `TransactionService.edit(EditRequest)` + 변경 이력 logUpdate. OCR 오독을 컨펌 화면에서 바로 수정 가능. 카테고리 해석도 `findById` → findAll+filter 로 격리 안전화 (create/update/edit 공통)

## P1 — M3. 대시보드

대체 대상: `trend_screen` / `budget_screen` / `networth_screen` / `networth_form_screen` / `networth_trend_screen`

- [x] **추이 차트** (`GET /web/trend`, `WebTrendController`) — `MonthlySummaryService.series` 최근 6개월 → Chart.js 라인(수입/지출/잉여). 데이터는 `th:inline="javascript"` 로 주입
- [x] **예산 설정** (`GET/POST /web/budget`, `WebBudgetController`) — 이번 달 지출 카테고리 진행률 bar + 초과 강조 + 카테고리별 예산 수정. 예산 수정은 `CategoryQueryService.updateBudget`(findAll+filter, 격리 안전)
- [x] **순자산** (`GET /web/networth`, `WebNetWorthController`) — 스냅샷(자산/부채 목록 + 합계 + 순자산) + 12개월 Chart.js 추이(자산/부채/순자산 3선) + 자산/부채 추가·삭제 + 월 선택
- [ ] **순자산 편집 유예** — 월별 스냅샷 모델상 삭제 후 재추가로 갈음. 필요 시 후속(인라인 편집 또는 편집 페이지)
- [x] **🔒 격리 누수 수정**: `NetWorthService.updateAsset/deleteAsset/updateLiability/deleteLiability` 가 `findById`(PK 직접 로드 → `@Filter` 미적용) 쓰던 것을 `findOne(Specification)` 로 교체 (M1 거래와 동일 패턴, 기존 REST 에도 있던 누수)
- [x] Chart.js CDN — 차트 페이지에서 페이지별 include (전역 X)
- 검증: ✅ 추이 labels/데이터 주입, 예산 설정·영속·진행률, 순자산 추가→합계 반영→삭제→0 복귀, 12개월 추이. 격리: owner2 가 owner1 자산 삭제 시 400 차단·자산 생존. 홈에 추이/예산/순자산 링크

## P2 — M4. 정리 (순수 SSR 단일화)

**모든 화면 마이그레이션 완료 후 진행. 순서 중요 — 화면 다 옮긴 뒤에.**

- [x] `SecurityConfig` 에서 `apiChain` 제거 → `webChain` 단일 체인 (CSRF 는 webChain 기본 활성 유지)
- [x] JWT 인프라 제거 — `JwtAuthenticationFilter` / `JwtTokenProvider` / `JwtProperties` / `jwtFilterRegistration`
- [x] REST 컨트롤러 + 전용 서비스 제거 — `/api/**` (`AuthController` / `AuthService` / `TransactionController` / `ReceiptController` / `SummaryController` / `NetWorthController` / `AssetController` / `LiabilityController` / `CategoryController`). 공유 서비스(`TransactionService` / `MonthlySummaryService` / `NetWorthService` / `ReceiptIngestionService`) + 공유 DTO 는 Web 컨트롤러가 재사용하므로 유지. 고아가 된 `TransactionService.update()` + `TransactionDtos.UpdateTransactionRequest` 함께 제거 (웹은 `edit()` 사용)
- [x] `AuthDtos` 제거. `CategoryController` 는 전용이라 통째 삭제 (`CategoryControllerTest` 동반 삭제 — 예산 수정 로직은 `CategoryQueryService.updateBudget` 에 존재). `MonthlySummaryDtos` / `NetWorthDtos` / `TransactionDtos` 는 공유라 유지
- [x] `GlobalExceptionHandler`(@RestControllerAdvice) **삭제** — REST 컨트롤러가 모두 사라져 더는 JSON 에러 advice 가 필요 없음. 웹 컨트롤러 예외는 이제 `error.html` 로 렌더 (status 500). 별도 4xx 매핑이 필요하면 후속
- [x] `account.jwt.*` 설정 (`application.yml`) + `application-secret.yml` / `.example` 의 jwt 블록 제거
- [x] **`flutter_app/` 디렉터리 삭제**
- [x] CI 에서 Flutter 잡 제거 (`.github/workflows/ci.yml`) — backend 잡 단독
- [x] Android signing 자산 제거 — `.gitignore` 의 `key.properties`/`*.jks`/`*.keystore` 패턴 + Flutter 블록 제거, `docs/deployment.md` §7 Flutter/서명 섹션 삭제
- [x] (Claude) `docs/deployment.md` (§0 JWT / §6 /api curl / §7 Flutter) + `README.md` (JWT/REST/Flutter 서술) SSR 기준 정리
- [ ] **(사용자) CLAUDE.md + `docs/account.md` 최신화** — Flutter 기준 서술 → SSR 기준으로. *사용자가 직접 진행.*

---

## 이어서 — SSR 이후 (기능 / 운영 후속)

> M0~M4(마이그레이션)는 완료. 2026-05-27 운영 배포(account.kyuhyeong.com, 호스트 8085) + CI/CD(GitHub Actions: ci.yml 빌드·테스트 / deploy.yml SSH 배포) 가동. 아래는 그 이후 백로그.

### 기능
- [ ] **관리자 페이지** (`/web/admin/**`, OWNER 전용) — 사용자 / 가구 / 비밀번호를 UI 로 관리
  - 동기: 현재 사용자·비번·테스트데이터 정리가 전부 raw SQL 이라 운영 시 위험·번거로움 ([`data-cleaning.md`](data-cleaning.md) 참조). 이를 UI 로 대체.
  - 범위(초안): 가구 멤버 목록·역할 표시 / 사용자 비밀번호 재설정 / (선택) 시드·테스트 데이터 정리 액션.
  - 권한: 세션 role=OWNER 만 접근 — `SecurityConfig.authorizeHttpRequests` 에 `/web/admin/**` → `hasRole("OWNER")` 추가.
  - ⚠ **MVP 범위 확장**: `docs/account.md` §11 의 "OWNER/MEMBER 권한 차등" · "회원가입/초대 UI" 는 v1.5+ 유예 항목 → 착수 전 scope 정합 확인.

### 운영 (1회성 / 상시)
- [ ] **운영 DB 데이터 클리닝** — dev 시드(테스트가구 + 약한 비번 계정) 제거. 절차: [`data-cleaning.md`](data-cleaning.md). ⚠ **아직 미적용.**
- [x] owner1 운영 비밀번호 변경 (dev1234! → 강한 값)
- [ ] member1(user 2) 비번 변경 또는 삭제 — `data-cleaning.md` §2
- [ ] **root 비밀번호 SSH 차단** — 키 로그인 확립됨 → `/etc/ssh/sshd_config` 에 `PasswordAuthentication no` (키 검증 후에만)
- [ ] (선택) GitHub `production` 환경에 Required reviewer 등록 → deploy 승인 게이트 활성화
- [ ] (선택) DBeaver 운영 DB 접근 — mariadb 를 `127.0.0.1:3316` 로 노출 (`docker-compose.prod.yml`, SSH 터널 전용)

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
- [x] `HouseholdIsolationIntegrationTest` 제거 (M4) — `/api`+JWT 경로 전용이라 그 경로와 함께 삭제. 세션 경로 격리 회귀 테스트는 미작성 (현재 수동 검증: owner1 → 22, owner2 → 5, 익명 → `/login`). 작성 시 Testcontainers Windows 비호환은 동일하므로 Linux CI 전제.

---

*이 파일은 마이그레이션 백로그다. 단계 완료 시 체크박스 + 진행 현황 막대를 갱신한다. 전체 완료 후 CLAUDE.md / `docs/account.md` 최신화 (사용자).*
