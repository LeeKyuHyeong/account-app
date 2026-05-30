-- V6__drop_unused_monthly_summaries_wedding_items.sql
--
-- dead 도메인 정리 (2026-05-30):
--   monthly_summaries — 매월 MonthlySummaryJob(account-batch)이 적재했으나 아무도 읽지 않는 write-only
--                       테이블. 월별/기간 집계는 MonthlySummaryService 가 transactions 에서 on-the-fly 로
--                       계산(부부 2명 규모엔 충분). 잡(MonthlySummaryJob + MonthlyAggregationService)도 함께 제거.
--   wedding_items     — 결혼 일시 지출(v1.1 유예) 도메인. 화면/서비스 없는 고아 테이블.
-- 둘 다 들어오는 FK 없음(leaf) → DROP 안전. 해당 엔티티/Repository 클래스도 함께 제거됨.
-- 향후 재도입 시(집계 캐싱 / 결혼 화면) 그 시점에 테이블+엔티티를 다시 추가한다.

DROP TABLE IF EXISTS monthly_summaries;
DROP TABLE IF EXISTS wedding_items;
