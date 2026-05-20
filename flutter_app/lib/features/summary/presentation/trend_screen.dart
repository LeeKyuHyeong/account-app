import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../data/summary_api.dart';
import '../models/monthly_summary.dart';

/// 최근 6개월 추이 — 수입 / 지출 / 잉여금 3개 라인.
///
/// 데이터 소스: `GET /api/summary/monthly/series?from=...&to=...` (6개월).
/// y 축 단위: 만원 (₩10,000) — 라벨이 ₩1,234,567 처럼 길어지면 차트 좌측이 잡아먹힘.
class TrendScreen extends ConsumerWidget {
  const TrendScreen({super.key});

  static final _monthLabel = DateFormat('M월', 'ko_KR');

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(recentSixMonthsProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('최근 6개월 추이')),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('로드 실패: $e'),
              const SizedBox(height: 8),
              FilledButton(
                onPressed: () => ref.invalidate(recentSixMonthsProvider),
                child: const Text('다시 시도'),
              ),
            ],
          ),
        ),
        data: (series) {
          if (series.isEmpty) {
            return const Center(child: Text('데이터가 없습니다'));
          }
          return Padding(
            padding: const EdgeInsets.fromLTRB(16, 24, 24, 16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _Legend(),
                const SizedBox(height: 8),
                Text(
                  'y 축 단위: 만원',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                const SizedBox(height: 16),
                Expanded(child: _Chart(series: series, monthLabel: _monthLabel)),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _Legend extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
      children: const [
        _LegendDot(color: Colors.blue, label: '수입'),
        _LegendDot(color: Colors.orange, label: '지출'),
        _LegendDot(color: Colors.green, label: '잉여'),
      ],
    );
  }
}

class _LegendDot extends StatelessWidget {
  const _LegendDot({required this.color, required this.label});
  final Color color;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 12,
          height: 12,
          decoration: BoxDecoration(color: color, shape: BoxShape.circle),
        ),
        const SizedBox(width: 6),
        Text(label, style: Theme.of(context).textTheme.bodyMedium),
      ],
    );
  }
}

class _Chart extends StatelessWidget {
  const _Chart({required this.series, required this.monthLabel});
  final List<MonthlySummary> series;
  final DateFormat monthLabel;

  static const double _yScale = 10000; // 만원

  @override
  Widget build(BuildContext context) {
    final incomeSpots = <FlSpot>[];
    final expenseSpots = <FlSpot>[];
    final surplusSpots = <FlSpot>[];
    for (var i = 0; i < series.length; i++) {
      final s = series[i];
      incomeSpots.add(FlSpot(i.toDouble(), s.income / _yScale));
      expenseSpots.add(FlSpot(i.toDouble(), s.totalExpense / _yScale));
      surplusSpots.add(FlSpot(i.toDouble(), s.surplus / _yScale));
    }

    final allValues = [
      ...incomeSpots.map((s) => s.y),
      ...expenseSpots.map((s) => s.y),
      ...surplusSpots.map((s) => s.y),
    ];
    final maxY = allValues.fold<double>(0, (m, v) => v > m ? v : m);
    final minY = allValues.fold<double>(0, (m, v) => v < m ? v : m);
    final padding = (maxY - minY) * 0.1;
    final chartMin = (minY - padding).floorToDouble();
    final chartMax = (maxY + padding).ceilToDouble();

    return LineChart(
      LineChartData(
        minX: 0,
        maxX: (series.length - 1).toDouble(),
        minY: chartMin,
        maxY: chartMax == chartMin ? chartMin + 1 : chartMax,
        gridData: const FlGridData(show: true, drawVerticalLine: false),
        titlesData: FlTitlesData(
          rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
          topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
          bottomTitles: AxisTitles(
            sideTitles: SideTitles(
              showTitles: true,
              interval: 1,
              getTitlesWidget: (value, meta) {
                final idx = value.round();
                if (idx < 0 || idx >= series.length) return const SizedBox.shrink();
                final ym = _parseYearMonth(series[idx].yearMonth);
                return Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(monthLabel.format(ym),
                      style: Theme.of(context).textTheme.bodySmall),
                );
              },
            ),
          ),
          leftTitles: AxisTitles(
            sideTitles: SideTitles(
              showTitles: true,
              reservedSize: 44,
              getTitlesWidget: (value, meta) => Text(
                value.toStringAsFixed(0),
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ),
          ),
        ),
        borderData: FlBorderData(show: false),
        lineBarsData: [
          _line(incomeSpots, Colors.blue),
          _line(expenseSpots, Colors.orange),
          _line(surplusSpots, Colors.green),
        ],
      ),
    );
  }

  LineChartBarData _line(List<FlSpot> spots, Color color) {
    return LineChartBarData(
      spots: spots,
      isCurved: false,
      color: color,
      barWidth: 2.5,
      dotData: const FlDotData(show: true),
    );
  }

  static DateTime _parseYearMonth(String s) {
    final parts = s.split('-');
    return DateTime(int.parse(parts[0]), int.parse(parts[1]));
  }
}
