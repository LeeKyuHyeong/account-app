import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../data/networth_api.dart';
import '../models/networth.dart';

/// 순자산 화면 — 선택된 월의 자산/부채 + 합계 + 순자산 표시.
///
/// 월 picker 로 과거/미래 월 조회. 자산/부채 추가 / 수정 / 삭제 가능. 차트 (월별 추이) 는
/// 별도 PR.
class NetWorthScreen extends ConsumerWidget {
  const NetWorthScreen({super.key});

  static final _currency = NumberFormat.currency(
    locale: 'ko_KR',
    symbol: '₩',
    decimalDigits: 0,
  );
  static final _monthFmt = DateFormat.yMMM('ko_KR');

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(netWorthSnapshotProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('순자산'),
        actions: [
          IconButton(
            tooltip: '월 변경',
            icon: const Icon(Icons.calendar_month),
            onPressed: () => _pickMonth(context, ref, async),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(netWorthSnapshotProvider),
        child: async.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => ListView(
            children: [
              const SizedBox(height: 80),
              Center(child: Text('로드 실패: $e')),
              const SizedBox(height: 16),
              Center(
                child: TextButton(
                  onPressed: () => ref.invalidate(netWorthSnapshotProvider),
                  child: const Text('다시 시도'),
                ),
              ),
            ],
          ),
          data: (snapshot) => _Content(snapshot: snapshot),
        ),
      ),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
          child: Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () => _addNew(context, ref, NetWorthKind.asset),
                  icon: const Icon(Icons.add),
                  label: const Text('자산 추가'),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () => _addNew(context, ref, NetWorthKind.liability),
                  icon: const Icon(Icons.add),
                  label: const Text('부채 추가'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _addNew(
      BuildContext context, WidgetRef ref, NetWorthKind kind) async {
    final selectedYm = ref.read(selectedYearMonthProvider);
    final changed = await context.push<bool>(
      '/networth/form',
      extra: NetWorthFormArgs(
        kind: kind,
        existingId: null,
        initialYearMonth: selectedYm,
      ),
    );
    if (changed == true) ref.invalidate(netWorthSnapshotProvider);
  }

  Future<void> _pickMonth(
      BuildContext context,
      WidgetRef ref,
      AsyncValue<NetWorthSnapshot> async) async {
    // showDatePicker 의 월 단위 선택은 initialDatePickerMode + 1일 보정으로 처리.
    final current = async.value;
    DateTime initial;
    if (current != null) {
      initial = _parseYearMonth(current.yearMonth);
    } else {
      final now = DateTime.now();
      initial = DateTime(now.year, now.month);
    }
    final picked = await showDatePicker(
      context: context,
      initialDate: initial,
      firstDate: DateTime(2020),
      lastDate: DateTime(DateTime.now().year + 5, 12),
      initialDatePickerMode: DatePickerMode.year,
      helpText: '월 선택',
    );
    if (picked == null) return;
    final ym = _formatYearMonth(DateTime(picked.year, picked.month));
    ref.read(selectedYearMonthProvider.notifier).state = ym;
  }

  static DateTime _parseYearMonth(String s) {
    final parts = s.split('-');
    return DateTime(int.parse(parts[0]), int.parse(parts[1]));
  }

  static String _formatYearMonth(DateTime d) {
    return '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}';
  }
}

class _Content extends ConsumerWidget {
  const _Content({required this.snapshot});

  final NetWorthSnapshot snapshot;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 16),
      children: [
        _TotalsCard(snapshot: snapshot),
        const SizedBox(height: 24),
        Text('자산 (${snapshot.assets.length})',
            style: theme.textTheme.titleMedium),
        const SizedBox(height: 8),
        if (snapshot.assets.isEmpty)
          const _EmptyHint(label: '아직 자산이 없습니다. 아래 "자산 추가"로 시작하세요.')
        else
          ...snapshot.assets.map(
            (a) => _AssetTile(
              key: ValueKey('asset-${a.id}'),
              item: a,
            ),
          ),
        const SizedBox(height: 24),
        Text('부채 (${snapshot.liabilities.length})',
            style: theme.textTheme.titleMedium),
        const SizedBox(height: 8),
        if (snapshot.liabilities.isEmpty)
          const _EmptyHint(label: '부채 없음.')
        else
          ...snapshot.liabilities.map(
            (l) => _LiabilityTile(
              key: ValueKey('liability-${l.id}'),
              item: l,
            ),
          ),
        const SizedBox(height: 96),
      ],
    );
  }
}

class _TotalsCard extends StatelessWidget {
  const _TotalsCard({required this.snapshot});
  final NetWorthSnapshot snapshot;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final monthLabel = NetWorthScreen._monthFmt.format(
        NetWorthScreen._parseYearMonth(snapshot.yearMonth));
    final netColor = snapshot.netWorth >= 0
        ? Colors.green.shade700
        : theme.colorScheme.error;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(monthLabel, style: theme.textTheme.titleMedium),
            const SizedBox(height: 12),
            _TotalsRow(label: '자산', value: snapshot.assetsTotal, color: Colors.blue.shade700),
            const SizedBox(height: 8),
            _TotalsRow(label: '부채', value: snapshot.liabilitiesTotal, color: theme.colorScheme.error),
            const Divider(height: 24),
            _TotalsRow(
              label: '순자산',
              value: snapshot.netWorth,
              color: netColor,
              emphasize: true,
            ),
          ],
        ),
      ),
    );
  }
}

