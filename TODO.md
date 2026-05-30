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

- 도메인 Entity 11개 + Multi-tenant 격리(`HouseholdContext` + Hibernate `@Filter`) + Flyway(V1~V6). *(마이그레이션 시작 시점엔 12개/V1~V3 — 이후 반복거래 V4, 구독 V5, dead 도메인 정리 V6.)*
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
- [x] **순자산 자산/부채 인라인 편집** (2026-05-28) — 행마다 이름·종류·잔액 인라인 폼으로 즉시 수정. `WebNetWorthController` 에 `POST /web/networth/assets/{id}` + `/liabilities/{id}` 추가 (이미 격리 안전한 `NetWorthService.updateAsset/updateLiability` + `UpdateRequest` 재사용, yearMonth=null 로 월 불변). 월 변경은 미지원 — 월 잘못 입력 시는 기존처럼 삭제 후 재추가
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
- [x] **CLAUDE.md + `docs/account.md` 최신화** (2026-05-28) — Flutter/JWT/REST 서술을 SSR/세션/Web 컨트롤러 기준으로 정리. 역사적 의사결정(구 §8 Week 1 / 구 §9 Week 2-6 로드맵)은 통째로 들어내고 (git log + TODO.md 가 이력 추적), 후속 절 번호 재정렬 (§10→§9, §11→§10, ...). §10 결정사항: #2(iOS)는 무효 표시, #6(FAB) 실제 구현 명시, #7(Multi-tenant) "JWT 클레임" → "세션 principal" 로 갱신. v1.1 백로그에서 완료된 항목(순자산 화면 M3, 예산 초과 경고 M1) 제거

---

## 이어서 — SSR 이후 (기능 / 운영 후속)

> M0~M4(마이그레이션)는 완료. 2026-05-27 운영 배포(account.kyuhyeong.com, 호스트 8085) + CI/CD(GitHub Actions: ci.yml 빌드·테스트 / deploy.yml SSH 배포) 가동. 아래는 그 이후 백로그.

### 기능
- [x] **관리자 페이지** (`/web/admin/**`, OWNER 전용) — 가구 멤버 목록·역할 + 비밀번호 재설정 (최소 범위)
  - 동기: 현재 사용자·비번·테스트데이터 정리가 전부 raw SQL 이라 운영 시 위험·번거로움 ([`data-cleaning.md`](data-cleaning.md) 참조). 이를 UI 로 대체.
  - 구현: `WebAdminController`(GET `/web/admin` 목록 / POST `/web/admin/users/{userId}/password` 재설정) + `AdminUserService` + `admin/users.html` + navbar OWNER 전용 "관리" 링크. `SecurityConfig` 에 `/web/admin/**` → `hasRole("OWNER")`.
  - **🔒 격리 가드**: `User`/`HouseholdMember` 는 `@Filter` 미적용(전역 식별 단위)이라, 비번 재설정 시 `findByHouseholdIdAndUserId` 멤버십 검증으로 타 가구 사용자 변경을 코드로 차단. `User.changePassword(hash)` 비즈니스 메서드 추가(@Setter 금지 준수). 단위 테스트 `AdminUserServiceTest`(멤버 매핑/정렬 · 인코딩 · 비멤버 거부) 통과.
  - scope 결정(2026-05-28): "(선택) 시드·테스트 데이터 정리 액션" 은 **제외** (운영 클리닝은 `data-cleaning.md` raw-SQL 유지). `docs/account.md` §8 v1.5 유예 항목과의 정합을 위해 OWNER 게이트는 관리자 섹션 한정의 최소 권한 차등으로만 도입.
  - ⏳ **수동 e2e 미검증**: bootRun + 브라우저 시나리오(owner1 → 관리 링크 노출/목록/비번 변경 후 재로그인, member1 → `/web/admin` 403)는 사용자 환경에서 1회 확인 필요.

