# Account-App TODO

작업 백로그 — 우선순위 + 현재 페이즈 기반.

- **P0**: 현재 페이즈(Week 1) 내 필수 — 지금 진행
- **P1**: 다음 페이즈(Week 2~6) — MVP 완성까지
- **P2**: MVP 이후 (v1.1 / v1.5 / v2) — 우선순위 낮음
- 상세 컨텍스트는 [`docs/account.md`](docs/account.md) 해당 절 참조. 본 파일은 "체크리스트 + 우선순위"만 유지.

---

## 진행 현황 (요약)

```
Week 1: ▓▓▓▓▓▓░  Task 6 코드 통합 완료 / 통합 테스트·Acceptance 보류
Week 2-3: ▓▓▓▓▓▓░ Flutter 로그인 + 거래 목록/입력 + 백엔드 API 완료
Week 4:   ▓▓▓▓▓▓░ 카메라 촬영 + 1280px 압축 + 업로드 + 신뢰도 분기 컨펌 + PATCH 완료
Week 5:   ▓▓▓▓▓▓░ 학습 UPSERT + 월별 집계 + 홈 카드 + 배치 잡 + 시계열 + 추이 차트 완료
Week 6:   ▓▓▓▓▓░░ Dockerfile + 운영 compose + nginx 예시 + CI + Android signing 완료 / CD + LE 발급 대기
v1.1+:     대기
```

---

## P0 — Week 1 (현재 페이즈)

목표: 멀티 모듈 + DB + 인증 + 가구 격리 검증까지 완료. **격리 검증 통합 테스트가 본 페이즈의 가장 중요한 산출물**.

### Task 1. Gradle 멀티 모듈 루트 셋업 ✅
- [x] `settings.gradle.kts` (4개 모듈 include)
- [x] 루트 `build.gradle.kts` (Java 21 toolchain, Spring Boot BOM)
- [x] 각 모듈 `build.gradle.kts` + 의존성 그래프 (api→core, batch→core, ai 독립)
- [x] Gradle Wrapper
- [x] `AccountApiApplication` 기동 확인 (DB 미연결 상태)

### Task 2. MariaDB Docker + Flyway 스키마 ✅
- [x] 루트 `docker-compose.yml` (MariaDB 11.4, 호스트 포트 **3305**, volume `./data/mariadb`) — 호스트 mysqld 가 3306 점유 중이라 변경
- [x] `account-core/src/main/resources/db/migration/V1__init_schema.sql` — 12 도메인 테이블 + 인덱스 + FK(`ON DELETE RESTRICT`)
- [x] `V2__seed_dev.sql` — 가구 2(`우리집`/`테스트가구`), 사용자 4, 카테고리 22+5 (격리 검증용 차이)
- [x] `account-api/application.yml` — DataSource + JPA + Flyway 활성화, autoconfigure.exclude 블록 제거
- [x] Acceptance: `./gradlew :account-api:bootRun` 시 V1/V2 자동 적용 → 13개 테이블(12+flyway_schema_history), 가구 2, 카테고리 22/5, 사용자 4 확인
- [x] 알려진 변형: `monthly_summaries.year_month` 는 MariaDB 예약어라 백틱 인용. Task 3 entity 에서 `@Column(name = "`year_month`")` 사용 필요
- 커밋: `feat(core): add flyway migrations with seed data for two households`

### Task 3. JPA Entity + Repository ✅
- [x] Entity 12개 (`User`/`Household`/`HouseholdMember`/`Category`/`Transaction`/`TransactionHistory`/`Receipt`/`MerchantHistory`/`MonthlySummary`/`Asset`/`Liability`/`WeddingItem`)
- [x] enum 5개 (`CategoryType`/`TransactionStatus`/`HouseholdRole`/`PlanType`/`ChangeType`)
- [x] Lombok 패턴 (`@Getter` + `@NoArgsConstructor(access=PROTECTED)` + `@AllArgsConstructor(access=PRIVATE)` + `@Builder`, Setter 0건)
- [x] Repository 인터페이스 12개 (`JpaRepository<E, Long>` 기본형, 쿼리 메서드는 필요 시점에 추가)
- [x] `JpaConfig` (account-core 내 `@Configuration`) — `@EntityScan` + `@EnableJpaRepositories` 캡슐화. account-api 는 component-scan 만으로 자동 등록
- [x] `application.yml` `ddl-auto: none → validate` 격상
- [x] Acceptance: `./gradlew :account-api:bootRun` 시 Hibernate validate 통과 + `Started AccountApiApplication`
- [x] `@Filter` 는 본 단계 미적용 (Task 4)
- 알려진 변형: `MonthlySummary.yearMonth` 는 `columnDefinition = "CHAR(7)"` 명시 (SQL 의 CHAR(7) 과 일치)
- 커밋: `feat(core): add JPA entities and repositories for all domain tables`

