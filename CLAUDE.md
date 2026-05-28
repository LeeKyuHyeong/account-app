# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

LLM 코딩에서 흔히 발생하는 실수를 줄이기 위한 행동 가이드라인.
Andrej Karpathy의 LLM 코딩 함정 관찰을 기반으로 함.

**Tradeoff:** 이 가이드라인은 **속도보다 신중함**에 무게를 둔다.
사소한 작업(오타 수정, 명백한 한 줄 변경)에는 판단껏 유연하게 적용할 것.

> **단일 진실 원천**: 설계/페이즈는 [`docs/account.md`](docs/account.md), 작업 백로그/우선순위는 [`TODO.md`](TODO.md). 본 파일은 LLM 행동 가이드 + 빌드/아키텍처 요약이며, 두 문서를 대체하지 않는다.

---

## 1. Think Before Coding (코딩 전에 먼저 생각)

**가정하지 마라. 혼란을 숨기지 마라. Tradeoff를 드러내라.**

구현에 들어가기 전에:

- **가정을 명시적으로 진술**한다. 불확실하면 추측하지 말고 질문한다.
- **여러 해석이 가능하면 모두 제시**한다. 조용히 하나만 골라서 진행하지 않는다.
- **더 단순한 접근이 존재하면 말한다.** 필요할 때는 반박(push back)한다.
- **불명확하면 멈춘다.** 무엇이 헷갈리는지 명시하고 묻는다.

### 이 프로젝트에서의 적용 예시

- **새 도메인 Entity 추가 시:** 가구 격리 대상인가? (`@Filter("householdFilter")` 적용 여부) — `User` / `Household` / `HouseholdMember` 만 비격리이며 나머지는 모두 `household_id` + Filter 가 필수. 임의로 빠뜨리면 격리 누수.
- **세션 principal(`CustomUserDetails`) 필드 추가/변경 시:** `CustomUserDetailsService.loadUserByUsername` + `SessionHouseholdContextFilter`(주입) + 필요 시 `HouseholdContext` 까지 동기화. 한 곳만 고치지 말 것. (이전엔 JWT 클레임이었지만 M4 에서 JWT 인프라 전부 제거됨.)
- **영수증 분석 실패 보고 받으면:** 어느 layer 인지부터 확인 — `ReceiptStorage` (디스크 IO) / `ReceiptAnalysisService` (Claude 호출+JSON 파싱) / 카테고리 매칭 (`ReceiptIngestionService.resolveCategory`) / `Transaction` insert. 추측 패치 금지.
- **새 SSR 화면 추가 시:** `Web*Controller` 컨벤션 — `record DTO + @ModelAttribute + @Valid + BindingResult`, 에러 시 `form` Map (원본값) + `errors` Map (필드 에러) 으로 재렌더. `th:field`/`@Setter` 안 씀. 인라인 편집 폼은 `budget.html` / `networth.html` 패턴 (`th:action` 으로 CSRF 자동 주입).
- **격리 누수 함정:** `findById` (= `EntityManager.find`, PK 직접 로드) 는 Hibernate `@Filter` 가 안 걸려 타 가구 row 도 로드함. 단건 조회는 `findOne(Specification)` (criteria 쿼리, 필터 적용) 또는 `findAll().filter()` 사용. `TransactionService.get/update` (M1), `NetWorthService.update*/delete*` (M3) 가 같은 함정으로 수정된 전례 — 같은 실수 반복 금지.
- **`User`/`Household`/`HouseholdMember` 는 `@Filter` 미적용** (전역 식별 단위). 멤버십 검증은 `findByHouseholdIdAndUserId` 같은 메서드로 코드에서 직접 가드 (관리자 비번 재설정의 가드 패턴 참조).

---

## 2. Simplicity First (단순함이 먼저)

**문제를 해결하는 최소 코드. 투기적인 것은 없다.**

- 요청되지 않은 기능은 추가하지 않는다.
- 1회용 코드에 추상화 계층을 만들지 않는다.
- 요청되지 않은 "유연성"이나 "설정 가능성"을 끼워넣지 않는다.
- 발생할 수 없는 시나리오에 대한 예외 처리를 하지 않는다.
- 200줄로 쓴 것이 50줄로 가능했다면, 다시 쓴다.

