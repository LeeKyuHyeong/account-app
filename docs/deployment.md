# 배포 가이드

운영 환경 (kyuhyeong.com VPS) 에 account-app 을 첫 배포할 때의 순서.

> 본 가이드는 **최초 1회 수동 셋업**(§1~6) 절차다. 이후 일상 배포는 **CD 자동화됨** —
> `main` push → [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml) 가
> `production` 환경 승인 게이트 통과 후 SSH 로 `git pull` + `docker compose build/up account-api`
> 까지 자동 수행 (§8 참조). CI(테스트)는 [`.github/workflows/ci.yml`](../.github/workflows/ci.yml).

## 0. 사전 준비 (사용자 액션)

| 항목 | 작업 | 비고 |
|---|---|---|
| DNS | `account.kyuhyeong.com` A 레코드 → VPS IP | Cafe24 DNS 콘솔 |
| Claude 키 | https://console.anthropic.com → API Keys 발급 | Spend limits 월 $10 권장 |
| DB password | `pwgen -s 32 1` 로 생성 (root + app 각각) | 로컬 `accountlocal` 과 분리 |

## 1. VPS 준비

```bash
# Docker / docker compose 설치 확인
docker --version
docker compose version

# 작업 디렉토리 (root 홈 아래 — 기존 /root/* 토이들과 동일)
cd /root

# 데이터 디렉토리 (compose volume 대상)
mkdir -p /var/lib/account-app/{mariadb,receipts}
```

## 2. 코드 복사

```bash
git clone https://github.com/LeeKyuHyeong/account-app.git
cd account-app
git checkout main
```

## 3. 시크릿 작성

```bash
cp .env.prod.example .env.prod
chmod 600 .env.prod
$EDITOR .env.prod  # 모든 placeholder 를 실제 값으로 교체
```

§0 에서 만든 시크릿을 그대로 옮긴다.

## 4. 빌드 + 기동

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod build
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d
```

첫 기동 시 Flyway 가 `V1__init` ~ 최신 마이그레이션을 자동 적용. 로그 확인:

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod logs -f account-api
```

`Started AccountApiApplication` 메시지가 보이면 정상. 시드 데이터 (가구 2 / 사용자 4) 도
같이 들어가는데, 운영에서는 본인 가구만 남기고 테스트 데이터는 정리한다 (별도 SQL).

## 5. nginx + Let's Encrypt

호스트의 기존 nginx 를 사용. 본 저장소의 [`infra/nginx/account.kyuhyeong.com.conf.example`](../infra/nginx/account.kyuhyeong.com.conf.example) 을 참고.

**첫 발급 절차** (HTTP 만 있는 임시 server 블록 → certbot → HTTPS 자동 추가):

```bash
# 1. HTTP only 임시 블록 (80 listen + /.well-known/acme-challenge/ + 나머지 502 또는 maintenance)
#    또는 example 의 80 블록만 떼서 sites-available/ 에 둔다
sudo cp infra/nginx/account.kyuhyeong.com.conf.example \
        /etc/nginx/sites-available/account.kyuhyeong.com.conf
# 임시로 443 블록을 주석 처리하고 80 블록만 활성
sudo ln -s /etc/nginx/sites-available/account.kyuhyeong.com.conf \
           /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx

# 2. Let's Encrypt 발급 (기존 certbot 활용)
sudo certbot --nginx -d account.kyuhyeong.com

# 3. certbot 이 자동으로 443 server 블록을 example 의 형태로 갱신한다.
#    필요 시 client_max_body_size / proxy timeout 등 example 의 세부 설정을 다시 머지.
sudo nginx -t && sudo systemctl reload nginx

# 4. 자동 갱신 확인 — certbot 의 systemd timer 또는 cron 이 기존에 설치되어 있다.
sudo systemctl list-timers | grep certbot
```

## 6. 동작 확인

브라우저로 `https://account.kyuhyeong.com` 접속 → 시드 사용자로 로그인(`/login`) →
홈(`/web/home`) 의 이번 달 요약 + 거래 목록(`/web/transactions`) 이 렌더되면 정상.

```bash
# 헬스 체크 — 인증 불필요한 로그인 페이지가 200 인지
curl -I https://account.kyuhyeong.com/login
```

## 7. CI

`.github/workflows/ci.yml` 이 push / PR 시 자동으로 Backend (Java 21 + Gradle 빌드 + 모든 테스트) 를 실행한다.

CI 실패는 main branch protection 으로 머지 차단 (GitHub 설정에서 별도 활성화 필요). CD(`deploy.yml`)는 CI 와 독립 트리거이므로, 배포 전 CI 초록 여부는 §8 승인 단계에서 눈으로 확인한다.

## 8. 운영 (롤링 업데이트) — CD 자동화됨

일상 배포는 **`main` push 시 `deploy.yml` 이 자동 수행**: `production` 환경 승인 게이트(Required reviewer 등록 시)에서 1-click 승인 → SSH 로 VPS 접속 → 아래 명령 자동 실행.

```bash
cd /root/account-app
git pull --ff-only
docker compose -f docker-compose.prod.yml --env-file .env.prod build account-api
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d account-api
docker image prune -f
```

DB 마이그레이션 (V4, V5, V6, ...) 이 포함된 경우 Flyway 가 기동 시 자동 적용.

> 필요한 GitHub Secrets: `DEPLOY_SSH_PRIVATE_KEY`, `DEPLOY_SSH_HOST` / Variables: `DEPLOY_SSH_USER`, `DEPLOY_SSH_PORT`, `DEPLOY_PATH`. 수동 배포가 필요하면 위 명령을 VPS 에서 직접 실행해도 된다. Actions 탭의 `workflow_dispatch` 로 수동 트리거도 가능.