### Task 4. HouseholdContext + Hibernate Filter ✅
- [x] `HouseholdContext` (ThreadLocal<Long>) in `account-core/tenant/`
- [x] `package-info.java` 에 `@FilterDef`; 9 개 격리 엔티티에 `@Filter("householdFilter", ...)` (User/Household/HouseholdMember 제외)
- [x] `HouseholdFilterAspect` — `@Around` on `@Transactional`, **fail-safe default** (ctx 미설정 시 `-1` sentinel → 0 rows). `TransactionConfig` 에서 `@EnableTransactionManagement(order=100)` 명시해 aspect 가 tx 내부에서 실행되도록 순서 제어
- [x] `HouseholdContextFilter` (Servlet `OncePerRequestFilter`) — `X-Household-Id` 헤더 → ThreadLocal set, finally clear. JWT 도입(Task 5) 시 헤더 부분만 교체
- [x] `CategoryController` 스텁 + `SecurityConfig` (현 단계 permitAll, Task 5 에서 JWT 인증으로 교체)
- [x] account-api 에 `starter-data-jpa` 명시 의존 (jakarta.persistence / @Transactional 컴파일 노출용)
- [x] **수동 격리 검증 통과**: `X-Household-Id: 1` → 22개, `:2` → 5개, 헤더 없음 → 0개. ID 교집합 0 (H1: [1..22], H2: [23..27])
- [ ] **알려진 이슈**: `HouseholdIsolationIntegrationTest` 는 `@Disabled` — Docker Desktop on Windows 의 CLI 프록시 가로채기로 Testcontainers 가 docker-java 통신 실패. Linux CI 또는 Docker Desktop TCP 노출 활성화 시 재활성화
- 커밋: `feat(core): add multi-tenant isolation via HouseholdContext + Hibernate filter`

### Task 5. JWT 인증 셋업 ✅
- [x] `JwtTokenProvider` — access(15분) + refresh(30일), 클레임 sub/household_id/role, HS256
- [x] `JwtProperties` — `account.jwt.{secret,access-ttl,refresh-ttl}` 바인딩. secret 은 base64, @PostConstruct 에서 32바이트 미만/blank/invalid base64 모두 명확히 실패
- [x] `JwtAuthenticationFilter` (OncePerRequestFilter) — Bearer 헤더 → 검증 → SecurityContext + HouseholdContext set, finally clear (두 ThreadLocal 모두)
- [x] `SecurityConfig` — STATELESS, /api/auth/login + /refresh permitAll, 나머지 인증 필수, 401 EntryPoint
- [x] `AuthController` + `AuthService` + `AuthDtos` records — login / refresh / me
- [x] Task 4 의 `HouseholdContextFilter` (X-Household-Id) **제거** — JWT 클레임 단일 진입점
- [x] V3 마이그레이션: 4 시드 사용자에 BCrypt(`dev1234!`) 적용 (`BcryptHashToolTest` 재현 가능)
- [x] `application-secret.yml.example` jwt 블록 추가, `application.yml` 기본값은 빈 secret (명확한 실패)
- [x] `bootRun.workingDir = rootProject.projectDir` — 모듈 dir 가 아닌 루트에서 실행해야 `optional:file:./application-secret.yml` 이 로드됨 (이전엔 placeholder default 가 non-blank 라 조용히 가려져 있던 잠재 버그)
- [x] 격리 통합 테스트 JWT 기반으로 갱신 (여전히 `@Disabled` — TC 이슈)
- [x] **수동 Acceptance 통과**: owner1 토큰 → 22, owner2 → 5, 익명 → 401, 잘못된 비밀번호 → 401
- 커밋: `feat(api): add JWT authentication with household_id claim`

