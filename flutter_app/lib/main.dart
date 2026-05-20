import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'app.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // intl 의 한국어 locale 데이터 로드 — DateFormat('...', 'ko_KR') 사용 전 필수.
  await initializeDateFormatting('ko_KR');
  runApp(const ProviderScope(child: AccountApp()));
}
