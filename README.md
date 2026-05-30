# Account-App

부부/가구 단위 가계부 앱. 영수증 사진을 찍으면 Claude Vision API가 OCR + 카테고리 자동 분류 후 저장한다. Multi-tenant(가구 단위) 구조로 처음부터 설계되어 추후 가까운 인원(20명 내외)으로의 확장이 가능.

**Repo**: <https://github.com/LeeKyuHyeong/account-app>
**현재 상태**: Thymeleaf SSR 단일 구조로 운영 배포 + CI/CD 가동 중 (Flutter/JWT/REST 레거시는 M4 에서 제거 완료). 상세는 [`TODO.md`](TODO.md)

## 설계 / 작업 지시서

- 전체 설계와 작업 페이즈: [`docs/account.md`](docs/account.md)
- 작업 우선순위 / 백로그: [`TODO.md`](TODO.md)

> **에이전트(Claude Code 등)는 항상 `docs/account.md`의 §0(작업 가이드)과 §8(백로그), 그리고 `TODO.md`를 먼저 읽고 그 안의 작업만 수행한다.**

## 모노레포 구성

| 모듈 | 상태 | 책임 |
|---|---|---|
| `account-ai` | ✅ 운영 | Claude Vision API 통합, 영수증 OCR + 카테고리 분류 |
| `account-api` | ✅ 운영 | Thymeleaf SSR 컨트롤러, 세션 인증(`SecurityConfig` 단일 webChain), 가구 격리 진입점 |
| `account-core` | ✅ 운영 | Entity 11 + `@Filter` 적용 + `HouseholdContext` + `HouseholdFilterAspect` + Flyway(V1~V6) |
| `account-batch` | ⏳ 비어 있음 | 영수증 단계적 압축/삭제 잡 (계획만). 반복 거래 스케줄러는 단일 잡이라 `account-api/recurring/` 에 거침 |
| `docs/` | ✅ 본 문서 | 설계 + 작업 지시서 |

## 구현 현황 (요약)

홈 · 거래(목록/입력/수정/soft-delete) · 영수증 AI 분석 · 카테고리 관리 · 예산 · 순자산 · 추이 차트 · 기간/연 결산(`/web/report`) · 거래 CSV 내보내기 · 반복 거래(KST 05:00 스케줄러) · 구독 플랜 티어(FREE/FAMILY/PRO, OWNER) · 관리자(멤버 목록 + 비번 재설정) 까지 SSR 로 구현 + 운영 배포.

세부 작업 이력 / 백로그는 [`TODO.md`](TODO.md), 전체 설계는 [`docs/account.md`](docs/account.md) 참조.

## 기술 스택

**Backend**: Java 21 (가상 스레드) · Spring Boot 3.3+ · MariaDB 11.x · Hibernate `@Filter` 기반 multi-tenant
**Frontend**: Thymeleaf SSR · Bootstrap · Chart.js (서버 렌더 + 최소 JS, 세션 인증)
**AI**: Claude Vision API (Sonnet 4.5, 가구별 가맹점 학습)
**Infra**: kyuhyeong.com VPS · nginx · Docker Compose · Let's Encrypt
**CI/CD**: GitHub Actions

## 7개 핵심 결정 (요약)

| # | 항목 | 결정 |
|---|---|---|
| 1 | Java 버전 | Java 21 + 가상 스레드 |
| 2 | 클라이언트 | ~~iOS/Flutter~~ → Thymeleaf SSR 단일 (M4, 2026-05-27) |
| 3 | Claude API | 별도 키 + Console 한도 설정 |
| 4 | 영수증 보관 | 5년 + 단계적 압축 + 가구별 정책 |
| 5 | 거래 권한 | 가구 멤버 모두 수정 + 변경 이력 로그 |
| 6 | 첫 화면 | 홈 + 카메라 FAB + 앱 아이콘 Quick Action |
| 7 | Multi-tenant | 처음부터 `household_id` 기반 격리 |

상세는 `docs/account.md` §10 참조.

## 시크릿 관리

`application-secret.yml`, `.env`, `*.pem`, `*.key`, `local.properties` 등은 `.gitignore`로 차단됨. 환경변수 또는 `application-secret.yml` 분리 사용. 자세한 내용은 `docs/account.md` §9.2 참조.

```yaml
# application.yml (커밋 OK)
account:
  claude:
    api-key: ${ACCOUNT_CLAUDE_API_KEY}   # 환경변수 주입
```

## 개발 시작

```bash
# 1) MariaDB 로컬 컨테이너 (호스트 포트 3305 — 3306 은 기존 mysqld 충돌 회피)
docker compose up -d

# 2) 빌드
./gradlew build

# 3) account-api 기동 (Flyway V1~V6 자동 적용 → 가구 2 + 카테고리 22/5 시드, 4계정 비번 dev1234!)
./gradlew :account-api:bootRun

# account-ai 단독 테스트
./gradlew :account-ai:test
```

> **로컬 DB 접속 기본값** (`application.yml` 디폴트, env 로 오버라이드 가능)
> `jdbc:mariadb://localhost:3305/account` · user `account` / pw `accountlocal`
> 운영에서는 `ACCOUNT_DB_HOST/PORT/USER/PASSWORD` 환경변수를 반드시 설정.

다음 진행 작업과 우선순위는 [`TODO.md`](TODO.md) 참조.