### Task 6. account-ai 모듈 멀티 모듈 통합
- [x] `JpaMerchantHistoryProvider implements MerchantHistoryProvider` — **위치 변경**: `account-api/ai/` 에 배치. account-core/build.gradle.kts 의 "다른 어떤 account-* 모듈에도 의존하지 않음" 정책이 강해서 core 가 ai 의 인터페이스를 implements 하려면 의존 역전이 필요. account-api 가 양쪽 모듈에 합법 의존하는 합성 모듈이라 어댑터 적격
- [x] `account-ai`의 `ReceiptController` → `account-api/receipt/`로 이전 (account-ai 는 이제 library jar)
- [x] `X-Household-Id` 헤더 처리 제거 — 컨트롤러는 `Authentication` 에서 user_id 추출, 가구 ID 는 `HouseholdContext` (JWT 필터 set) 에서 자동 조회
- [x] `account-api/build.gradle.kts`에 `implementation(project(":account-ai"))` (Task 1 셋업 시점에 이미 추가됨, no-op 확인)
- [x] 영수증 업로드 → Receipt + DRAFT Transaction 자동 생성 (`ReceiptIngestionService`, @Transactional)
- [x] 이미지 저장: `ReceiptStorage` — `{account.receipts.storage-root}/{hid}/{yyyy}/{mm}/{uuid}.{ext}`. 개발 기본값 `./data/receipts` (gitignored), 운영 `ACCOUNT_RECEIPTS_STORAGE_ROOT=/mnt/data/receipts` 로 오버라이드
- [x] 카테고리 매칭 — 정확 일치 → "기타 변동" → 첫 VARIABLE → 첫 카테고리 fallback (DRAFT 거래 절대 실패 안 함, 사용자 확정 시 수정)
- [x] `MerchantHistoryRepository.findAllByOrderByCountDescLastUsedAtDesc(Pageable)` + `findByMerchantName(String)` 추가 (가구 격리는 Hibernate `@Filter` 자동 적용)
- [x] `StubMerchantHistoryProvider` 완전 삭제
- [x] `./gradlew build -x test` 통과 + 기존 `ReceiptAnalysisServiceTest` 6건 통과
- [ ] **보류**: 영수증 인제스천 통합 테스트 — `HouseholdIsolationIntegrationTest` 와 동일한 Docker Desktop / Testcontainers 비호환으로 보류. Linux CI 또는 TCP 노출 시점에 작성
- [ ] **보류**: Acceptance `curl -X POST /api/receipts -F "image=@..."` — 실제 영수증 이미지 + Claude API 키 있는 환경에서 사용자가 수동 검증
- 커밋: `feat(api): integrate account-ai with multi-module structure`

### Week 1 완료 기준
- [ ] `./gradlew build` 성공 (전 모듈)
- [ ] `docker-compose up -d` → MariaDB 기동 + Flyway 자동 적용
- [ ] `./gradlew :account-api:bootRun` 기동 + `/api/auth/login` 호출 가능
- [ ] **격리 검증 통합 테스트 통과** (가장 중요)
- [ ] `curl` 영수증 업로드 → Claude 분석 → DRAFT 거래 → 본인 가구로만 조회
- [ ] 시크릿 0건 커밋 (§10.2 grep 검증)

---

## P1 — Week 2~6 (MVP 완성까지)

### Week 2~3. Flutter 셋업 + 거래 입력
- [x] `flutter_app` 모듈 추가 (`flutter create flutter_app --platforms=android --org=com.kyuhyeong`)
- [x] 의존성: flutter_riverpod 3.3.1, go_router 17.2.3, dio 5.9.2, flutter_secure_storage 10.2.0, intl 0.20.2, reactive_forms 18.2.2, flutter_localizations
- [x] 로그인 화면 + JWT 자동 갱신 — AuthInterceptor 가 401 시 `/api/auth/refresh` 호출 후 원 요청 1회 retry, 실패 시 토큰 폐기. flutter_secure_storage 로 access/refresh 영속.
- [x] go_router redirect — AuthState 변화 시 `_RouterNotifier` 가 router refresh, unauth↔auth 자동 분기
- [x] Android: INTERNET 권한(main) + cleartext(debug only, 10.0.2.2 개발용)
- [x] **백엔드 추가**: `GET /api/transactions` (필터=from/to/categoryId/type/status + 페이징, occurred_at DESC), `POST /api/transactions` (수동 입력, CONFIRMED 상태). `JpaSpecificationExecutor` + soft-delete 자동 제외. `GlobalExceptionHandler` 추가 (422 → 필드별 에러).
- [x] 거래 목록 화면 — 날짜별 그룹핑, intl 통화/날짜 포맷, 무한 스크롤 (loadMore), pull-to-refresh, DRAFT 뱃지.
- [x] 수동 거래 입력 폼 (`reactive_forms`) — amount/category/occurredAt/merchant/paymentMethod/memo, DatePicker+TimePicker 통합, 카테고리 dropdown, 서버 fieldErrors 표시.
- [x] flutter_localizations + intl ko_KR locale 데이터 초기화 (DatePicker / DateFormat 한국어).
- [x] 검증: `./gradlew build`, `flutter analyze` 0 issues, `flutter test` 1/1, `flutter build apk --debug` OK.