자문해라: **"시니어 엔지니어가 이건 오버엔지니어링이라고 할까?"** 그렇다면 단순화한다.

### 이 프로젝트에서의 안티 패턴

- `CategoryResolver` 인터페이스 + 구현체로 분리하지 마라. `ReceiptIngestionService.resolveCategory` 안의 fallback 체인 (정확 일치 → "기타 변동" → 첫 VARIABLE → 첫 카테고리) 정도면 충분.
- 단일 화면 스타일 1개 보정에 `static/css/page-*.css` 분리하지 마라. `static/css/app.css` (현재 단일 파일) 안에 그대로. 화면별 스타일이 진짜로 커지면 그때 분리 검토.
- `application-dev.yml` / `application-stg.yml` / `application-prd.yml` 분리는 도입 X. 현재는 `application.yml` + `application-secret.yml` (gitignored) + env 오버라이드 (운영도 동일 — `docker-compose.prod.yml` 에서 env 주입).
- `account-batch` 가 비어 있어도 첫 잡 추가 시 `AbstractScheduledJob` 같은 부모 클래스부터 만들지 마라. 한 잡으로 끝나면 한 클래스로 끝낸다.
- 인라인 폼 저장/삭제를 위해 새 `Web*Service` 만들기 전에 기존 `TransactionService` / `NetWorthService` / `MerchantHistoryService` 등에 메서드 추가가 가능한지부터 본다 (Web 컨트롤러 = 얇은 어댑터 컨벤션).
- **MVP scope (`docs/account.md` §10 결정 사항 + §8 백로그) 를 임의로 확장하지 마라**: 회원가입/초대 UI, OWNER/MEMBER 권한 차등(관리자 페이지의 OWNER 게이트는 예외적 최소 적용), 카테고리 커스터마이징 UI, FCM 푸시, 결혼지출 화면 — 전부 v1.1 / v1.5 / v2 로 유예된 항목.

---

## 3. Surgical Changes (외과적 변경)

**필요한 곳만 건드린다. 내가 만든 흔적만 정리한다.**

기존 코드를 수정할 때:

- 인접한 코드, 주석, 포매팅을 "개선"하지 않는다.
- 망가지지 않은 것을 리팩토링하지 않는다.
- 내가 다르게 작성할 스타일이라도, **기존 스타일에 맞춘다.**
- 무관한 dead code를 발견하면 *언급만* 한다. 삭제하지 않는다.

내 변경이 고아(orphan)를 만들었다면:

- *내 변경*으로 인해 사용되지 않게 된 import/변수/함수만 제거한다.
- 변경 전부터 있던 dead code는 요청 없이 제거하지 않는다.

검증 기준: **변경된 모든 라인은 사용자 요청과 직접 연결되어야 한다.**

### 이 프로젝트에서의 특별 주의

- **`docs/account.md` §10 의 결정 사항** (Java 21, ~~iOS 유예→무효~~, Claude 키 분리, 영수증 5년 보관, 가구 멤버 모두 수정 + 변경 이력, 홈+카메라 FAB, Multi-tenant + 세션 principal) 은 단독 변경 금지. 변경 사유 + 영향 보고 후 사용자 승인.
- **`account-core/build.gradle.kts` 의 "다른 어떤 account-* 모듈에도 의존하지 않음" 정책은 의도된 분리**. `account-ai` 의 인터페이스 (`MerchantHistoryProvider` 등) 를 implements 하는 어댑터가 필요하면 `account-api` 에 배치 (`JpaMerchantHistoryProvider` 가 그 예).
- **한국어 주석 + Lombok + record 패턴은 기존 코드 컨벤션**. "더 모던하게" 라는 이유로 일관성 깨지 X.
- **다음 설정값은 의도되어 있다 — 무관 작업 중 만지지 마라**: `spring.jpa.hibernate.ddl-auto: validate` (Flyway 단독 책임), `spring.flyway.baseline-on-migrate: false` (드리프트 즉시 발각), `spring.threads.virtual.enabled: true` (Loom), `application.yml` 의 `ACCOUNT_DB_PORT:3305` 기본값 (호스트 mysqld 3306 충돌 회피), bootRun 의 `workingDir = rootProject.projectDir` (루트 `application-secret.yml` 로드 위함).
- **`HouseholdIsolationIntegrationTest` 는 M4 에서 제거됨** — `/api`+JWT 전용 테스트라 SSR 단일화 후 의미를 잃음. 세션 경로 격리는 현재 수동 검증 (`owner1@example.com` → 22 카테고리, `owner2@example.com` → 5 카테고리, 익명 → `/login` 리다이렉트). 회귀 테스트 작성 시 Testcontainers Windows 비호환은 동일하므로 Linux CI 전제.