### 폰 UX 개선 (모바일 반응형)
- [x] **A. 폼 입력 개선** (2026-05-28) — 금액 input `inputmode="decimal"`(거래/영수증), 잔액·예산 `inputmode="numeric"`(순자산/예산) → iOS/Android 적절한 숫자 키패드. `networth.html` 인라인 편집·추가 폼 4개를 `col-7/5 + col-9/3` 2행 레이아웃으로 변경(좁은 폰에서 잔액 가로 잘림 회피). 거래 목록 페이지네이션·필터 적용 버튼 `btn-sm` 제거(터치 타겟 ↑). 보조 액션(`홈으로`/`취소`/`+ 입력`)은 시각적 위계 유지 위해 그대로
- [x] **B. 영수증 촬영 FAB** (2026-05-28) — `docs/account.md` §10 #6 미구현분. `fragments/layout.html` 에 fixed 우하단 56×56 원형 anchor → `/web/receipts/new`. 인라인 SVG(Bootstrap Icons camera path) 아이콘, `aria-label` 부여. `#httpServletRequest.requestURI` 로 `/web/receipts` 경로에서는 자기 자신 링크라 숨김. `app.css` 에 `.fab-camera` + `body` 패딩 `safe-area + 88px` 로 확장(긴 페이지 최하단 FAB 가림 방지). **스코프 확장**: §10 문구는 "홈 + 카메라 FAB"지만 layout fragment 한 곳에서 처리해 모든 chromed 페이지(거래/예산/순자산/추이/관리)에 노출 — Material FAB 패턴 본의(persistent quick action) 정합 + 진입 1탭 단축이 화면 가로질러 발휘
- [x] **C. navbar 햄버거 메뉴** (2026-05-28) — `fragments/navbar.html` 을 `navbar-expand-md` + `navbar-toggler` + `collapse navbar-collapse` 구조로 재작성. 768px 미만(폰/landscape 폰)에서 햄버거 토글, 이상에서 풀-가로. 메뉴 = 거래 · 추이 · 예산 · 순자산 · 관리(OWNER) · email · 로그아웃. `home.html` 푸터의 추이/예산/순자산 small 링크 제거(navbar로 통합). 핵심 액션 3버튼(영수증 촬영/직접 입력/거래 목록)은 home 에 그대로 유지. JS 의존 추가 X (Bootstrap bundle 기존 포함)
- [x] **D. navbar active 페이지 표시** (2026-05-28) — `fragments/navbar.html` 의 5개 nav-link (거래/추이/예산/순자산/관리) 에 `#strings.startsWith(#httpServletRequest.requestURI, '/web/{section}')` 기반 `active` 클래스 + `aria-current="page"` 조건부 부여. `th:with="uri=..."` 로 nav 전체에 1회 평가. `/web/transactions/123` 같은 하위 경로에서도 "거래" 강조. 브랜드는 active 처리 안 함 (홈 진입점은 항상 브랜드)
- [x] **E. 거래 목록 필터 collapsible** (2026-05-28) — `transactions/list.html` 의 필터 폼을 Bootstrap `collapse` 로 감쌈. 필터 미설정 시 접힘, 설정되어 있으면 펼침(서버 측 `th:with="hasFilter=..."` 판단 → `show` 클래스 조건부 부여). 헤더 우측에 "필터" 토글 버튼 (필터 설정 시 `●` 점 indicator + `aria-expanded` 동기화). 컨트롤러 변경 0, JS 의존 추가 X (Bootstrap bundle 기존 포함)
- [x] **F. flash 메시지 일관화** (2026-05-28) — `fragments/layout.html` `<main>` 최상단에 공통 `message`/`error` alert 영역 1곳 추가. 거래 create/update, 예산 update, 순자산 자산·부채 6 CRUD 엔드포인트 모두 `RedirectAttributes.addFlashAttribute("message", ...)` 부여. 거래 update 는 `confirm=true` 시 "확정" vs 일반 "수정" 분기. `admin/users.html` 의 기존 flash divs 는 중복 노출 회피로 제거 (layout 으로 이전). 에러 케이스(검증/예외)는 본 PR 범위 외 — 기존 폼 재렌더/error.html 흐름 유지
- [x] **G. 인라인 폼 저장 후 스크롤 위치 보존** (2026-05-28) — URL fragment 방식. `networth.html` 의 자산/부채 `<li>` 에 `id="asset-{id}"`/`id="liability-{id}"`, `budget.html` 의 카테고리 row `<div>` 에 `id="category-{id}"` 부여. 컨트롤러는 update redirect URL 에 `#asset-N`/`#liability-N`/`#category-N` 부착 → 브라우저 native scroll-to-anchor 로 편집한 행 위치로 복귀. `app.css` 에 attribute selector `[id^="asset-"], [id^="liability-"], [id^="category-"] { scroll-margin-top: 88px }` 추가 — 상단 flash alert 가 row 를 가리지 않도록 여백 확보. 대상: update 만 (create/delete 는 row 가 없거나 모호해 제외). 0 JS
- [x] **H. Dark mode** (2026-05-28) — Bootstrap 5.3.3 의 native `data-bs-theme` 활용. `fragments/head.html` 의 stylesheet 앞에 inline script (`localStorage('account-theme')` 읽고 `<html>` attribute 설정 → FOUC 0). `fragments/layout.html`·`login.html`·`error.html` 의 `<html>` 에 `data-bs-theme="light"` 기본값. `app.css` 에 차트 6색 CSS variables (`:root` 라이트 + `[data-bs-theme="dark"]` override) + theme 아이콘 가시성 규칙. `trend.html`/`networth.html` 차트가 `getComputedStyle().getPropertyValue('--account-chart-*')` 로 색 읽음. `navbar.html` 우측에 🌙/☀️ 토글 버튼, `scripts.html` 에 클릭 핸들러(13줄, defensive null-check). 컨트롤러 변경 0, 새 의존성 0. 라이트 회귀 0 (기존 hex 그대로 보존)
- [x] **🐛 Fix: `#httpServletRequest` SpEL EL1007E** (2026-05-28) — PR B(FAB 숨김) / PR D(navbar active) 에서 `${#httpServletRequest.requestURI}` 사용했으나 Thymeleaf 3.1 (Spring Boot 3.3 번들) 부터 servlet implicit object 직접 접근 불가 (`EL1007E: Property 'requestURI' cannot be found on null`). 누적된 PR들이 실제 bootRun 시점에 일괄 발견됨. 해결: `ViewContextAdvice`(@ControllerAdvice) 신설 → `@ModelAttribute("currentUri")` 가 `request.getRequestURI()` 를 모든 SSR 응답 모델에 자동 주입. `navbar.html` 의 `th:with="uri=${#httpServletRequest.requestURI}"` 와 `layout.html` 의 FAB 숨김 조건 둘 다 `${currentUri}` 로 교체. 다크모드(PR H) 와 직접 무관

