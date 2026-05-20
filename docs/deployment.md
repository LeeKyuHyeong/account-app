# 배포 가이드 (Week 6)

운영 환경 (kyuhyeong.com VPS) 에 account-app 을 첫 배포할 때의 순서.

> 본 가이드는 사람이 손으로 한 번 따라가는 절차를 정리한 것. CI/CD 가 자동화하는 부분은
> [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) 참고. **CD 자동화 (이미지
> push + VPS 재시작) 는 별도 PR 에서 추가** — 본 가이드는 수동 배포 + CI(테스트만)
> 단계까지를 다룬다.

## 0. 사전 준비 (사용자 액션)

| 항목 | 작업 | 비고 |
|---|---|---|
| DNS | `account.kyuhyeong.com` A 레코드 → VPS IP | Cafe24 DNS 콘솔 |
| Claude 키 | https://console.anthropic.com → API Keys 발급 | Spend limits 월 $10 권장 |
| JWT secret | `openssl rand -base64 48` 로 생성 | 로컬과 다른 강한 값 |
| DB password | `pwgen -s 32 1` 로 생성 (root + app 각각) | 로컬 `accountlocal` 과 분리 |

## 1. VPS 준비

```bash
# Docker / docker compose 설치 확인
docker --version
docker compose version

# 작업 디렉토리
sudo mkdir -p /opt/account-app
sudo chown $USER:$USER /opt/account-app
cd /opt/account-app

# 데이터 디렉토리 (compose volume 대상)
sudo mkdir -p /var/lib/account-app/{mariadb,receipts}
sudo chown -R $USER:$USER /var/lib/account-app
```

## 2. 코드 복사

```bash
git clone https://github.com/LeeKyuHyeong/account-app.git .
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

```bash
# 로그인
curl -X POST https://account.kyuhyeong.com/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"<운영-시드-사용자>", "password":"<password>"}'

# 카테고리 조회 (Bearer 토큰 필요)
curl https://account.kyuhyeong.com/api/categories \
  -H "Authorization: Bearer $TOKEN"

# 이번 달 합계
curl https://account.kyuhyeong.com/api/summary/monthly \
  -H "Authorization: Bearer $TOKEN"
```

## 7. Flutter 클라이언트 빌드 (Android)

운영용 release APK 는 별도 업로드 키로 서명해야 한다. `flutter_app/android/key.properties`
가 없으면 `app/build.gradle.kts` 가 debug 키로 자동 fallback 하므로 CI (`flutter build apk
--debug`) 와 로컬 dev 흐름은 키스토어 없이도 그대로 동작한다.

### 7.1 Upload keystore 생성 (1회)

`flutter_app/android/` 에서:

```bash
keytool -genkey -v -keystore upload-keystore.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias upload \
  -dname "CN=Kyuhyeong Lee, OU=, O=, L=Seoul, ST=, C=KR"
```

keystore 비밀번호 + alias 비밀번호 2개를 입력한다. **둘 다 비밀번호 매니저에 즉시 저장**한다.
분실 시 Play Store 에 같은 패키지명으로 업데이트 업로드가 영구 불가 → 앱 신규 등록 강제.
키스토어 파일 자체도 (1Password attachment / 별도 USB / 암호화된 클라우드 백업 등) 다중
백업 필수.

### 7.2 `key.properties` 작성

```bash
cd flutter_app/android
cp key.properties.example key.properties
chmod 600 key.properties
$EDITOR key.properties  # storePassword / keyPassword / storeFile 채우기
```

`storeFile` 은 `flutter_app/android/` 기준 상대경로. 위 §7.1 처럼 같은 디렉토리에 두면
`upload-keystore.jks` 그대로. `key.properties` 와 `*.jks` 는 `.gitignore` 로 차단되어 있다.

### 7.3 Release APK 빌드

```bash
cd flutter_app
flutter build apk --release \
  --dart-define=API_BASE_URL=https://account.kyuhyeong.com
```

산출물: `build/app/outputs/flutter-apk/app-release.apk`. 단말에 설치 후 로그인 → 거래 목록
1회 동작 확인. Play Store Internal Track 업로드는 본 키로 서명된 APK 만 수용한다.

## 8. CI

`.github/workflows/ci.yml` 이 push / PR 시 자동:
- Backend: Java 21 + Gradle 빌드 + 모든 테스트
- Flutter: analyze + test + debug APK

CI 실패는 main branch protection 으로 머지 차단 (GitHub 설정에서 별도 활성화 필요).

## 9. 운영 운영 (롤링 업데이트)

코드 푸시 후 VPS 에서:

```bash
cd /opt/account-app
git pull
docker compose -f docker-compose.prod.yml --env-file .env.prod build account-api
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d account-api
```

DB 마이그레이션 (V4, V5, ...) 이 포함된 경우 Flyway 가 기동 시 자동 적용.

CD 자동화 (별도 PR) 추가 후에는 git push to main → GitHub Actions → SSH 로 위 명령 자동 실행 흐름이 된다.