---

## 4. Goal-Driven Execution (목표 주도 실행)

**성공 기준을 정의한다. 검증될 때까지 루프를 돈다.**

작업을 검증 가능한 목표로 변환한다:

| 명령형 지시           | 목표형 변환                                                |
| --------------------- | ---------------------------------------------------------- |
| "validation 추가해줘" | "잘못된 입력에 대한 테스트를 쓰고, 통과시켜라"             |
| "버그 고쳐줘"         | "버그를 재현하는 테스트를 쓰고, 통과시켜라"                |
| "X를 리팩토링해줘"    | "리팩토링 전후로 테스트가 모두 통과하는지 확인하라"        |

다단계 작업은 짧은 계획을 먼저 제시한다:

```
1. [단계] → 검증: [확인 방법]
2. [단계] → 검증: [확인 방법]
3. [단계] → 검증: [확인 방법]
```

강한 성공 기준은 LLM이 독립적으로 루프를 돌게 한다.
약한 기준("동작하게 해줘")은 계속 추가 질문을 요구한다.

### 이 프로젝트에서의 검증 패턴

- **Backend Web 컨트롤러/Service 추가**: `./gradlew :account-api:compileJava` 통과 → `./gradlew :account-api:test` 통과 → (가능 시) `./gradlew :account-api:bootRun` 후 브라우저로 `/login` (`owner1@example.com / dev1234!`) → 해당 화면 검증. SQL 격리 확인은 `logging.level.org.hibernate.SQL=DEBUG` 켜고 `WHERE household_id = ?` 가 격리 엔티티 쿼리에 포함되는지 본다. 또는 `curl --cookie-jar c.txt --data "email=...&password=..." http://localhost:8080/login` 후 `curl -b c.txt http://localhost:8080/web/...`.
- **새 마이그레이션 추가**: `./gradlew :account-api:bootRun` → 기동 로그에 `Migrating schema "account" to version "Vx__..."` + `flyway_schema_history` 에 새 row → 시드/DDL 결과를 직접 SELECT 로 검증.
- **새 SSR 템플릿 추가**: 컴파일은 안 잡힘 (런타임 평가). bootRun + 브라우저로 렌더 직접 확인 — 새 Thymeleaf 식 (`#httpServletRequest`, `sec:authorize` 등) 사용 시 특히 주의.
- **격리에 영향 줄 수 있는 변경**: 자동 회귀 테스트 없음 — 수동으로 owner1 세션 → `/web/transactions` 22 카테고리, owner2 세션 → 5 카테고리, 익명 → `/login` 리다이렉트 시나리오 재현. `findById` 함정 (PK 직접 로드 → 필터 미적용) 사용했는지 코드 리뷰에서 반드시 확인.
- **시크릿 변경**: `git diff --cached | grep -iE "sk-ant|password.*=.*[a-zA-Z0-9]{8}|secret.*=.*[a-zA-Z0-9]{16}"` 결과가 비어있는지 확인 (`docs/account.md` §9.2 패턴).

---

## 5. 주요 커맨드

전제: 프로젝트 루트 = `D:\account-app`. PowerShell 또는 bash 모두 가능. Gradle 명령은 wrapper (`./gradlew`) 사용.

### 부팅 / 셋업

```bash
# 시크릿 템플릿 복사 (최초 1회) — Claude API 키 필요 (JWT secret 은 M4 에서 제거됨)
cp application-secret.yml.example application-secret.yml
# 그리고 application-secret.yml 의 값 채우기 — 절대 커밋 금지

# MariaDB (호스트 포트 3305, 호스트 3306 은 기존 mysqld 점유)
docker compose up -d

# 백엔드 기동 — Flyway 가 V1__init / V2__seed_dev / V3__... 자동 적용
./gradlew :account-api:bootRun

# 브라우저로 http://localhost:8080/login
# 로컬 시드 계정 (V3__seed_dev_bcrypt_passwords): 4명 모두 비번 "dev1234!"
#   owner1@example.com  (OWNER, 우리집)       — 관리자 페이지 접근 가능
#   member1@example.com (MEMBER, 우리집)
#   owner2@example.com  (OWNER, 테스트가구)
#   member2@example.com (MEMBER, 테스트가구)
```