### 가계부 기능 보강 (post-UX)
- [x] **거래 삭제** (2026-05-28) — 거래 수정 화면 하단 "거래 삭제" 폼 (`POST /web/transactions/{id}/delete`, native `confirm()` 가드). `TransactionService.softDelete(id, actorUserId)` 신설 — `findOne(Specification)` 격리 가드 + `Transaction.softDelete(actor)` 비즈니스 메서드(기존) + `TransactionHistoryService.logDelete`(신규, `ChangeType.DELETE` + beforeJson). flash "거래가 삭제되었습니다." 후 목록으로. `TransactionServiceTest`(신규, Mockito) 3건: happy path / 미존재·타가구 거부 / 이미 삭제 거부
- [x] **카테고리 관리 UI** (2026-05-28) — `/web/categories` 전용 페이지. CRUD: 이름·타입(INCOME/FIXED/VARIABLE/INVEST)·월예산·정렬순서. `Category.edit(name,type,budget,sortOrder)` 비즈니스 메서드 + `CategoryQueryService` 에 create/edit/delete 추가 (findAll+filter 격리 가드, 기존 updateBudget 패턴 일관). 🛡 **삭제 안전 가드**: `TransactionRepository.countByCategoryId` 로 사용 카운트 사전 체크 → ≥1건이면 `IllegalStateException` + flash error ("거래 N건에서 사용 중"). DB FK 가 RESTRICT 라 어차피 막히는 걸 사용자 친절 메시지로 노출. `WebCategoryController` 신규. budget 페이지에 "카테고리 관리" 진입 링크. **§10 v1.5 항목 일부 당겨오기 결정**: 가구 초대/OWNER 차등은 그대로 유예. `CategoryQueryServiceTest`(신규, Mockito) 6건: create 빌드+save / null budget 기본 0 / edit 적용 / edit 미존재 거부 / delete 미사용 시 OK / delete 사용 중 거부
- [ ] **거래 검색 (상점명·메모)** — `transactions/list` 필터에 keyword 필드 추가. `Specification` 에 LIKE %keyword% (merchant OR memo)
- [x] **기간/연 결산 화면** (2026-05-30) — `/web/report` (`WebReportController`). 임의 from~to + 프리셋(이번 달/지난 달/올해/작년), 기본 올해 전체. `MonthlySummaryService` 의 월 집계를 private `aggregate(from,to)` 로 추출 → 신규 `getRange(from,to,label)`(양끝 inclusive→내부 반-개구간) + `PeriodSummaryResponse` DTO. 기존 `get(YearMonth)` 호출부(home/budget/trend) 무변경. `report/report.html` + navbar "결산".
- [x] **거래 CSV 내보내기** (2026-05-30) — `GET /web/transactions/export`, 목록과 동일 필터(`listForExport` = `buildSpec` 재사용, 페이지네이션 없이 전체). **UTF-8 BOM 바이트 직접 부착**(엑셀 한글), RFC4180 escape, 외부 라이브러리 0. 컬럼: 거래일시/카테고리/분류/금액/상점/결제수단/메모/상태. 목록 화면에 "CSV" 링크(현재 `${filterQuery}` 전달).
- [x] **dead 도메인 정리** (2026-05-30) — `MonthlySummary`·`WeddingItem` 엔티티+repository 제거(호출 0건) + **V6 `DROP TABLE monthly_summaries, wedding_items`**(들어오는 FK 없음). 집계는 on-the-fly 라 사전계산 테이블 불필요, 결혼 화면은 v1.1 유예. `ddl-auto: validate` 정합 위해 엔티티 삭제+DROP 동시. docs ER/§8 갱신.
- [x] **반복 거래** (2026-05-28) — 월 단위(day_of_month 1~31, 짧은 달은 말일로 클램프) 자동 적재. Flyway V4 `recurring_transactions` 신규 + `RecurringTransaction` 엔티티 + repository. `RecurringTransactionService`(account-api/recurring) 가 CRUD + fire 로직. `RecurringTransactionScheduler` 가 매일 KST 05:00 (`@Scheduled cron`) 에 `runDueAcrossHouseholds` 호출 — 가구 단위 트랜잭션 격리로 한 가구 실패가 다른 가구를 막지 않음. `WebRecurringController` 가 `/web/recurring` CRUD + "지금 실행" 버튼 (수동 trigger, 동일 멱등 로직). **멱등**: `last_run_year_month` 컬럼으로 같은 달 재실행 방지. **생성 의외성 회피**: 생성 시점 `today.day >= 발화일` 이면 `lastRunYearMonth=현재월` 로 초기화 → 다음 달부터 발화. **카테고리 삭제 가드 보강**: `CategoryQueryService.delete` 가 `transactionRepository.countByCategoryId` + `recurringRepository.countByCategoryId` 둘 다 체크 + 친절 메시지에 어디서 막혔는지 명시. `RecurringTransactionServiceTest`(Mockito) 8건: due 발화 / 멱등 skip / 발화일 전 skip / 31일 말일 클램프 / lastRun 초기화 분기 2건 / 검증 가드 2건. `account-batch` 모듈 활성화는 미룸 (잡 1개라 surgical)