### Week 4. 카메라 + 영수증 촬영
- [x] **백엔드**: `PATCH /api/transactions/{id}` (categoryId/status partial update, DRAFT→CONFIRMED 일방향). `transaction_history` 적재 (CREATE on insert + UPDATE on patch). `ReceiptIngestionService` 도 CREATE 이력 적재.
- [x] `image_picker` 통합 (CAMERA 권한 main 매니페스트)
- [x] 클라이언트 측 1280px 압축 (`image` 라이브러리, JPEG 80%, `compute()` 로 isolate off-main-thread)
- [x] 업로드 → 분석 결과 → 컨펌 화면 흐름 (`/receipts/new` → `/receipts/confirm`)
- [x] 신뢰도 분기 UI
  - `confidence ≥ 0.8`: 자동 확정 권장 banner + "이대로 확정" 버튼
  - `0.5~0.8`: 카테고리 확인 요청 banner
  - `< 0.5`: 수동 변경 강제 (변경 없으면 확정 버튼 비활성)
- [x] HomeScreen 에 large FAB 카메라 버튼 추가
- [x] 컨펌 후 거래 목록 invalidate + `/transactions` 로 이동
- [x] 검증: `./gradlew test` + `flutter analyze` 0 issues + `flutter test` 1/1 + `flutter build apk --debug` OK

### Week 5. 학습 + 대시보드
- [x] **`merchant_history` 학습 피드백 루프** — `MerchantHistoryService.upsert(merchant, category)` 가 가맹점명으로 lookup → exists 면 `touchUsage`, 없으면 insert. 학습 시점: 수동 입력 / PATCH 컨펌(DRAFT→CONFIRMED) / 카테고리 변경. DRAFT (영수증 자동 생성) 직후는 학습 X.
- [x] **홈 화면 이번 달 카드** — `ThisMonthCard` 위젯 (수입/지출/잉여금 3행 + 투자 별도 표시). `currentMonthSummaryProvider` autoDispose 로 화면 진입마다 fresh fetch. 잉여금 음수면 빨강.
- [x] 카메라 FAB (Week 4 에서 large FAB 으로 이미 완료)
- [x] **월별 집계 API** — `GET /api/summary/monthly?yearMonth=2026-05`. `MonthlySummaryService` 가 Specification 으로 한 달치 거래만 fetch 후 메모리 집계. soft-delete 제외. 거래 0 인 카테고리도 응답에 포함 (예산 진행률 UI 대비).
- [x] **`MonthlySummary` 사전 계산 배치 잡 (`account-batch`)** — `MonthlySummaryJob` (`@Scheduled` cron 매월 1일 03:00 KST, Asia/Seoul) → `MonthlyAggregationService` 가 가구별 트랜잭션으로 직전 월 집계 후 `monthly_summaries` UPSERT. account-api 가 batch 모듈 의존해서 단일 프로세스로 같이 기동 (`@EnableScheduling`).
- [x] **시계열 API + 추이 차트 (`fl_chart`)** — `GET /api/summary/monthly/series?from=YYYY-MM&to=YYYY-MM` (최대 24개월). Flutter `TrendScreen` 에서 최근 6개월 라인 차트 (수입/지출/잉여 3선, y축 만원 단위). 홈 카드 우상단 "추이" 버튼에서 진입.
- [ ] 앱 아이콘 Quick Action — Week 6 배포 시점에 같이

