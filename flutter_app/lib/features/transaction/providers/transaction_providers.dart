import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/transaction_api.dart';
import '../models/transaction.dart';

/// 거래 목록 상태 — 누적 페이지 + 다음 페이지 존재 여부.
class TransactionListState {
  const TransactionListState({
    required this.items,
    required this.page,
    required this.hasNext,
    required this.isLoadingMore,
  });

  const TransactionListState.initial()
      : items = const [],
        page = -1,
        hasNext = true,
        isLoadingMore = false;

  TransactionListState copyWith({
    List<TransactionItem>? items,
    int? page,
    bool? hasNext,
    bool? isLoadingMore,
  }) {
    return TransactionListState(
      items: items ?? this.items,
      page: page ?? this.page,
      hasNext: hasNext ?? this.hasNext,
      isLoadingMore: isLoadingMore ?? this.isLoadingMore,
    );
  }

  final List<TransactionItem> items;
  final int page;
  final bool hasNext;
  final bool isLoadingMore;
}

class TransactionListNotifier extends AsyncNotifier<TransactionListState> {
  static const _pageSize = 30;

  @override
  Future<TransactionListState> build() async {
    return _loadPage(const TransactionListState.initial(), 0);
  }

  Future<TransactionListState> _loadPage(
    TransactionListState current,
    int nextPage,
  ) async {
    final api = ref.read(transactionApiProvider);
    final response = await api.list(page: nextPage, size: _pageSize);
    return current.copyWith(
      items: [...current.items, ...response.content],
      page: response.page,
      hasNext: response.hasNext,
      isLoadingMore: false,
    );
  }

  Future<void> loadMore() async {
    final current = state.value;
    if (current == null || !current.hasNext || current.isLoadingMore) return;
    state = AsyncData(current.copyWith(isLoadingMore: true));
    state = await AsyncValue.guard(() => _loadPage(current, current.page + 1));
  }

  Future<void> refresh() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(
      () => _loadPage(const TransactionListState.initial(), 0),
    );
  }
}

final transactionListProvider =
    AsyncNotifierProvider<TransactionListNotifier, TransactionListState>(
  TransactionListNotifier.new,
);

/// 지출 카테고리 (입력 폼용 dropdown).
final expenseCategoriesProvider = FutureProvider<List<Category>>((ref) async {
  // 수동 입력은 보통 지출 (VARIABLE / FIXED). 일단 전부 노출하고 폼에서 그룹핑.
  return ref.read(transactionApiProvider).listCategories();
});
