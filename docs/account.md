# Account-App

> 부부/가구 단위 가계부 앱. 영수증 사진을 찍으면 Claude Vision API가 OCR + 카테고리 자동 분류 후 저장한다. Multi-tenant(가구 단위) 구조로 처음부터 설계되어 추후 가까운 인원(20명 내외)으로의 확장이 가능.

**Repo**: <https://github.com/LeeKyuHyeong/account-app>
**현재 페이즈**: `운영 — account.kyuhyeong.com 배포 완료 + 폰 UX 개선 진행 중` (☞ [TODO.md](../TODO.md))
**Last updated**: 2026-05-28

> **이력 메모**: 초안은 Flutter + JWT REST 기준이었으나 2026-05-26 부터 Thymeleaf SSR 로 마이그레이션, 2026-05-27 운영 배포 완료, JWT/REST/`flutter_app/` 는 모두 제거됐다. 본 문서는 **현재 상태** 만 담는다 — 작업 단위 / 의사결정 이력은 git log + [`TODO.md`](../TODO.md) 참조.

---

## 목차

0. [에이전트 작업 가이드](#0-에이전트-작업-가이드)
1. [프로젝트 컨텍스트](#1-프로젝트-컨텍스트)
2. [엑셀 → 앱 매핑 / 카테고리](#2-엑셀--앱-매핑--카테고리)
3. [핵심 기능: 영수증 OCR + AI 분류](#3-핵심-기능-영수증-ocr--ai-분류)
4. [시스템 아키텍처](#4-시스템-아키텍처)
5. [기술 스택](#5-기술-스택)
6. [데이터 모델 (Multi-tenant ER)](#6-데이터-모델-multi-tenant-er)
7. [API 설계 / 인증 / 보안](#7-api-설계--인증--보안)
8. [후속 버전 백로그](#8-후속-버전-백로그)
9. [작업 규칙](#9-작업-규칙)
10. [확정된 결정 사항](#10-확정된-결정-사항)
11. [비용 추정](#11-비용-추정)
12. [확장성 / 사업화 가능성](#12-확장성--사업화-가능성)
13. [부록: 재활용 자산 / 환경 정보](#13-부록-재활용-자산--환경-정보)

---

## 0. 에이전트 작업 가이드

이 문서를 작업 지시서로 사용하는 AI 에이전트(Claude Code 등)는 다음 규칙을 따른다.

### 0.1 본 문서의 사용법

- §1~§7은 **참조 영역**. 작업 중 의문이 생기면 해당 절을 찾아 의사결정 근거로 사용.
- §8은 **후속 버전 백로그**. v1.1 / v1.5 / v2 로 유예된 항목. 임의로 당겨오지 말 것.
- §9는 **모든 작업에 적용되는 규칙**. 코드 스타일, 시크릿 관리, 커밋 컨벤션 등.
- §10은 **변경 불가 결정 사항**. 임의로 뒤집지 말 것.
- 현재 진행 중인 작업은 본 문서가 아니라 [`TODO.md`](../TODO.md) 가 추적한다.

### 0.2 작업 진행 원칙

1. **현재 진행 작업은 [TODO.md](../TODO.md)**. 백로그(§8)와 규칙(§9·§10)은 본 문서 참조.
2. **작업 단위로 커밋** (§9.3 컨벤션).
3. **시크릿 절대 커밋 금지** (§9.2 참조). 환경변수 또는 `application-secret.yml` 분리.
4. **§10 결정을 임의로 변경하지 말 것**. 변경 필요 시 사용자에게 명시적 확인.
5. **모르는 것은 추측하지 말 것**. 특히 외부 의존(VPS IP, API 키, 도메인) 관련해 모호하면 사용자에게 질문.
6. **테스트 없이 완료 선언 금지** (§9.4).

### 0.3 작업 외 영역

- 본인 PC의 IDE 설정, OS 설정, GitHub Repo Settings(secret 등록 등) 같은 **사람만 할 수 있는 작업**은 에이전트가 직접 수행 불가. 사용자에게 단계별 안내만 제공.
- VPS 운영 명령(SSH 접속, nginx 설정 적용 등)은 사용자가 직접 수행. 에이전트는 적용할 설정 파일/명령어만 제공.

---

## 1. 프로젝트 컨텍스트

### 1.1 배경 및 목적

**현재**: 엑셀 가계부 2종(`우리집_자산관리.xlsx`, `우리집_가계부.xlsx`)으로 부부 자산 관리 중. 매월 말 카드 명세서 보고 카테고리별 합계 수기 입력. 이 과정의 마찰이 본질적 문제.

**앱으로 전환하는 이유**:
- 매월 말 일괄 입력 → **결제 발생 즉시 영수증 촬영**으로 마찰 제거
- 카테고리 분류 수기 → **AI 자동 분류**로 시간 단축
- 단일 엑셀 작성자 → **부부 동시 입력/동기화**
- 정적 시트 → **실시간 대시보드 + 푸시 알림**

**범위**:
- **MVP**: 본인 + 아내 (2명, private)
- **확장 가능성**: 가구(household) 단위 분리, 최대 20명 내외 (친구·가족 가구)
- **플랫폼**: ~~Flutter (iOS/Android)~~ → **Thymeleaf SSR 단일** (모바일 브라우저 우선, `max-width:640px` 폰 폭 강제). 2026-05-26 결정. 사유: Java 백엔드 커리어 집중.
- **호스팅**: 기존 kyuhyeong.com VPS 활용. `account.kyuhyeong.com` (호스트 nginx → 컨테이너 8085) — 2026-05-27 운영 가동.

### 1.2 모노레포 구성 (현재 + 계획)

| 모듈 | 상태 | 책임 |
|---|---|---|
| `account-ai` | ✅ 운영 | Claude Vision API 통합, 영수증 OCR + 카테고리 분류 (`ClaudeVisionClient` / `ReceiptAnalysisService`) |
| `account-api` | ✅ 운영 | **Thymeleaf SSR 컨트롤러**(`/web/**`) + **Spring Security 세션 + formLogin**. 영수증 인제스천 흐름. 가구 격리 진입점(`SessionHouseholdContextFilter`) |
| `account-core` | ✅ 운영 | Entity 12개, Repository, Multi-tenant 격리 본체(`HouseholdContext` + Hibernate `@Filter`) + Flyway V1~V3 |
| `account-batch` | ⏳ 비어 있음 | 월말 집계 / 이미지 정리 잡 (계획만, 구현 X) |
| ~~`flutter-app`~~ | ❌ 제거됨 (2026-05-27) | M4 정리에서 디렉터리 삭제 |
| `docs/` | ✅ 본 문서 + [`docs/deployment.md`](deployment.md) | 설계 + 운영 절차 |

### 1.3 핵심 외부 의존

| 의존 | 용도 | 비용 | 확보 상태 |
|---|---|---|---|
| Claude API (Vision) | 영수증 OCR + 분류 | 영수증 1장 ₩20~30 (Sonnet 4.5) | 사용자가 console.anthropic.com에서 발급 필요 |
| FCM (Firebase) | 푸시 알림 (P1) | 무료 | 추후 v1.1에서 셋업 |
| ~~Apple Developer~~ | ~~iOS TestFlight 배포~~ | ~~$99/년~~ | **불필요** — Flutter 폐기로 무효 (§10 #2). 네이티브 모바일 재도입 시에만 부활 |
| kyuhyeong.com VPS | 호스팅 | 0원 (기존 활용) | ✅ 운영 중 |
| MariaDB | DB | 0원 (VPS 내) | ✅ 설치됨 |

---

## 2. 엑셀 → 앱 매핑 / 카테고리

### 2.1 우리집_가계부.xlsx (가계부 본체)

| 엑셀 시트 | 앱 화면/기능 | 우선순위 |
|---|---|---|
| 대시보드 | 홈 화면 — 이번 달 요약, 잉여금, 순자산, 결혼 진행률 카드 | P0 |
| 설정 | 설정 화면 — 카테고리 예산 관리 (가구별) | P0 |
| 월별기록 | 거래 입력 화면 (일별 입력) + 월별/카테고리별 자동 집계 화면 | P0 |
| 결혼 일시 지출 | 결혼 프로젝트 화면 — 항목별 예산 vs 실제 진행률 | P1 |
| 순자산 | 순자산 화면 — 자산/부채 입력, 월별 추이 차트 | P1 |
| 사용법 | 온보딩 / 도움말 | P2 |

### 2.2 우리집_자산관리.xlsx (투자)

본 앱에 통합하지 않음. 이유:
- 갱신 주기 다름 (가계부 매일 vs 투자 월 1회)
- 사용자 다름 (가계부 부부 vs 투자 본인)
- 도메인 다름 (현금흐름 vs 자산평가)

대신 앱의 순자산 화면에서 **투자 자산 평가액만 외부 입력 필드로 받아옴**. 추후 v2에서 별도 모듈로 통합 검토.

### 2.3 카테고리 22개 (엑셀에서 이식, MVP 시드 데이터)

```
수입 (4):
  - 본인 월급, 아내 월급, 보너스/성과급, 기타 수입

고정지출 (5):
  - 주거, 통신, 보험, 구독, 부모님 용돈

변동지출 (8):
  - 식비, 외식/카페, 교통/주유, 문화/데이트,
    의류/미용, 의료/건강, 경조사, 기타 변동

투자/저축 (5):
  - 비상금, 청년미래적금, 본인 ISA, 아내 ISA, KB 직투
```

AI 분류는 변동지출 8개 중 하나로 매핑하는 케이스가 가장 많을 것. 각 가구는 자신의 카테고리를 추가/수정 가능 (가구별 격리, §6 참조).

---

## 3. 핵심 기능: 영수증 OCR + AI 분류

### 3.1 사용자 흐름

```
[1] 결제 후 영수증 사진 촬영 (모바일 브라우저 카메라
                              <input type="file" accept="image/*" capture="environment">)
       ↓
[2] 이미지 업로드 (multipart/form-data → POST /web/receipts)
       ↓
[3] 백엔드: 이미지를 Claude Vision API에 전달
       ↓
[4] Claude가 영수증 분석 → 구조화된 JSON 반환
   { "date": "2026-05-18", "merchant": "스타벅스 강남점",
     "category": "외식/카페", "items": [...], "total": 8500,
     "confidence": 0.95 }
       ↓
[5] DB에 거래 레코드(DRAFT) + 디스크에 원본 이미지 저장
       ↓
[6] receipts/confirm.html 렌더 → 사용자가 전체 필드 수정/확정 (1폼 제출)
       ↓
[7] 가구 내 다른 멤버에게 실시간 알림 (FCM, v1.1 — 현재 SSR 새로고침으로 대체)
```

### 3.2 분류 정확도 향상 전략

1. **가맹점 학습 테이블 (가구별)**: `merchant_history` 테이블에 가구별 분류 이력을 누적. 프롬프트 컨텍스트로 주입하여 같은 가맹점은 일관 분류.

2. **신뢰도 기반 처리**:
   - `confidence ≥ 0.8`: 자동 확정 (DRAFT → CONFIRMED)
   - `0.5 ~ 0.8`: 사용자 컨펌 요청 (기본값 제시)
   - `< 0.5`: 사용자가 수동 카테고리 선택

3. **수정 피드백 루프**: 사용자가 AI 분류를 수정하면 `merchant_history` UPSERT. 시간이 지날수록 정확도 자동 향상.

4. **카드 명세서 일괄 처리** (P2): 월말에 카드사 PDF 명세서 업로드 → Claude가 한 번에 100건씩 분류 → 영수증 없는 항목 보완.

### 3.3 모델 선택 전략

- **MVP 기본값**: `claude-sonnet-4-5` (정확도 우선, 영수증당 약 ₩20~30)
- **운영 안정화 후**: `claude-haiku-4-5`로 다운그레이드 검토 (영수증당 약 ₩5~10, 4~5배 저렴)
- **A/B 검증**: 첫 1~2개월간 동일 영수증을 두 모델로 모두 분류 → 정확도 차이 측정 후 결정

### 3.4 프롬프트 관리

- 위치: `account-ai/src/main/resources/prompts/receipt-analysis.txt`
- 자리표시자: `{{MERCHANT_HISTORY}}` — 가구별 가맹점 학습 이력을 런타임 주입
- 22개 카테고리 후보 + 한국 가맹점 분류 가이드 포함
- 응답은 순수 JSON (코드 펜스 금지) 강제. 단 모델이 가끔 어기므로 `ReceiptAnalysisService.extractJson()` 에서 방어적으로 처리.

---

## 4. 시스템 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│  Mobile Browser (Safari / Chrome — 폰 우선 SSR)             │
│  - Thymeleaf 렌더링 HTML + Bootstrap 5 + Chart.js (차트만)   │
│  - 카메라(file input capture) / 거래 입력 / 대시보드          │
└──────────────────┬──────────────────────────────────────────┘
                   │ HTTPS + Session Cookie (JSESSIONID)
┌──────────────────▼──────────────────────────────────────────┐
│  nginx (account.kyuhyeong.com) — reverse proxy + SSL        │
│  127.0.0.1:8085 → account-api 컨테이너                       │
└──────────────────┬──────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────┐
│  Spring Boot 3.3+ (Gradle multi-module, Java 21)            │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ account-api    Thymeleaf SSR + Spring Security 세션  │    │
│  │                SessionHouseholdContextFilter 진입    │    │
│  ├─────────────────────────────────────────────────────┤    │
│  │ account-core   Entity, Repository, Service          │    │
│  │                Hibernate @Filter (가구 격리 본체)     │    │
│  ├─────────────────────────────────────────────────────┤    │
│  │ account-ai     Claude Vision 통합 (RestClient)       │    │
│  ├─────────────────────────────────────────────────────┤    │
│  │ account-batch  (비어 있음 — 월말 잡 계획만)            │    │
│  └─────────────────────────────────────────────────────┘    │
└────────┬─────────────────────┬──────────────────┬───────────┘
         │                     │                  │
    ┌────▼────┐           ┌────▼────┐        ┌────▼────┐
    │ MariaDB │           │ Claude  │        │  FCM    │
    │ (Docker)│           │   API   │        │ (v1.1)  │
    └─────────┘           └─────────┘        └─────────┘
         │
    ┌────▼─────────┐
    │ /mnt/data/   │  영수증 이미지 (서버 디스크, 가구별 격리)
    │ receipts/    │  /receipts/{household_id}/{yyyy}/{mm}/...
    │   {hid}/...  │  Spring 세션 검증 후 stream 으로 응답
    └──────────────┘
```

### 핵심 설계 원칙

1. **단일 VPS에서 모두 호스팅** (기존 kyuhyeong.com 활용, 신규 비용 0)
2. **Docker Compose 단일 stack**: Spring Boot + MariaDB + nginx
3. **이미지는 가구별 디렉토리 격리** (S3 안 씀, 2~20인 사용에 용량 충분)
4. **모든 도메인 데이터는 `household_id`로 격리** (Hibernate `@Filter` 자동 적용)
5. **외부 의존은 Claude API + FCM 두 개뿐** (서드파티 결제·OCR 안 씀)

---

## 5. 기술 스택

### 5.1 프론트엔드 (Thymeleaf SSR — 2026-05-26 마이그레이션 후)

| 영역 | 라이브러리 / 도구 | 용도 |
|---|---|---|
| 템플릿 엔진 | `spring-boot-starter-thymeleaf` | `templates/**/*.html` SSR |
| 보안 dialect | `thymeleaf-extras-springsecurity6` | `sec:authorize` / `sec:authentication` |
| UI 프레임워크 | Bootstrap 5.3.3 (webjars) | 그리드/카드/폼/네비/유틸리티 |
| 차트 | Chart.js 4.4.3 (CDN) | 추이·순자산 차트 (페이지별 include) |
| 카메라 | HTML5 `<input type="file" capture="environment">` | 모바일 브라우저 카메라 |
| 폼 바인딩 | record DTO + @ModelAttribute + @Valid + BindingResult | th:field/setter 미사용 — `form` Map (원본 값) + `errors` Map (필드 에러) 재렌더 패턴 |
| 클라이언트 JS | inline `<script>` (페이지당 0~10줄) | 영수증 저신뢰도 컨펌 가드, 차트 데이터 주입. 전역 `static/js/` 없음 |
| 정적 CSS | `static/css/app.css` (단일 파일) | 폰 우선 폭 + safe-area + FAB 스타일 |
| 인증 | Spring Security 세션 (HttpSession + JSESSIONID 쿠키) | formLogin |

이전(2026-05-26 이전) Flutter 스택(`flutter_riverpod` / `go_router` / `dio` / `image_picker` / `fl_chart` / `drift` / `flutter_secure_storage` / `reactive_forms` / `local_auth`)은 **모두 폐기**. v1.5+ 네이티브 모바일 재도입 가능성은 별도 결정.

### 5.2 백엔드 (Spring Boot)

| 영역 | 기술 | 비고 |
|---|---|---|
| 언어 | **Java 21** (LTS) | 가상 스레드(Loom) 활용 — 영수증 I/O 대기에 적합 |
| 프레임워크 | Spring Boot 3.3+ | |
| 빌드 | Gradle 멀티 모듈 (Kotlin DSL) | ITSM toy 패턴 |
| ORM | Spring Data JPA + QueryDSL | 통계 쿼리 대비 |
| Multi-tenancy | Hibernate `@Filter` + `HouseholdContext` (ThreadLocal) | §6.2 참조 |
| DB | MariaDB 11.x | 기존 VPS 설치됨 |
| 마이그레이션 | **Flyway** | DB 스키마 버전 관리 |
| 인증 | Spring Security 세션 + formLogin | `CustomUserDetails(userId, activeHouseholdId, role, email, passwordHash)` 가 principal 로 HttpSession 직렬화 저장. ~~JWT(HS256, 15분/30일)~~ M4(2026-05-27) 에 모두 제거 |
| 검증 | Bean Validation | DTO 입력 검증 |
| AI 클라이언트 | **Spring `RestClient`** (직접 호출) | Anthropic Java SDK는 추후 검토. 가상 스레드 호환 |
| 이미지 처리 | `thumbnailator` 또는 `imgscalr` | 썸네일/압축 |
| 푸시 | Firebase Admin SDK (Java) | FCM (P1) |
| 배치 | Spring Batch | 월말 집계, 이미지 정리 |
| 변경 이력 | Hibernate Envers 또는 직접 구현 | `transaction_history` |
| 로깅 | Logback + JSON layout | nginx 로그와 통합 |
| 테스트 | JUnit 5 + Testcontainers + AssertJ | DB 통합 테스트 |

### 5.3 인프라

| 영역 | 도구 |
|---|---|
| 호스팅 | kyuhyeong.com VPS (Cafe24, 4GB) — 기존 활용 |
| 컨테이너 | Docker + Docker Compose |
| 리버스 프록시 | nginx (기존 셋업) |
| SSL | Let's Encrypt (certbot, 기존) |
| 도메인 | `account.kyuhyeong.com` (서브도메인 신규) |
| CI/CD | GitHub Actions (KH Shop 패턴) |
| 모바일 배포 | **별도 배포 없음** — 모바일 브라우저로 `account.kyuhyeong.com` 접속. ~~TestFlight/APK Internal Track~~ 은 Flutter 폐기와 함께 무효 |
| 시크릿 관리 | `application-secret.yml` 분리 + 환경변수 (KH Shop 패턴) |

---

## 6. 데이터 모델 (Multi-tenant ER)

### 6.1 전체 ER

```
─────────────────────────────────────────────────
[가구 / 멤버십] - 모든 데이터의 격리 단위
─────────────────────────────────────────────────

users
  id, email, password_hash, name, fcm_token,
  created_at, last_login_at

households
  id, name, plan_type(PERSONAL|FAMILY|PRO),  -- 추후 티어 확장
  owner_user_id (FK→users), data_retention_months,
  max_members, created_at

household_members
  id, household_id (FK), user_id (FK),
  role(OWNER|MEMBER), invited_by (FK→users, nullable),
  joined_at
  UNIQUE (household_id, user_id)

─────────────────────────────────────────────────
[도메인 데이터] - 모두 household_id로 격리
─────────────────────────────────────────────────

categories
  id, household_id (FK), name,
  type(INCOME|FIXED|VARIABLE|INVEST),
  budget_monthly, sort_order, created_at
  INDEX (household_id, sort_order)

transactions
  id, household_id (FK), user_id (FK, 입력자),
  category_id (FK), amount, occurred_at,
  merchant, payment_method, memo,
  receipt_id (FK, nullable), confidence,
  status(DRAFT|CONFIRMED),
  created_at, updated_at, updated_by_user_id (FK)
  INDEX (household_id, occurred_at)
  INDEX (household_id, category_id, occurred_at)

transaction_history  -- 변경 이력 (감사 로그)
  id, transaction_id (FK), household_id (FK),
  changed_by_user_id (FK), changed_at,
  change_type(CREATE|UPDATE|DELETE),
  before_json (TEXT), after_json (TEXT)
  INDEX (transaction_id, changed_at)

receipts
  id, household_id (FK), user_id (FK, 업로더),
  image_path,  -- /mnt/data/receipts/{hid}/{yyyy}/{mm}/{uuid}.jpg
  original_filename, file_size,
  ocr_raw_json (TEXT),  -- Claude 원본 응답 보관
  processed_at, created_at
  INDEX (household_id, created_at)

merchant_history  -- 가구별 가맹점 학습
  id, household_id (FK), merchant_name, category_id (FK),
  count, last_used_at
  UNIQUE (household_id, merchant_name)

monthly_summaries  -- 배치로 사전 계산
  id, household_id (FK), year_month,
  category_id (FK), total_amount, transaction_count
  UNIQUE (household_id, year_month, category_id)

assets / liabilities  -- 순자산용 (v1.1)
  id, household_id (FK), name, type, balance,
  recorded_at (YYYY-MM-01 단위)
  INDEX (household_id, recorded_at)

wedding_items  -- 결혼 일시 지출 (v1.1, 해당 가구만)
  id, household_id (FK), section, name, budget,
  actual, parent_support, memo, paid_at
```

### 6.2 가구 격리 메커니즘 (4단계)

1. **세션 principal**: 로그인 성공 시 `CustomUserDetails(userId, activeHouseholdId, role, ...)` 를 HttpSession 에 저장. 사용자가 여러 가구 소속이면 활성 가구 선택은 v1.5. (이전엔 JWT `household_id` 클레임)
2. **Spring 진입점**: `SessionHouseholdContextFilter`(OncePerRequestFilter) 가 principal 의 activeHouseholdId 를 `HouseholdContext` (ThreadLocal) 에 주입. 응답 후 clear (가상 스레드 재사용 시 누수 방지).
3. **Hibernate `@Filter`**: 모든 도메인 엔티티에 `@Filter("householdFilter", condition = "household_id = :current")` 활성화 (`HouseholdFilterAspect` 가 `@Transactional` 진입 시 활성화). `HouseholdContext` 미설정이면 `-1` sentinel 로 0 rows.
4. **Repository 강제**: `findAll` / `findOne(Specification)` 은 자동 필터 적용. **`findById` 는 PK 직접 로드라 필터가 안 걸려 격리 누수** — 이미 알려진 함정이고 `TransactionService.get/update` (M1), `NetWorthService.update*/delete*` (M3) 에서 `findOne(Specification)` 으로 교체됨. `User`/`Household`/`HouseholdMember` 는 비격리(@Filter 미적용) 이므로 코드로 직접 `findByHouseholdId` 가드 (관리자 비번 재설정에서 적용).

### 6.3 MVP 운영 (부부 단계 시드)

- `households` row 1개: `(id=1, name='우리집', plan_type='PERSONAL')`
- `household_members` row 2개: 본인 `OWNER`, 아내 `MEMBER`
- 회원가입/초대 화면 없음 (v1.5)
- 단, **컬럼·인덱스·필터링 로직은 처음부터 작동** → v1.5에서 초대 화면만 얹으면 끝

---

## 7. API 설계 / 인증 / 보안

### 7.1 엔드포인트 요약 (Thymeleaf SSR — 2026-05-27 M4 이후)

모든 인증 경로는 세션 principal 의 `activeHouseholdId` 로 자동 격리. `/api/**` REST 엔드포인트는 M4 에서 전부 제거됨. 모든 화면은 SSR HTML 응답이며, POST 폼은 `_csrf` 토큰 자동 주입 (CSRF 활성).

```
# 인증 / 진입점 (WebAuthController, Spring Security)
GET    /                                 # → /web/home 리다이렉트
GET    /login                            # 로그인 폼 (auth/login.html)
POST   /login                            # formLogin (usernameParameter=email)
POST   /logout                           # Spring Security 자동 처리 → /login?logout

# 홈 (WebHomeController)
GET    /web/home                         # 이번 달 카드 + 예산 초과 배너 + 진입 버튼

# 거래 (WebTransactionController)
GET    /web/transactions                 # 목록 — 날짜 그룹, 필터(from/to/categoryId/status), 페이지네이션
GET    /web/transactions/new             # 입력 폼
POST   /web/transactions/new             # 거래 생성 (record DTO + @Valid, 에러 시 재렌더)
GET    /web/transactions/{id}            # 수정 폼 (DRAFT 확정 체크박스)
POST   /web/transactions/{id}            # 전체필드 편집 + 선택적 DRAFT→CONFIRMED
                                           ↑ 영수증 컨펌도 이 엔드포인트 재사용 (confirm=true)

# 영수증 (WebReceiptController)
GET    /web/receipts/new                 # 업로드 폼 (file capture)
POST   /web/receipts                     # multipart 업로드 (10MB) → Claude 분석 → confirm.html

# 대시보드
GET    /web/trend                        # 최근 6개월 추이 차트 (WebTrendController)
GET    /web/budget                       # 카테고리별 진행률 + 인라인 예산 폼 (WebBudgetController)
POST   /web/budget                       # 카테고리 예산 수정
GET    /web/networth?ym=YYYY-MM          # 월 스냅샷 + 12개월 차트 + 자산/부채 인라인 편집 (WebNetWorthController)
POST   /web/networth/{assets|liabilities}                  # 추가
POST   /web/networth/{assets|liabilities}/{id}             # 인라인 편집 (이름·종류·잔액)
POST   /web/networth/{assets|liabilities}/{id}/delete      # 삭제

# 관리자 (WebAdminController) — OWNER 전용 (SecurityConfig hasRole)
GET    /web/admin                        # 가구 멤버 목록
POST   /web/admin/users/{userId}/password # 비밀번호 재설정 (8자 이상)

# v1.5 (가구 확장 시)
# 회원가입 / 가구 초대 / 멤버 추방 — 현재 raw SQL + 관리자 비번 재설정으로 갈음
```

### 7.2 실시간 동기화 전략

SSR 단일 — 가구 내 다른 멤버의 변경분은 페이지 **새로고침**으로 반영. 별도 풀링/푸시 없음.

- **즉시성 부족 케이스**: 부부가 동시에 같은 거래를 입력할 가능성 — 실사용에서는 매우 드물고, 발생해도 DB 레벨에서 두 row 가 별도로 적재되며 격리 영향 없음.
- **FCM Silent Push (v1.1)**: 별도 클라이언트 앱 부활 시에만 의미. 현재는 폐기 항목.

### 7.3 인증 흐름

1. 이메일 + 비밀번호 로그인 (`POST /login`, `usernameParameter=email`)
2. `CustomUserDetailsService.loadUserByUsername` 이 user + 첫 `HouseholdMember` 조회 → `CustomUserDetails(userId, activeHouseholdId, role, email, passwordHash)` 반환
3. Spring Security 가 BCrypt 검증 → SecurityContext + HttpSession 에 principal 저장 (JSESSIONID 쿠키 발급)
4. 매 요청 `SessionHouseholdContextFilter` 가 principal.activeHouseholdId 를 `HouseholdContext` 에 주입 → finally clear
5. 로그아웃: `POST /logout` (Spring Security) → 세션 무효화 → `/login?logout`
6. 여러 가구 소속 시 활성 가구 전환은 v1.5 (UI 미구현)

> ~~JWT(HS256, 15분/30일), `flutter_secure_storage` 토큰 보관, `/api/auth/refresh` 갱신, `/api/auth/switch-household`~~ M4(2026-05-27) 에 인프라(`JwtAuthenticationFilter`/`JwtTokenProvider`/`JwtProperties`/`AuthController`/`AuthService`/`AuthDtos`)와 함께 모두 제거.

### 7.4 보안 추가 조치

- **회원가입 외부 차단** (MVP): 가입 화면 없음, DB 시드로 2명 등록. `robots.txt` + `X-Robots-Tag: noindex` 로 검색엔진 노출 차단
- ~~앱 진입 시 생체 인증 (`local_auth`)~~ — Flutter 폐기로 무효. 브라우저 OS 잠금에 의존
- **Rate Limiting**: nginx + Spring 양쪽에서 IP/사용자별 (현재 미구현, v1.1 후속)
- **이미지 접근 제어**: 영수증은 세션 검증 후 Spring stream (nginx 직접 노출 X)
- **CSRF**: webChain 기본 활성 — 모든 POST 폼은 `_csrf` 자동 주입 (Thymeleaf-Spring 통합). 임의로 끄지 말 것
- ~~CORS: 모바일 앱만 허용~~ — SSR 단일이라 same-origin, CORS 설정 불필요
- **HTTPS 강제**: HSTS, http → https 리다이렉트 (호스트 nginx 담당)
- **백업**: MariaDB 일일 덤프 (cron) + 영수증 이미지 주 1회 Cloudflare R2 (무료 10GB)

### 7.5 영수증 보관 정책

- **업로드 시**: 즉시 1280px 이하로 리사이즈 + JPEG 80% (1장 약 200KB)
- **1년 경과**: 추가 압축 800px / JPEG 60% (1장 약 80KB) — Spring Batch 월 1회 잡
- **5년 경과**: 자동 삭제 — DB 거래 레코드와 `ocr_raw_json`은 유지
- **가구별 정책**: `households.data_retention_months` 컬럼으로 가구마다 다르게 적용 가능

---

## 8. 후속 버전 백로그

> 마이그레이션(M0~M4) + 운영 배포까지의 작업 단위와 의사결정 과정은 **git log + [`TODO.md`](../TODO.md) + commit history** 에 있다. 본 절은 **아직 안 한 일**만 둔다.

### v1.1 (MVP 후 점진)

- 결혼 일시 지출 화면
- FCM 푸시 (silent push 동기화 + 알림) — 별도 클라이언트 앱 재도입 시에만 유효, SSR 단일 동안은 새로고침
- 영수증 단계적 압축/삭제 배치 잡 (`account-batch` 첫 잡 후보)

### v1.5 (가구 확장 시)

- 가구 초대 플로우 (이메일)
- OWNER/MEMBER 역할 분리 강화 (예산 수정은 OWNER만)
- 회원가입 화면
- 가구별 카테고리 커스터마이징 UI
- 탈퇴/데이터 삭제 자동 배치

### v2 (장기 — 사업화 검토 단계)

- 카드 PDF 명세서 일괄 분류
- 음성 입력 거래
- 자산관리 엑셀 통합 (투자 모듈)
- 연말정산 시뮬레이터
- 구독 티어 (PERSONAL / FAMILY / PRO) — In-App Purchase
- 개인정보처리방침 본격 정비

---

## 9. 작업 규칙

### 9.1 코드 스타일

- **Lombok 사용 OK**. `@Getter`, `@Builder`, `@RequiredArgsConstructor`, `@NoArgsConstructor(access=PROTECTED)`, `@AllArgsConstructor(access=PRIVATE)` 까지.
- **`@Setter` 절대 금지**. Entity는 비즈니스 메서드를 통해서만 상태 변경. DTO는 record 또는 final 필드.
- **DTO는 Java 21 record 우선**. 여러 표현이 필요한 경우만 class.
- **Service 메서드는 명사형 X, 동사형 O**. `getCategoryList()` 보다 `findCategoriesByHousehold()`.
- **Optional 반환은 Repository까지만**. Service 이상에서는 도메인 예외 throw.
- **null 금지**. 컬렉션은 빈 컬렉션, 단일 객체는 Optional 또는 예외.
- **Java 21 패턴 적극 활용**: record, sealed, switch expression, pattern matching.
- **가상 스레드 활성화**: `application.yml`에 `spring.threads.virtual.enabled: true`

### 9.2 시크릿 관리

**절대 커밋하지 말 것**:
- `application-secret.yml`, `application-secret.yaml`, `application-secret.properties`
- `application-local.yml`, `application-local.yaml`
- `.env`, `.env.*`
- `*.pem`, `*.key`, `id_rsa*`
- Claude API 키 (`sk-ant-...`), JWT secret, DB 비밀번호

**커밋 전 검증**:
```bash
git diff --cached | grep -iE "sk-ant|password.*=.*[a-zA-Z0-9]{8}|secret.*=.*[a-zA-Z0-9]{16}"
# 결과가 비어있어야 정상
```

**환경변수 패턴** (KH Shop 사고 재발 방지):
```yaml
# application.yml
account:
  claude:
    api-key: ${ACCOUNT_CLAUDE_API_KEY}
  jwt:
    secret: ${ACCOUNT_JWT_SECRET}
  datasource:
    password: ${ACCOUNT_DB_PASSWORD}
```

### 9.3 커밋 메시지 컨벤션

Conventional Commits 형식:

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

- **type**: `feat`, `fix`, `refactor`, `chore`, `docs`, `test`, `build`, `ci`
- **scope**: `core`, `api`, `ai`, `batch`, `build`, `infra` (~~`flutter`~~ 폐기, 마이그레이션 후 SSR 변경은 `api` scope 으로 통합)
- **예시**:
  - `feat(core): add Household and HouseholdMember entities`
  - `feat(api): add OWNER-only admin page for password reset`
  - `test(api): mock NetWorthService isolation guard`
  - `fix(ai): handle code-fenced JSON response from Claude`
  - `docs: update §7 endpoints to /web/** after SSR migration`

### 9.4 테스트 정책

- **격리 검증은 필수**. 거래 / 영수증 / 순자산 / 카테고리 / 예산 등 격리 엔티티에 새 조회·수정 경로를 추가할 때 `findById`(필터 미적용) 함정에 빠지지 말 것 — `findOne(Specification)` 또는 `findAll().filter()` 패턴 사용. `User`/`Household`/`HouseholdMember` 비격리 엔티티는 코드로 `findByHouseholdId*` 가드. 자동 회귀 테스트(`HouseholdIsolationIntegrationTest`)는 M4 에서 제거됐으므로 owner1 / owner2 / 익명 세 세션으로 **수동 검증**.
- **단위 테스트는 핵심 비즈니스 로직만**. Getter/Setter 같은 trivial 테스트는 X.
- **DB 통합 테스트는 Testcontainers**. H2 등 인메모리 DB로 대체 X (방언 차이).
- **테스트 격리**: 각 테스트는 자체 데이터 시드. 다른 테스트의 부산물에 의존 X.
- **AssertJ 사용**. JUnit assertions 보다 가독성 ↑.

### 9.5 의존성 / 데이터 접근 규칙

- **`account-ai`는 `account-core`에 의존하지 않음** (인터페이스만 의존: `MerchantHistoryProvider`). 단방향 결합도 유지.
- **`account-api`는 `account-core` + `account-ai`에 의존**.
- **`account-batch`는 `account-core`에 의존**, `account-api` 의존 금지.
- **Repository에 raw SQL 메서드 추가 금지**. 메서드 이름 규칙 또는 QueryDSL 사용. `@Query` 어노테이션 사용 시 사용자에게 사유 보고.
- **`household_id` 없는 메서드 정의 금지**. 모든 Repository 조회 메서드는 `findByHouseholdIdAnd*` 형태.

### 9.6 모르는 것 처리

- 외부 의존(IP, 도메인, API 키, 계정 정보)이 명확하지 않으면 **추측 금지**. 사용자에게 명시적 질문.
- §10 결정 사항 변경이 필요해 보이면 **임의 변경 금지**. 사용자에게 사유와 영향 보고 후 승인.
- 새 라이브러리 도입 시 **이유 + 대안 비교**를 커밋 메시지나 PR 본문에 명시.

---

## 10. 확정된 결정 사항

| # | 항목 | 결정 | 변경 시 영향 |
|---|---|---|---|
| 1 | Java 버전 | **Java 21** + 가상 스레드 | toolchain 전체 재설정 |
| 2 | ~~iOS 배포~~ | **무효(2026-05-26)** — Flutter 폐기, SSR 단일. 모든 폰은 브라우저로 접속 | 네이티브 모바일 재도입 시 별도 결정 |
| 3 | Claude API | **별도 키 발급** + Console 한도 설정 ($10/월) | Anthropic 결제 카드 등록 |
| 4 | 영수증 보관 | **5년 + 단계적 압축 + 가구별 정책** | `households.data_retention_months` 활용 |
| 5 | 거래 권한 | **가구 멤버 모두 수정 + 변경 이력 로그** | `transaction_history` 자동 적재 |
| 6 | 첫 화면 | **홈 + 카메라 FAB** (~~앱 아이콘 Quick Action~~ 은 네이티브 전용이라 무효). 2026-05-28 FAB 구현 — `fragments/layout.html` 의 fixed 우하단 anchor → `/web/receipts/new`, `/web/receipts/*` 자기 자신에선 숨김 | 변경 시 FAB CSS(`.fab-camera`) + `body { padding-bottom }` 도 함께 |
| 7 | Multi-tenant | **모든 도메인 테이블 `household_id` + Hibernate Filter + 세션 principal** (이전엔 JWT 클레임) | 본 프로젝트 가장 중요한 결정 — 변경 시 전면 재작업 |

**MVP에서 의도적으로 제외**:
- 회원가입/초대 화면 (v1.5)
- OWNER/MEMBER 역할 차등 (v1.5) — 단, 관리자 페이지의 OWNER 게이트는 최소 적용 (운영 비번 재설정 UI)
- 가구별 카테고리 커스터마이징 UI (v1.5)
- FCM 푸시 (v1.1) — SSR 단일 동안은 새로고침으로 갈음
- 결혼 일시 지출 화면 (v1.1)

---

## 11. 비용 추정

| 항목 | 부부 단계 | 20명 확장 단계 |
|---|---|---|
| VPS | 0원 (기존 kyuhyeong.com 활용) | 0원 (4GB로 충분) |
| 도메인 | 0원 (서브도메인) | 0원 |
| Claude API | 약 ₩5,000/월 (영수증 200건 × Sonnet 4.5) | 약 ₩50,000/월 (10배) — Haiku 시 ₩10,000 |
| FCM | 0원 (무료 티어) | 0원 |
| Apple Developer | ~~$99/년~~ → **0원 (Flutter 폐기로 무효, §10 #2)**. 네이티브 모바일 재도입 시에만 부활 | 진입 시 동일 |
| 백업 스토리지 | 0원 (Cloudflare R2 무료 10GB) | 동일 |
| **합계** | **약 ₩5,000/월** (iOS 진입 전) | **약 ₩10,000~50,000/월** |

---

## 12. 확장성 / 사업화 가능성

기술적으로는 multi-tenant 설계로 자연스럽게 확장 가능:

- **소규모 (20명 내외)**: 현재 VPS로 충분, 비용 거의 변화 없음
- **중규모 사업화 (수백~수천 가구)**:
  - 디스크: 외부 오브젝트 스토리지로 이전 (Cloudflare R2, AWS S3)
  - DB: read replica 또는 더 큰 인스턴스
  - 결제: Apple/Google In-App Purchase 통합 (수수료 15~30%)
  - 법적: 개인정보처리방침 게재, 개인정보보호책임자 지정
  - 운영: CS 채널, 모니터링/알람 강화

`plan_type` 컬럼이 이미 있어 티어 운영 가능 (PERSONAL / FAMILY / PRO). 단, **본격 사업화는 별도 결정 사항이며 본 문서의 범위가 아님**. 포트폴리오 관점에서는 "확장 가능한 멀티테넌트 아키텍처를 의식하고 설계한 프로젝트" 자체가 면접 어필 포인트.

---

## 13. 부록: 재활용 자산 / 환경 정보

### 13.1 본인 코드 재활용 가능 지점

- **ITSM toy 프로젝트** (참고용 — 이미 본 프로젝트 코드에 적용 완료):
  - Gradle 멀티 모듈 구조
  - ~~JWT + HttpOnly 쿠키~~ — Flutter/REST 폐기로 무관해짐, 세션 기반으로 전환
  - Spring Batch 9개 잡 → `account-batch` 첫 잡 작성 시 참고
  - 환경 변수 분리 패턴

- **KH Shop**:
  - `application-secret.properties` 분리 패턴 → §9.2
  - GitHub Actions CI/CD → 본 프로젝트 `ci.yml` + `deploy.yml` 에 적용 완료
  - OAuth2 (Google/Kakao/Naver) → v1.5 에서 활용 가능

- ~~MyStar Flutter 앱~~ — Flutter 폐기 후 재활용 가치 사라짐 (Riverpod / go_router / Flutter 프로젝트 구조 모두 무관). v1.5+ 네이티브 모바일 재도입 시에만 다시 의미.

- **MCP 서버 (YouTube)**:
  - Claude API 클라이언트 패턴 (RestClient 사용으로 변경됨)
  - JSON 파싱 / 에러 처리

- **kyuhyeong.com 모니터링 대시보드**:
  - Spring Actuator → 운영 모니터링 통합

- **AssetInsight-Monorepo** (자산 스냅샷, 별도 보존):
  - 직접 코드 재활용은 X (컨셉 다름)
  - 단, OAuth 통합 / GitHub Actions 패턴 참고 가능

### 13.2 운영 환경 정보

- **VPS**: kyuhyeong.com, 175.125.21.245, Cafe24 4GB
- **OS**: CentOS / RHEL 계열
- **기존 서비스 도메인**: shop/game/itsm/api.kyuhyeong.com (각각 다른 Docker 컨테이너)
- **운영 중 (2026-05-27~)**: `account.kyuhyeong.com` — 호스트 nginx → `127.0.0.1:8085` → `account-api` 컨테이너. CD: GitHub Actions `deploy.yml` (main push → `production` 환경 → SSH `git pull` + `docker compose up -d account-api`)
- **MariaDB (운영)**: `account-app-mariadb-prod` 컨테이너 내부 전용 (호스트 미노출). 시드 정리 절차는 [`data-cleaning.md`](../data-cleaning.md)
- **MariaDB (로컬 dev)**: `docker compose up -d` → 호스트 포트 3305 (3306 은 mysqld 점유 회피)
- **nginx**: 이미 reverse proxy 셋업됨, account 서브도메인 server block 추가됨
- **SSL**: certbot 이미 셋업, account.kyuhyeong.com 인증서 발급 완료

### 13.3 .gitignore 표준 (이미 적용됨, 변경 시 주의)

`.gitignore`는 시크릿 사전 차단을 위한 안전장치. 임의로 제거하지 말 것. 특히 다음 패턴:

```gitignore
**/application-secret.yml
**/application-secret.yaml
**/application-secret.properties
**/application-local.yml
**/*.env
.env*
*.pem
*.key
**/receipts/    # 실제 영수증 이미지 (실명/실금액)
```

### 13.4 의문 사항 발생 시

본 문서로 해결되지 않는 의문이 생기면:

1. 본 문서 검색 (Ctrl+F) — 같은 키워드가 다른 절에 있을 수 있음
2. 그래도 모호하면 사용자에게 명시적 질문 (추측 금지)
3. 외부 정보가 필요하면: Spring/Hibernate/Flutter 공식 docs 우선, Stack Overflow 신중히 활용

---

*이 문서는 단순 설계도가 아니라 작업 지시서다. §8의 작업이 완료되면 본 문서의 "현재 페이즈" 표시(문서 상단)와 §8 자체를 업데이트하여 다음 페이즈로 진행한다.*