### 빌드 / 테스트

```bash
# 전체 빌드 (테스트 포함)
./gradlew build

# 테스트 제외 빌드
./gradlew build -x test

# 모듈 단독 컴파일 / 테스트
./gradlew :account-api:compileJava
./gradlew :account-ai:test
./gradlew :account-core:test

# 단일 테스트 클래스/메서드
./gradlew :account-ai:test --tests "ReceiptAnalysisServiceTest"
./gradlew :account-ai:test --tests "ReceiptAnalysisServiceTest.parsesCleanJson"
```

### 시크릿 스캔 (커밋 전 권장)

```bash
git diff --cached | grep -iE "sk-ant|password.*=.*[a-zA-Z0-9]{8}|secret.*=.*[a-zA-Z0-9]{16}"
# 결과가 비어있어야 정상
```

---

## 6. 아키텍처 개요

### 6.1 모듈 구조 + 의존성 정책

```
account-core   ←─── account-api ←─── (없음)
   ↑                    ↓
   └── account-batch    └── account-ai
```

- **`account-core`**: 도메인 Entity (12개) + Repository + Multi-tenant 격리 본체 (`HouseholdContext`, `HouseholdFilterAspect`, Hibernate `@Filter`) + Flyway 마이그레이션. **다른 어떤 `account-*` 모듈에도 의존 X** (`build.gradle.kts` 의 강한 정책).
- **`account-ai`**: Claude Vision API 호출 + 프롬프트 조립 + JSON 파싱. **다른 어떤 `account-*` 모듈에도 의존 X**. `MerchantHistoryProvider` 같은 인터페이스만 외부에 공개.
- **`account-api`**: **Thymeleaf SSR 컨트롤러** (`Web*Controller`, `/web/**`) + **Spring Security 세션 + formLogin** + 영수증 인제스천 흐름. `account-core` + `account-ai` 둘 다에 의존하는 유일한 모듈 — 어댑터 (예: `JpaMerchantHistoryProvider`) 는 이쪽에 배치. ~~REST 컨트롤러 + JWT 인증~~ 은 M4 (2026-05-27) 에 제거됨.
- **`account-batch`**: 월말 집계 / 이미지 정리 계획만 — **현재 비어 있음**. `account-core` 에만 의존, `account-api` 의존 금지.
- ~~`flutter_app`~~: M4 (2026-05-27) 에 디렉터리 삭제 — Thymeleaf SSR 단일화.

### 6.2 Multi-tenant 격리 흐름 (본 프로젝트의 가장 중요한 디자인)

```
1. 브라우저가 JSESSIONID 쿠키 첨부 요청
2. Spring Security 가 세션에서 SecurityContext + CustomUserDetails 복원
3. SessionHouseholdContextFilter 가 principal.activeHouseholdId 를
   HouseholdContext.set(Long) 로 ThreadLocal 바인딩
4. @Transactional 메서드 진입 시 HouseholdFilterAspect 가
   Hibernate Session 에 householdFilter 활성화 (currentHouseholdId = ctx 값)
5. Repository 의 모든 쿼리에 자동으로 WHERE household_id = ? 첨가
6. 응답 직전 finally 블록에서 HouseholdContext.clear()
```

핵심 보장: **`HouseholdContext` 미설정 상태에서 격리 엔티티를 조회하면 `-1` sentinel 로 필터가 켜져 0 rows 반환** (`HouseholdFilterAspect.NO_TENANT_SENTINEL`). 인증 단계 누수에 대한 두 번째 방어선이다 — 임의로 끄지 말 것.

비격리 Entity (Filter 미적용): `User`, `Household`, `HouseholdMember`. 나머지 9개 도메인 Entity는 `@Filter("householdFilter")` 가 클래스에 적용되어 있다 — 비격리 엔티티는 코드로 `findByHouseholdId*` 가드 (관리자 비번 재설정의 멤버십 검증 패턴).

