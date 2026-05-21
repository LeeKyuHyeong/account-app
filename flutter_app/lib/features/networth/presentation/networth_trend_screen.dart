import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../data/networth_api.dart';
import '../models/networth.dart';

/// 최근 12개월 순자산 추이 — 자산 / 부채 / 순자산 3개 라인.
///
/// 데이터 소스: `GET /api/networth/history?from=...&to=...` (12개월).
/// y 축 단위: 만원 (₩10,000) — 라벨이 ₩123,456,789 처럼 길어지면 차트 좌측이 잡아먹힘.
class NetWorthTrendScreen extends ConsumerWidget {
  const NetWorthTrendScreen({super.key});

  static final _monthLabel = DateFormat('M월', 'ko_KR');

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(netWorthHistoryProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('최근 12개월 순자산')),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('로드 실패: $e'),
              const SizedBox(height: 8),
              FilledButton(
                onPressed: () => ref.invalidate(netWorthHistoryProvider),
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
                const _Legend(),
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
  const _Legend();

  @override
  Widget build(BuildContext context) {
    return const Row(
      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
      children: [
        _LegendDot(color: Colors.blue, label: '자산'),
        _LegendDot(color: Colors.red, label: '부채'),
        _LegendDot(color: Colors.green, label: '순자산'),
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
  final List<NetWorthHistoryPoint> series;
  final DateFormat monthLabel;

  static const double _yScale = 10000; // 만원

  @override
  Widget build(BuildContext context) {
    final assetsSpots = <FlSpot>[];
    final liabilitiesSpots = <FlSpot>[];
    final netSpots = <FlSpot>[];
    for (var i = 0; i < series.length; i++) {
      final p = series[i];
      assetsSpots.add(FlSpot(i.toDouble(), p.assetsTotal / _yScale));
      liabilitiesSpots.add(FlSpot(i.toDouble(), p.liabilitiesTotal / _yScale));
      netSpots.add(FlSpot(i.toDouble(), p.netWorth / _yScale));
    }

    final allValues = [
      ...assetsSpots.map((s) => s.y),
      ...liabilitiesSpots.map((s) => s.y),
      ...netSpots.map((s) => s.y),
    ];
    final maxY = allValues.fold<double>(0, (m, v) => v > m ? v : m);
    final minY = allValues.fold<double>(0, (m, v) => v < m ? v : m);
    final padding = (maxY - minY) * 0.1;
    final chartMin = (minY - padding).floorToDouble();
    final chartMax = (maxY + padding).ceilToDouble();

    // 12개월이면 라벨이 빽빽해지므로 2개월 간격으로 표시.
    final interval = series.length > 6 ? 2 : 1;

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
                if (idx % interval != 0) return const SizedBox.shrink();
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
          _line(assetsSpots, Colors.blue),
          _line(liabilitiesSpots, Colors.red),
          _line(netSpots, Colors.green),
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
