import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../models/transaction.dart';
import '../providers/transaction_providers.dart';

class TransactionListScreen extends ConsumerStatefulWidget {
  const TransactionListScreen({super.key});

  @override
  ConsumerState<TransactionListScreen> createState() =>
      _TransactionListScreenState();
}

class _TransactionListScreenState extends ConsumerState<TransactionListScreen> {
  static final _currency = NumberFormat.currency(locale: 'ko_KR', symbol: '₩', decimalDigits: 0);
  static final _dateHeader = DateFormat('M월 d일 (E)', 'ko_KR');

  final _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
  }

  @override
  void dispose() {
    _scrollController.removeListener(_onScroll);
    _scrollController.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (_scrollController.position.pixels >=
        _scrollController.position.maxScrollExtent - 200) {
      ref.read(transactionListProvider.notifier).loadMore();
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(transactionListProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('거래'),
        actions: [
          IconButton(
            tooltip: '새로고침',
            icon: const Icon(Icons.refresh),
            onPressed: () =>
                ref.read(transactionListProvider.notifier).refresh(),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () async {
          final created = await context.push<bool>('/transactions/new');
          if (created == true && context.mounted) {
            await ref.read(transactionListProvider.notifier).refresh();
          }
        },
        icon: const Icon(Icons.add),
        label: const Text('거래 추가'),
      ),
      body: state.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => _ErrorRetry(message: '$e', onRetry: () {
          ref.read(transactionListProvider.notifier).refresh();
        }),
        data: (data) {
          if (data.items.isEmpty) {
            return const Center(child: Text('아직 거래가 없습니다. 우측 하단 버튼으로 추가하세요.'));
          }
          final grouped = _groupByDate(data.items);
          return RefreshIndicator(
            onRefresh: () => ref.read(transactionListProvider.notifier).refresh(),
            child: ListView.builder(
              controller: _scrollController,
              itemCount: grouped.length + 1,
              itemBuilder: (context, index) {
                if (index == grouped.length) {
                  if (data.isLoadingMore) {
                    return const Padding(
                      padding: EdgeInsets.all(16),
                      child: Center(child: CircularProgressIndicator()),
                    );
                  }
                  if (!data.hasNext) {
                    return const Padding(
                      padding: EdgeInsets.all(16),
                      child: Center(child: Text('마지막입니다')),
                    );
                  }
                  return const SizedBox.shrink();
                }
                final group = grouped[index];
                return _DateGroup(
                  date: group.date,
                  dateHeader: _dateHeader,
                  items: group.items,
                  currency: _currency,
                );
              },
            ),
          );
        },
      ),
    );
  }

  static List<_DateGroupData> _groupByDate(List<TransactionItem> items) {
    final map = <DateTime, List<TransactionItem>>{};
    for (final t in items) {
      final key = DateTime(t.occurredAt.year, t.occurredAt.month, t.occurredAt.day);
      map.putIfAbsent(key, () => []).add(t);
    }
    final entries = map.entries.toList()
      ..sort((a, b) => b.key.compareTo(a.key));
    return entries.map((e) => _DateGroupData(e.key, e.value)).toList();
  }
}

class _DateGroupData {
  const _DateGroupData(this.date, this.items);
  final DateTime date;
  final List<TransactionItem> items;
}

class _DateGroup extends StatelessWidget {
  const _DateGroup({
    required this.date,
    required this.dateHeader,
    required this.items,
    required this.currency,
  });

  final DateTime date;
  final DateFormat dateHeader;
  final List<TransactionItem> items;
  final NumberFormat currency;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          color: theme.colorScheme.surfaceContainerHighest,
          child: Text(
            dateHeader.format(date),
            style: theme.textTheme.labelLarge,
          ),
        ),
        ...items.map((t) => _TransactionTile(t: t, currency: currency)),
      ],
    );
  }
}

class _TransactionTile extends StatelessWidget {
  const _TransactionTile({required this.t, required this.currency});
  final TransactionItem t;
  final NumberFormat currency;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isIncome = t.isIncome;
    final amountColor = isIncome ? Colors.blue.shade700 : theme.colorScheme.onSurface;
    final sign = isIncome ? '+' : '-';

    return ListTile(
      title: Text(t.merchant ?? t.categoryName),
      subtitle: Row(
        children: [
          Text(t.categoryName, style: theme.textTheme.bodySmall),
          if (t.isDraft) ...[
            const SizedBox(width: 8),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
              decoration: BoxDecoration(
                color: theme.colorScheme.tertiaryContainer,
                borderRadius: BorderRadius.circular(4),
              ),
              child: Text(
                'DRAFT',
                style: theme.textTheme.labelSmall?.copyWith(
                  color: theme.colorScheme.onTertiaryContainer,
                ),
              ),
            ),
          ],
        ],
      ),
      trailing: Text(
        '$sign${currency.format(t.amount)}',
        style: theme.textTheme.titleMedium?.copyWith(
          color: amountColor,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

class _ErrorRetry extends StatelessWidget {
  const _ErrorRetry({required this.message, required this.onRetry});
  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.error_outline, size: 48),
            const SizedBox(height: 16),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            FilledButton(onPressed: onRetry, child: const Text('다시 시도')),
          ],
        ),
      ),
    );
  }
}