### 6.3 핵심 흐름

- **로그인**: `WebAuthController.login` → Spring Security formLogin (`usernameParameter=email`) → `CustomUserDetailsService.loadUserByUsername` (user + 첫 HouseholdMember 조회) → `CustomUserDetails(userId, activeHouseholdId, role, email, passwordHash)` → SecurityContext + HttpSession 저장 → `defaultSuccessUrl=/web/home`.
- **영수증 인제스천**: `WebReceiptController` → `ReceiptIngestionService.ingest` (@Transactional 단일 트랜잭션) → `ReceiptStorage.store` (디스크) + `Receipt` insert + `MerchantHistoryProvider.getRecentHistory` + `ReceiptAnalysisService.analyze` (Claude) + 카테고리 fallback 매칭 + DRAFT `Transaction` insert → `receipts/confirm.html` 렌더 (전체필드 편집 + DRAFT→CONFIRMED 확정은 `POST /web/transactions/{id}` 재사용).
- **거래 목록**: `WebTransactionController.list` → `TransactionService.list` → `JpaSpecificationExecutor` 로 동적 필터 (from/to/categoryId/type/status) + 페이지네이션 + `occurred_at DESC`, soft-delete (`deletedAt IS NULL`) 자동 제외 → `transactions/list.html` 날짜별 그룹.
- **관리자 (OWNER 전용)**: `WebAdminController` → `AdminUserService.listMembers / resetPassword` → BCrypt 인코딩 후 `User.changePassword(hash)`. 가구 경계는 `findByHouseholdIdAndUserId` 로 직접 가드 (User/HouseholdMember 비격리).

---

## 프로젝트 고유 규칙

위 가이드라인 외에 이 저장소에서 항상 적용되는 규칙:

- **`docs/account.md` 가 단일 진실 원천이다.** 별도 "구현 가이드" / "통합 가이드" / 새 README 같은 문서는 사용자가 명시 요청하기 전엔 만들지 않는다.
- **시크릿 분리 + 절대 커밋 금지** (`application-secret.yml`, `.env`, `*.pem`, `*.key`, `local.properties`). 과거 OAuth 키 노출 사고 재발 방지. 커밋 전 §5 의 시크릿 스캔 명령으로 검증.
- **환경 의존 값 (DB 호스트/포트/계정, Claude API 키, 영수증 저장 경로) 은 전부 환경변수 + `application-secret.yml` 로 외부화**. 코드 하드코딩 금지. (~~JWT secret~~ 은 M4 이후 불필요.)
- **Lombok 사용 OK, `@Setter` 절대 금지**. Entity 상태 변경은 비즈니스 메서드 (`confirm()`, `softDelete()`, `markProcessed()` 등). DTO 는 Java 21 `record`.
- **Repository 에 raw SQL / `@Query` 금지**. 메서드 이름 derivation 또는 `JpaSpecificationExecutor`. `@Query` 가 진짜 필요하면 사유를 PR 본문에 적는다.
- **DB 통합 테스트는 Testcontainers + MariaDB 이미지만**. H2 등 인메모리 대체 금지 (방언 차이로 운영과 결과가 달라진다).
- **커밋 메시지는 Conventional Commits**: `feat|fix|refactor|chore|docs|test|build|ci`(`scope`): description. scope 는 `core` / `api` / `ai` / `batch` / `build` / `infra`. (~~`flutter`~~ 는 M4 이후 폐기.)
- **커밋 메시지에 AI / Claude 공동 작성 표기 금지**. `Co-Authored-By: Claude`, `🤖 Generated with Claude Code` 같은 trailer / 문구를 넣지 않는다. 커밋 메시지는 변경 내용만 담는다.
- 한국어로 답변/주석 OK. 단 **변수명/함수명/커밋 메시지는 영어** 통일.

---

**이 가이드라인이 잘 작동하고 있다는 신호:**

- diff에 불필요한 변경이 줄어든다 — 요청한 변경만 나타난다.
- 오버엔지니어링으로 인한 재작성이 줄어든다 — 처음부터 단순하다.
- 실수 *후*가 아니라 구현 *전*에 명확화 질문이 온다.
- 깔끔하고 최소한의 PR — 지나가다가 하는 "개선"이 없다.
