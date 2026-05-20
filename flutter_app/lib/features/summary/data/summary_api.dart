import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/dio_provider.dart';
import '../models/monthly_summary.dart';

class SummaryApi {
  SummaryApi(this._dio);
  final Dio _dio;

  /// 월별 합계 — yearMonth 미지정 시 서버 기준 현재 월.
  Future<MonthlySummary> monthly({String? yearMonth}) async {
    final res = await _dio.get<Map<String, dynamic>>(
      '/api/summary/monthly',
      queryParameters: {'yearMonth': ?yearMonth},
    );
    return MonthlySummary.fromJson(res.data!);
  }

  /// 시계열 합계 — from(포함) ~ to(미포함), 최대 24개월.
  Future<List<MonthlySummary>> series({
    required String from,
    required String to,
  }) async {
    final res = await _dio.get<List<dynamic>>(
      '/api/summary/monthly/series',
      queryParameters: {'from': from, 'to': to},
    );
    return res.data!
        .cast<Map<String, dynamic>>()
        .map(MonthlySummary.fromJson)
        .toList();
  }
}

final summaryApiProvider = Provider<SummaryApi>((ref) {
  return SummaryApi(ref.watch(dioProvider));
});

/// 이번 달 합계 — 화면 진입 시마다 호출 (autoDispose). 다른 PR 이 거래를 변경한 후
/// refresh 가 필요하면 ref.invalidate(currentMonthSummaryProvider).
final currentMonthSummaryProvider =
    FutureProvider.autoDispose<MonthlySummary>((ref) {
  return ref.read(summaryApiProvider).monthly();
});

/// 최근 6개월 시계열 (현재 월 포함). 차트 화면 진입 시마다 fresh fetch.
final recentSixMonthsProvider =
    FutureProvider.autoDispose<List<MonthlySummary>>((ref) {
  final now = DateTime.now();
  // from = 현재 월에서 5개월 전 (포함), to = 다음 달 1일 (미포함) → 정확히 6개월.
  final fromYm = DateTime(now.year, now.month - 5);
  final toYm = DateTime(now.year, now.month + 1);
  String fmt(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}';
  return ref.read(summaryApiProvider).series(from: fmt(fromYm), to: fmt(toYm));
});