### Week 6. 배포
- [x] **`account.kyuhyeong.com` nginx server block 예시** — `infra/nginx/account.kyuhyeong.com.conf.example` (HTTP→HTTPS 리다이렉트, Let's Encrypt cert 경로, 보안 헤더, client_max_body_size 12m, Claude 분석용 90s timeout)
- [x] **Docker Compose 운영 stack** — `docker-compose.prod.yml` (mariadb 호스트 미노출 + account-api 127.0.0.1:8080만 노출 + 볼륨 `/var/lib/account-app/{mariadb,receipts}` + always restart). `account-api/Dockerfile` multi-stage (jdk-alpine build → jre-alpine runtime, tini PID 1).
- [x] **GitHub Actions CI** — `.github/workflows/ci.yml` (backend gradle build/test + Flutter analyze/test/build apk, 병렬 잡, concurrency cancel 정책)
- [x] **`.env.prod.example` + 배포 가이드** — `docs/deployment.md` (DNS / Claude 키 / JWT secret 생성 → VPS 준비 → compose 빌드/기동 → nginx + LE 발급 → curl 검증 → Android release APK 빌드)
- [ ] **Let's Encrypt 발급** — VPS 사용자 수동 단계 (`certbot --nginx -d account.kyuhyeong.com`)
- [ ] **CD 자동화** — git push to main → GitHub Actions → SSH 로 VPS pull/rebuild/restart. 별도 PR (GitHub Secrets 등록 필요)
- [x] **Android release signing** — `key.properties` (gitignored) 패턴 + debug fallback. `key.properties.example` 템플릿 + `docs/deployment.md` §7.1~7.3 (keystore 생성 + 백업 정책 포함). 키스토어 자체 생성/Internal Track 업로드는 사용자 수동.
- [ ] ~~TestFlight 빌드 + 부부 단말 설치~~ — `docs/account.md` §11 #2 변경에 따라 유예 (Android-first MVP)
- [ ] Android APK Internal Track 배포 — Android release signing PR 후

---

## P2 — v1.1 (MVP 후 점진)
- [ ] 순자산 화면 (자산/부채 + 월별 추이)
- [ ] 결혼 일시 지출 화면 (예산 vs 실제, 부모 지원 분리)
- [ ] FCM 푸시 (silent push 동기화 + 알림) — Firebase Admin SDK
- [ ] 예산 초과 경고
- [ ] 영수증 단계적 압축/삭제 배치 잡 (1년: 800px/60%, 5년: 삭제)
- [ ] 백업 자동화 (MariaDB 일일 덤프, Cloudflare R2 주 1회)

## P2 — v1.5 (가구 확장 시)
- [ ] 가구 초대 플로우 (이메일)
- [ ] OWNER/MEMBER 역할 차등 (예산 수정은 OWNER만)
- [ ] 회원가입 화면
- [ ] 가구별 카테고리 커스터마이징 UI
- [ ] `POST /api/auth/switch-household` (다중 가구 소속)
- [ ] 탈퇴/데이터 삭제 자동 배치

## P2 — v2 (장기 / 사업화 검토)
- [ ] 카드 PDF 명세서 일괄 분류
- [ ] 음성 입력 거래
- [ ] 자산관리 엑셀 통합 (투자 모듈)
- [ ] 연말정산 시뮬레이터
- [ ] 구독 티어 (PERSONAL / FAMILY / PRO) — In-App Purchase
- [ ] 개인정보처리방침 본격 정비
- [ ] Sonnet 4.5 → Haiku 4.5 A/B 정확도 비교 후 다운그레이드 판단

---

## 상시 / 가로지르는 항목

### 보안 / 시크릿 (모든 커밋 전)
- [ ] `git diff --cached | grep -iE "sk-ant|password.*=.*[a-zA-Z0-9]{8}|secret.*=.*[a-zA-Z0-9]{16}"` 결과 비어있는지 확인
- [ ] `application-secret.yml` 미커밋 확인
- [ ] `local.properties` 미커밋 확인 (Android SDK 경로 노출 방지)

### 테스트 정책 (§10.4)
- [ ] DB 통합 테스트는 Testcontainers (H2 금지)
- [ ] 격리 검증 테스트는 모든 도메인 Repository에 대해 작성
- [ ] AssertJ 사용
- [ ] 각 테스트 자체 시드, 다른 테스트의 부산물 의존 금지

### 외부 의존 / 사용자 행동 필요 항목
- [x] Anthropic Console에서 Claude API 키 발급 + 월 한도 $10 설정 → `application-secret.yml` 로컬 주입 완료
- [ ] ~~Apple Developer 가입~~ — **유예** (docs §11 #2 변경). Mac + 아내 iPhone 테스트 필요 시점에 재신청
- [ ] VPS에 `account.kyuhyeong.com` DNS A 레코드 추가 (Week 6) (사용자)
- [ ] GitHub Repo Settings → Secrets에 CI/CD용 시크릿 등록 (Week 6) (사용자)

---

*이 파일은 작업 백로그다. 작업이 완료되면 체크박스를 갱신하고, 페이즈가 넘어가면 진행 현황(맨 위 막대 그래프)도 같이 업데이트한다.*
