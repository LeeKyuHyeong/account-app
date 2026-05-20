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