### 구독 (플랜 티어)
- [x] **구독 Phase 1 — 도메인 게이팅** (2026-05-30) — 가구 단위 티어(**FREE/FAMILY/PRO** — V5 에서 PERSONAL→FREE 리네임, 영문 라벨) + 영수증 AI 월 한도 게이팅. **새 엔티티/테이블 0** — 기존 휴면 필드 `Household.planType` 재사용. `PlanType` enum 에 `monthlyReceiptQuota()`(10/100/무제한)+`displayName()`. V5 `UPDATE households plan_type 'PERSONAL'→'FREE'` + 컬럼 기본값 변경 (V1/V2 immutable 이라 신규 마이그레이션으로 처리). `Household.changePlan()` 비즈니스 메서드. `ReceiptRepository.countByCreatedAtGreaterThanEqual`(이번 달 KST 사용량). `ReceiptIngestionService.ingest` 맨 앞 fail-fast 게이트(`ReceiptQuotaExceededException`, 디스크·Claude 호출 전 → 한도 초과 비용 0). `WebReceiptController` 친절 처리 + OWNER 업그레이드 링크. `/web/plan` (OWNER 전용, `WebPlanController`+`PlanService`+`plan/plan.html`): 현재 티어/사용량/변경. `SecurityConfig` `/web/plan/**`→`hasRole("OWNER")`, navbar "구독". `PlanTypeTest`(순수 단위) 4건. **IAP→웹 PG 정정**(SSR 단일화로 App Store/Play 비대상). 게이트 enforcement 통합테스트는 Testcontainers Windows 비호환 → Linux CI 전제로 미작성, 수동 검증.
- [ ] **구독 Phase 2 — 실결제 (유예)** — Toss/PortOne/Stripe 정기결제: 빌링키 등록, 웹훅(갱신/실패), 결제 이력, 별도 `Subscription` 엔티티(기간/상태). 외부 PG 계정·시크릿·HTTPS 웹훅 필요.

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
