import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
// StateProvider 는 Riverpod 3.x 에서 legacy.dart 로 이동했다.
import 'package:flutter_riverpod/legacy.dart';

import '../../../core/network/dio_provider.dart';
import '../models/networth.dart';

/// 순자산 API — 스냅샷 조회 + 자산/부채 CRUD.
class NetWorthApi {
  NetWorthApi(this._dio);
  final Dio _dio;

  /// 월별 스냅샷 — yearMonth 미지정 시 서버 기준 현재 월.
  Future<NetWorthSnapshot> snapshot({String? yearMonth}) async {
    final res = await _dio.get<Map<String, dynamic>>(
      '/api/networth/snapshot',
      queryParameters: {'yearMonth': ?yearMonth},
    );
    return NetWorthSnapshot.fromJson(res.data!);
  }

  Future<AssetItem> createAsset({
    required String name,
    required String type,
    required double balance,
    required String yearMonth,
  }) async {
    final res = await _dio.post<Map<String, dynamic>>(
      '/api/assets',
      data: {
        'name': name,
        'type': type,
        'balance': balance,
        'yearMonth': yearMonth,
      },
    );
    return AssetItem.fromJson(res.data!);
  }

  Future<AssetItem> updateAsset({
    required int id,
    String? name,
    String? type,
    double? balance,
    String? yearMonth,
  }) async {
    final res = await _dio.patch<Map<String, dynamic>>(
      '/api/assets/$id',
      data: {
        'name': ?name,
        'type': ?type,
        'balance': ?balance,
        'yearMonth': ?yearMonth,
      },
    );
    return AssetItem.fromJson(res.data!);
  }

  Future<void> deleteAsset(int id) async {
    await _dio.delete<void>('/api/assets/$id');
  }

  Future<LiabilityItem> createLiability({
    required String name,
    required String type,
    required double balance,
    required String yearMonth,
  }) async {
    final res = await _dio.post<Map<String, dynamic>>(
      '/api/liabilities',
      data: {
        'name': name,
        'type': type,
        'balance': balance,
        'yearMonth': yearMonth,
      },
    );
    return LiabilityItem.fromJson(res.data!);
  }

  Future<LiabilityItem> updateLiability({
    required int id,
    String? name,
    String? type,
    double? balance,
    String? yearMonth,
  }) async {
    final res = await _dio.patch<Map<String, dynamic>>(
      '/api/liabilities/$id',
      data: {
        'name': ?name,
        'type': ?type,
        'balance': ?balance,
        'yearMonth': ?yearMonth,
      },
    );
    return LiabilityItem.fromJson(res.data!);
  }

  Future<void> deleteLiability(int id) async {
    await _dio.delete<void>('/api/liabilities/$id');
  }
}

final netWorthApiProvider = Provider<NetWorthApi>((ref) {
  return NetWorthApi(ref.watch(dioProvider));
});

/// 선택된 yearMonth (YYYY-MM). null = 서버 기준 현재 월. 화면 month picker 가 변경.
final selectedYearMonthProvider = StateProvider.autoDispose<String?>((_) => null);

/// 현재 선택 월의 순자산 스냅샷. yearMonth 변경 시 자동 refetch.
final netWorthSnapshotProvider =
    FutureProvider.autoDispose<NetWorthSnapshot>((ref) {
  final ym = ref.watch(selectedYearMonthProvider);
  return ref.read(netWorthApiProvider).snapshot(yearMonth: ym);
});
