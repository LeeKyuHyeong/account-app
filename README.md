# Account-App

부부/가구 단위 가계부 앱. 영수증 사진을 찍으면 Claude Vision API가 OCR + 카테고리 자동 분류 후 저장한다. Multi-tenant(가구 단위) 구조로 처음부터 설계되어 추후 가까운 인원(20명 내외)으로의 확장이 가능.

**Repo**: <https://github.com/LeeKyuHyeong/account-app>
**현재 페이즈**: `Week 1 — 기반 + Multi-tenant 셋업` (Task 1~4 완료, Task 5~6 진행 예정)

## 설계 / 작업 지시서

- 전체 설계와 작업 페이즈: [`docs/account.md`](docs/account.md)
- 작업 우선순위 / 백로그: [`TODO.md`](TODO.md)

> **에이전트(Claude Code 등)는 항상 `docs/account.md`의 §0(작업 가이드)과 §8(현재 작업), 그리고 `TODO.md`를 먼저 읽고 그 안의 작업만 수행한다.**

## 모노레포 구성

| 모듈 | 상태 | 책임 |
|---|---|---|
| `account-ai` | ✅ 프로토타입 + 멀티 모듈 편입 완료 | Claude Vision API 통합, 영수증 OCR + 카테고리 분류 |
| `account-api` | 🟡 `HouseholdContextFilter` + `SecurityConfig` + 격리 검증 통과 (수동 curl) | REST 엔드포인트, JWT 인증, 가구 격리 진입점 |
| `account-core` | 🟢 Entity 12 + `@Filter` 적용 + `HouseholdContext` + `HouseholdFilterAspect` | Entity, Repository, Service. Multi-tenant 격리 본체 |
| `account-batch` | ⏳ Week 4+ | 월말 집계, 이미지 정리, 알림 발송 |
| `flutter-app` | ⏳ Week 2+ | 모바일 앱 (iOS/Android) |
| `docs/` | ✅ 본 문서 | 설계 + 작업 지시서 |

## Week 1 진행 현황

| Task | 내용 | 상태 |
|---|---|---|
| 1 | Gradle 멀티 모듈 루트 셋업 | ✅ 완료 |
| 2 | MariaDB Docker + Flyway 스키마 | ✅ 완료 |
| 3 | JPA Entity + Repository | ✅ 완료 |
| 4 | HouseholdContext + Hibernate Filter (격리 검증) | ✅ 완료 |
| 5 | JWT 인증 셋업 | ⏳ 다음 |
| 6 | `account-ai` 모듈 멀티 모듈 통합 (`ReceiptController` 이전) | ⏳ |

> **알려진 이슈**: `HouseholdIsolationIntegrationTest` 는 `@Disabled` 상태. Docker Desktop on Windows 의 CLI 프록시가 Testcontainers 의 docker-java 호출을 가로채는 [알려진 비호환](https://github.com/testcontainers/testcontainers-java/issues) 이슈. Linux CI 또는 Docker Desktop TCP 노출 활성화 시 어노테이션 제거하면 자동 실행. 본 단계에서는 동일 시나리오를 `curl -H "X-Household-Id: N"` 로 수동 검증 (22/5/0 결과 확인).

상세는 [`TODO.md`](TODO.md) 또는 `docs/account.md` §8 참조.

## 기술 스택

**Backend**: Java 21 (가상 스레드) · Spring Boot 3.3+ · MariaDB 11.x · Hibernate `@Filter` 기반 multi-tenant
**Mobile**: Flutter · Riverpod · go_router · drift (오프라인 큐)
**AI**: Claude Vision API (Sonnet 4.5, 가구별 가맹점 학습)
**Infra**: kyuhyeong.com VPS · nginx · Docker Compose · Let's Encrypt
**CI/CD**: GitHub Actions

## 7개 핵심 결정 (요약)

| # | 항목 | 결정 |
|---|---|---|
| 1 | Java 버전 | Java 21 + 가상 스레드 |
| 2 | iOS 배포 | TestFlight ($99/년) |
| 3 | Claude API | 별도 키 + Console 한도 설정 |
| 4 | 영수증 보관 | 5년 + 단계적 압축 + 가구별 정책 |
| 5 | 거래 권한 | 가구 멤버 모두 수정 + 변경 이력 로그 |
| 6 | 첫 화면 | 홈 + 카메라 FAB + 앱 아이콘 Quick Action |
| 7 | Multi-tenant | 처음부터 `household_id` 기반 격리 |

상세는 `docs/account.md` §11 참조.

## 시크릿 관리

`application-secret.yml`, `.env`, `*.pem`, `*.key`, `local.properties` 등은 `.gitignore`로 차단됨. 환경변수 또는 `application-secret.yml` 분리 사용. 자세한 내용은 `docs/account.md` §10.2 참조.

```yaml
# application.yml (커밋 OK)
account:
  claude:
    api-key: ${ACCOUNT_CLAUDE_API_KEY}   # 환경변수 주입
  jwt:
    secret: ${ACCOUNT_JWT_SECRET}
```

## 개발 시작

```bash
# 1) MariaDB 로컬 컨테이너 (호스트 포트 3305 — 3306 은 기존 mysqld 충돌 회피)
docker compose up -d

# 2) 빌드
./gradlew build

# 3) account-api 기동 (Flyway V1/V2 자동 적용 → 가구 2 + 카테고리 22/5 시드)
./gradlew :account-api:bootRun

# account-ai 단독 테스트
./gradlew :account-ai:test
```

> **로컬 DB 접속 기본값** (`application.yml` 디폴트, env 로 오버라이드 가능)
> `jdbc:mariadb://localhost:3305/account` · user `account` / pw `accountlocal`
> 운영에서는 `ACCOUNT_DB_HOST/PORT/USER/PASSWORD` 환경변수를 반드시 설정.

다음 진행 작업과 우선순위는 [`TODO.md`](TODO.md) 참조.
