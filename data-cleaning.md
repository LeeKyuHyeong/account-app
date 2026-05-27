# 운영 DB 데이터 클리닝 (dev 시드 제거)

> ⚠ **운영(production) 데이터 삭제 절차 — 비가역.** 반드시 §0 백업 후 진행.
> 대상: VPS 의 `account-app-mariadb-prod` 컨테이너 `account` 스키마.
> **상태: 아직 미적용 (2026-05-27 기준).**

## 배경

Flyway 시드(`V2__seed_dev` + `V3__seed_dev_bcrypt_passwords`)가 운영 DB 에도 그대로 적용되어
**테스트용 가구/사용자 + 약한 공통 비밀번호(`dev1234!`)** 가 들어있다. 운영에는 본인 가구만 남기고
테스트가구 + 약한 비번 계정을 정리한다.

| user id | email | 가구 | 처리 |
|---|---|---|---|
| 1 | owner1@example.com  | 우리집(id 1) OWNER  | **유지** (실사용, 비번 변경 완료) |
| 2 | member1@example.com | 우리집(id 1) MEMBER | §2 — 비번 변경 또는 삭제 |
| 3 | owner2@example.com  | 테스트가구(id 2) OWNER  | **삭제** |
| 4 | member2@example.com | 테스트가구(id 2) MEMBER | **삭제** |

## FK 정책 (중요)

모든 도메인 테이블 FK 는 `ON DELETE RESTRICT` (cascade 없음). 따라서 **자식 테이블부터** 순서대로
삭제하고, 전체를 트랜잭션으로 묶어 검증 후 `COMMIT`/`ROLLBACK` 한다.

## 0. 백업 (필수)

```bash
docker exec account-app-mariadb-prod sh -c 'exec mariadb-dump -uroot -p"$MARIADB_ROOT_PASSWORD" account' \
  > /root/account-backup-$(date +%F).sql
ls -lh /root/account-backup-*.sql      # 파일 생성 확인
```

## 1. 테스트가구(household 2) + 전용 사용자(3, 4) 삭제

```bash
docker exec -it account-app-mariadb-prod mariadb -u root -p account
```
```sql
START TRANSACTION;

SELECT id, name FROM households WHERE id = 2;   -- '테스트가구' 인지 눈으로 확인

-- 자식 → 부모 순서 (RESTRICT)
DELETE FROM transaction_history WHERE household_id = 2;
DELETE FROM transactions        WHERE household_id = 2;
DELETE FROM monthly_summaries   WHERE household_id = 2;
DELETE FROM merchant_history    WHERE household_id = 2;
DELETE FROM receipts            WHERE household_id = 2;
DELETE FROM assets              WHERE household_id = 2;
DELETE FROM liabilities         WHERE household_id = 2;
DELETE FROM wedding_items       WHERE household_id = 2;
DELETE FROM categories          WHERE household_id = 2;
DELETE FROM household_members   WHERE household_id = 2;
DELETE FROM households          WHERE id = 2;
DELETE FROM users               WHERE id IN (3, 4);   -- owner2, member2

-- 검증: households=1, users=2, categories=22 면 정상
SELECT (SELECT COUNT(*) FROM households) AS households_left,
       (SELECT COUNT(*) FROM users)      AS users_left,
       (SELECT COUNT(*) FROM categories) AS categories_left;

-- 정상이면 COMMIT;  이상하면 ROLLBACK;
```

## 2. member1 (user 2) — 비번 변경 또는 삭제

household 1(우리집) 멤버. 아직 `dev1234!` 라 둘 중 하나로 처리한다.

### (A) 배우자용 유지 → 비번만 변경 (권장)
```bash
docker run --rm httpd:2.4-alpine htpasswd -bnBC 10 "" 'MEMBER1_NEW_PW' | tr -d ':\n'
```
```sql
UPDATE users SET password_hash='$2y$10$....(위 출력)' WHERE email='member1@example.com';
```

### (B) 안 쓰면 삭제 — 참조가 0 일 때만
```sql
SELECT 'tx' k, COUNT(*) c FROM transactions WHERE user_id=2 OR updated_by_user_id=2
UNION ALL SELECT 'receipt', COUNT(*) FROM receipts WHERE user_id=2
UNION ALL SELECT 'tx_hist', COUNT(*) FROM transaction_history WHERE changed_by_user_id=2
UNION ALL SELECT 'hh_owner', COUNT(*) FROM households WHERE owner_user_id=2;
-- 전부 c=0 이면:
START TRANSACTION;
DELETE FROM household_members WHERE household_id=1 AND user_id=2;
DELETE FROM users WHERE id=2;
SELECT COUNT(*) FROM users;   -- 1 (owner1 만)
COMMIT;
```
> c 가 하나라도 0 이 아니면 member1 이 만든 데이터가 있는 것 → 삭제 말고 (A) 비번 변경.

## 복구 (문제 시)

```bash
docker exec -i account-app-mariadb-prod sh -c 'exec mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" account' \
  < /root/account-backup-YYYY-MM-DD.sql
```

## 참고

- 비밀번호는 변경 UI 가 없어 DB 에서 직접 BCrypt 해시 교체 (Spring 은 `$2y/$2a/$2b` 모두 인식).
- 이 raw-SQL 운영을 UI 로 대체하는 것이 `TODO.md` 의 **관리자 페이지** 항목.
- owner1 운영 비밀번호는 이미 변경 완료. member1 만 §2 처리하면 약한 비번 계정 0.
