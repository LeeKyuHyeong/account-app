import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../auth/providers/auth_provider.dart';
import '../../summary/presentation/this_month_card.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(authProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('가계부'),
        actions: [
          IconButton(
            tooltip: '로그아웃',
            icon: const Icon(Icons.logout),
            onPressed: () => ref.read(authProvider.notifier).logout(),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.large(
        onPressed: () => context.push('/receipts/new'),
        tooltip: '영수증 촬영',
        child: const Icon(Icons.camera_alt),
      ),
      body: state.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('오류: $e')),
        data: (auth) {
          if (auth is! Authenticated) {
            return const Center(child: Text('로그인이 필요합니다'));
          }
          final me = auth.me;
          final household = me.households.isNotEmpty ? me.households.first : null;
          return ListView(
            padding: const EdgeInsets.only(top: 8, bottom: 96),
            children: [
              const ThisMonthCard(),
              Padding(
                padding: const EdgeInsets.fromLTRB(24, 16, 24, 8),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text('${me.name} 님',
                        style: Theme.of(context).textTheme.titleMedium,
                        textAlign: TextAlign.center),
                    if (household != null) ...[
                      const SizedBox(height: 4),
                      Text('가구 ${household.name} · ${household.role}',
                          textAlign: TextAlign.center,
                          style: Theme.of(context).textTheme.bodySmall),
                    ],
                    const SizedBox(height: 24),
                    FilledButton.icon(
                      onPressed: () => context.push('/transactions'),
                      icon: const Icon(Icons.list_alt),
                      label: const Text('거래 목록'),
                    ),
                    const SizedBox(height: 12),
                    OutlinedButton.icon(
                      onPressed: () => context.push('/transactions/new'),
                      icon: const Icon(Icons.add),
                      label: const Text('거래 추가'),
                    ),
                  ],
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}
