import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../features/auth/presentation/login_screen.dart';
import '../features/auth/providers/auth_provider.dart';
import '../features/home/presentation/home_screen.dart';
import '../features/receipt/models/receipt_response.dart';
import '../features/receipt/presentation/receipt_capture_screen.dart';
import '../features/receipt/presentation/receipt_confirmation_screen.dart';
import '../features/summary/presentation/trend_screen.dart';
import '../features/transaction/presentation/transaction_form_screen.dart';
import '../features/transaction/presentation/transaction_list_screen.dart';

/// 인증 상태 변화 시 go_router 를 refresh 시키기 위한 어댑터.
///
/// go_router 의 [GoRouter.refreshListenable] 은 [Listenable] 을 받는데, Riverpod
/// 의 [AsyncValue] 는 Listenable 이 아니다. 본 어댑터로 ref.listen → notifyListeners
/// 로 변환하면 로그인 / 로그아웃 즉시 라우터가 redirect 를 재계산한다.
class _RouterNotifier extends ChangeNotifier {
  _RouterNotifier(Ref ref) {
    ref.listen<AsyncValue<AuthState>>(authProvider, (_, _) {
      notifyListeners();
    });
  }
}

final appRouterProvider = Provider<GoRouter>((ref) {
  final notifier = _RouterNotifier(ref);
  return GoRouter(
    initialLocation: '/home',
    refreshListenable: notifier,
    redirect: (context, state) {
      final auth = ref.read(authProvider);
      if (auth.isLoading || !auth.hasValue) {
        return null;
      }
      final isAuth = auth.value is Authenticated;
      final loggingIn = state.matchedLocation == '/login';
      if (!isAuth && !loggingIn) return '/login';
      if (isAuth && loggingIn) return '/home';
      return null;
    },
    routes: [
      GoRoute(
        path: '/login',
        builder: (context, state) => const LoginScreen(),
      ),
      GoRoute(
        path: '/home',
        builder: (context, state) => const HomeScreen(),
      ),
      GoRoute(
        path: '/transactions',
        builder: (context, state) => const TransactionListScreen(),
        routes: [
          GoRoute(
            path: 'new',
            builder: (context, state) => const TransactionFormScreen(),
          ),
        ],
      ),
      GoRoute(
        path: '/receipts/new',
        builder: (context, state) => const ReceiptCaptureScreen(),
      ),
      GoRoute(
        path: '/receipts/confirm',
        builder: (context, state) {
          final upload = state.extra as ReceiptUploadResponse?;
          if (upload == null) {
            // extra 가 없으면 캡처로 되돌린다 (딥링크 직접 접근 방지).
            return const ReceiptCaptureScreen();
          }
          return ReceiptConfirmationScreen(upload: upload);
        },
      ),
      GoRoute(
        path: '/summary/trend',
        builder: (context, state) => const TrendScreen(),
      ),
    ],
  );
});
