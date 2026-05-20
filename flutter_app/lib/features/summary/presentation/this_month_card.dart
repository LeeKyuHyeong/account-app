import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../data/summary_api.dart';
import '../models/monthly_summary.dart';

/// 홈 화면 상단 "이번 달" 카드 — 수입 / 지출 / 잉여금 3행.
///
/// 잉여금 정의는 백엔드와 동일: 수입 − (고정지출 + 변동지출). 투자/저축은 잉여금 계산
/// 에서 제외되어, "이번 달 얼마 남았나" 직관과 일치.
class ThisMonthCard extends ConsumerWidget {
  const ThisMonthCard({super.key});

  static final _currency = NumberFormat.currency(
    locale: 'ko_KR',
    symbol: '₩',
    decimalDigits: 0,
  );

  static final _monthFmt = DateFormat.yMMM('ko_KR');

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(currentMonthSummaryProvider);
    final theme = Theme.of(context);

    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: async.when(
          loading: () => const SizedBox(
            height: 140,
            child: Center(child: CircularProgressIndicator()),
          ),
          error: (e, _) => SizedBox(
            height: 140,
            child: Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text('이번 달 집계 로드 실패',
                      style: theme.textTheme.titleMedium),
                  const SizedBox(height: 8),
                  TextButton(
                    onPressed: () =>
                        ref.invalidate(currentMonthSummaryProvider),
                    child: const Text('다시 시도'),
                  ),
                ],
              ),
            ),
          ),
          data: (s) => _Content(summary: s, currency: _currency, monthFmt: _monthFmt),
        ),
      ),
    );
  }
}

class _Content extends StatelessWidget {
  const _Content({
    required this.summary,
    required this.currency,
    required this.monthFmt,
  });

  final MonthlySummary summary;
  final NumberFormat currency;
  final DateFormat monthFmt;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final monthLabel = monthFmt.format(_parseYearMonth(summary.yearMonth));
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(monthLabel, style: theme.textTheme.titleMedium),
        const SizedBox(height: 16),
        _Row(label: '수입', value: summary.income, color: Colors.blue.shade700),
        const SizedBox(height: 8),
        _Row(label: '지출', value: summary.totalExpense, color: theme.colorScheme.onSurface),
        const Divider(height: 24),
        _Row(
          label: '잉여금',
          value: summary.surplus,
          color: summary.surplus >= 0
              ? Colors.green.shade700
              : theme.colorScheme.error,
          emphasize: true,
        ),
        if (summary.invest > 0) ...[
          const SizedBox(height: 8),
          Text(
            '투자/저축 ${currency.format(summary.invest)} 별도',
            style: theme.textTheme.bodySmall,
          ),
        ],
      ],
    );
  }

  /// "2026-05" 형식을 DateTime 으로 — DateFormat.yMMM 입력용.
  static DateTime _parseYearMonth(String s) {
    final parts = s.split('-');
    final year = int.parse(parts[0]);
    final month = int.parse(parts[1]);
    return DateTime(year, month);
  }
}

class _Row extends StatelessWidget {
  const _Row({
    required this.label,
    required this.value,
    required this.color,
    this.emphasize = false,
  });

  final String label;
  final double value;
  final Color color;
  final bool emphasize;

  static final _currency = NumberFormat.currency(
    locale: 'ko_KR',
    symbol: '₩',
    decimalDigits: 0,
  );

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final valueStyle = emphasize
        ? theme.textTheme.headlineSmall?.copyWith(
            color: color, fontWeight: FontWeight.w600)
        : theme.textTheme.titleMedium?.copyWith(color: color);
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(label, style: theme.textTheme.bodyLarge),
        Text(_currency.format(value), style: valueStyle),
      ],
    );
  }
}