class _TotalsRow extends StatelessWidget {
  const _TotalsRow({
    required this.label,
    required this.value,
    required this.color,
    this.emphasize = false,
  });

  final String label;
  final double value;
  final Color color;
  final bool emphasize;

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
        Text(NetWorthScreen._currency.format(value), style: valueStyle),
      ],
    );
  }
}

class _AssetTile extends ConsumerWidget {
  const _AssetTile({super.key, required this.item});
  final AssetItem item;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Dismissible(
      key: ValueKey('asset-dismiss-${item.id}'),
      direction: DismissDirection.endToStart,
      background: _dismissBackground(context),
      confirmDismiss: (_) => _confirmDelete(context, '자산', item.name),
      onDismissed: (_) async {
        try {
          await ref.read(netWorthApiProvider).deleteAsset(item.id);
          ref.invalidate(netWorthSnapshotProvider);
        } catch (e) {
          if (context.mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text('삭제 실패: $e')),
            );
            ref.invalidate(netWorthSnapshotProvider);
          }
        }
      },
      child: Card(
        child: ListTile(
          title: Text(item.name),
          subtitle: Text(item.type),
          trailing: Text(
            NetWorthScreen._currency.format(item.balance),
            style: Theme.of(context).textTheme.titleMedium,
          ),
          onTap: () async {
            final changed = await context.push<bool>(
              '/networth/form',
              extra: NetWorthFormArgs(
                kind: NetWorthKind.asset,
                existingId: item.id,
                initialName: item.name,
                initialType: item.type,
                initialBalance: item.balance,
                initialYearMonth: _toYearMonth(item.recordedAt),
              ),
            );
            if (changed == true) ref.invalidate(netWorthSnapshotProvider);
          },
        ),
      ),
    );
  }
}

class _LiabilityTile extends ConsumerWidget {
  const _LiabilityTile({super.key, required this.item});
  final LiabilityItem item;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Dismissible(
      key: ValueKey('liability-dismiss-${item.id}'),
      direction: DismissDirection.endToStart,
      background: _dismissBackground(context),
      confirmDismiss: (_) => _confirmDelete(context, '부채', item.name),
      onDismissed: (_) async {
        try {
          await ref.read(netWorthApiProvider).deleteLiability(item.id);
          ref.invalidate(netWorthSnapshotProvider);
        } catch (e) {
          if (context.mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text('삭제 실패: $e')),
            );
            ref.invalidate(netWorthSnapshotProvider);
          }
        }
      },
      child: Card(
        child: ListTile(
          title: Text(item.name),
          subtitle: Text(item.type),
          trailing: Text(
            NetWorthScreen._currency.format(item.balance),
            style: Theme.of(context)
                .textTheme
                .titleMedium
                ?.copyWith(color: Theme.of(context).colorScheme.error),
          ),
          onTap: () async {
            final changed = await context.push<bool>(
              '/networth/form',
              extra: NetWorthFormArgs(
                kind: NetWorthKind.liability,
                existingId: item.id,
                initialName: item.name,
                initialType: item.type,
                initialBalance: item.balance,
                initialYearMonth: _toYearMonth(item.recordedAt),
              ),
            );
            if (changed == true) ref.invalidate(netWorthSnapshotProvider);
          },
        ),
      ),
    );
  }
}

class _EmptyHint extends StatelessWidget {
  const _EmptyHint({required this.label});
  final String label;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 16),
      child: Center(
        child: Text(label,
            style: Theme.of(context)
                .textTheme
                .bodyMedium
                ?.copyWith(color: Theme.of(context).hintColor)),
      ),
    );
  }
}

Widget _dismissBackground(BuildContext context) {
  return Container(
    alignment: Alignment.centerRight,
    padding: const EdgeInsets.symmetric(horizontal: 24),
    color: Theme.of(context).colorScheme.error,
    child: const Icon(Icons.delete, color: Colors.white),
  );
}

Future<bool> _confirmDelete(BuildContext context, String kind, String name) async {
  final ok = await showDialog<bool>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: Text('$kind 삭제'),
      content: Text('$name 을(를) 삭제할까요?'),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(ctx).pop(false),
          child: const Text('취소'),
        ),
        FilledButton(
          onPressed: () => Navigator.of(ctx).pop(true),
          child: const Text('삭제'),
        ),
      ],
    ),
  );
  return ok ?? false;
}

String _toYearMonth(DateTime d) {
  return '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}';
}

/// 라우트 인자 — 새 자산/부채 추가 또는 기존 항목 편집.
class NetWorthFormArgs {
  const NetWorthFormArgs({
    required this.kind,
    required this.existingId,
    this.initialName,
    this.initialType,
    this.initialBalance,
    this.initialYearMonth,
  });

  final NetWorthKind kind;
  final int? existingId;
  final String? initialName;
  final String? initialType;
  final double? initialBalance;
  final String? initialYearMonth;
}
