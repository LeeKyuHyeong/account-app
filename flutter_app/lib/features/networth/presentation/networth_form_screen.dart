import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:reactive_forms/reactive_forms.dart';

import '../data/networth_api.dart';
import '../models/networth.dart';
import 'networth_screen.dart' show NetWorthFormArgs;

/// 자산 / 부채 추가 / 편집 폼.
///
/// 동일 화면이 두 종류를 다룬다 — [NetWorthFormArgs.kind] 로 분기. 기존 id 가 있으면 PATCH,
/// 없으면 POST. 성공 시 [GoRouter.pop] 으로 호출자에게 `true` 반환 → 목록 invalidate.
class NetWorthFormScreen extends ConsumerStatefulWidget {
  const NetWorthFormScreen({super.key, required this.args});

  final NetWorthFormArgs args;

  @override
  ConsumerState<NetWorthFormScreen> createState() => _NetWorthFormScreenState();
}

class _NetWorthFormScreenState extends ConsumerState<NetWorthFormScreen> {
  late final FormGroup _form;
  bool _submitting = false;
  String? _serverError;

  bool get _isEdit => widget.args.existingId != null;
  bool get _isAsset => widget.args.kind == NetWorthKind.asset;

  @override
  void initState() {
    super.initState();
    _form = FormGroup({
      'name': FormControl<String>(
        value: widget.args.initialName,
        validators: [Validators.required, Validators.maxLength(100)],
      ),
      'type': FormControl<String>(
        value: widget.args.initialType,
        validators: [Validators.required, Validators.maxLength(50)],
      ),
      'balance': FormControl<String>(
        value: widget.args.initialBalance?.toStringAsFixed(0),
        validators: [Validators.required, Validators.delegate(_balanceValidator)],
      ),
      'yearMonth': FormControl<String>(
        value: widget.args.initialYearMonth ?? _currentYearMonth(),
        validators: [Validators.required],
      ),
    });
  }

  static Map<String, Object>? _balanceValidator(AbstractControl<dynamic> c) {
    final raw = (c.value as String? ?? '').replaceAll(',', '').trim();
    if (raw.isEmpty) return null;
    final value = double.tryParse(raw);
    if (value == null) return {'balance': '숫자만 입력해주세요'};
    if (value < 0) return {'balance': '0 이상의 금액을 입력해주세요'};
    return null;
  }

  Future<void> _pickMonth() async {
    final control = _form.control('yearMonth') as FormControl<String>;
    final current = _parseYearMonth(control.value ?? _currentYearMonth());
    final picked = await showDatePicker(
      context: context,
      initialDate: current,
      firstDate: DateTime(2020),
      lastDate: DateTime(DateTime.now().year + 5, 12),
      initialDatePickerMode: DatePickerMode.year,
      helpText: '기록 월 선택',
    );
    if (picked == null) return;
    control.value =
        '${picked.year.toString().padLeft(4, '0')}-${picked.month.toString().padLeft(2, '0')}';
  }

  Future<void> _submit() async {
    _form.markAllAsTouched();
    if (!_form.valid) return;

    setState(() {
      _submitting = true;
      _serverError = null;
    });

    final name = _form.control('name').value as String;
    final type = _form.control('type').value as String;
    final balanceRaw =
        (_form.control('balance').value as String).replaceAll(',', '').trim();
    final balance = double.parse(balanceRaw);
    final yearMonth = _form.control('yearMonth').value as String;

    try {
      final api = ref.read(netWorthApiProvider);
      if (_isEdit) {
        if (_isAsset) {
          await api.updateAsset(
            id: widget.args.existingId!,
            name: name,
            type: type,
            balance: balance,
            yearMonth: yearMonth,
          );
        } else {
          await api.updateLiability(
            id: widget.args.existingId!,
            name: name,
            type: type,
            balance: balance,
            yearMonth: yearMonth,
          );
        }
      } else {
        if (_isAsset) {
          await api.createAsset(
              name: name, type: type, balance: balance, yearMonth: yearMonth);
        } else {
          await api.createLiability(
              name: name, type: type, balance: balance, yearMonth: yearMonth);
        }
      }
      if (!mounted) return;
      context.pop(true);
    } on DioException catch (e) {
      setState(() => _serverError = _formatError(e));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  Future<void> _delete() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('${_isAsset ? '자산' : '부채'} 삭제'),
        content: const Text('이 항목을 삭제할까요?'),
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
    if (ok != true) return;
    setState(() => _submitting = true);
    try {
      final api = ref.read(netWorthApiProvider);
      if (_isAsset) {
        await api.deleteAsset(widget.args.existingId!);
      } else {
        await api.deleteLiability(widget.args.existingId!);
      }
      if (!mounted) return;
      context.pop(true);
    } on DioException catch (e) {
      setState(() => _serverError = _formatError(e));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  String _formatError(DioException e) {
    final data = e.response?.data;
    if (data is Map<String, dynamic>) {
      final message = data['message'] as String?;
      final fields = data['fieldErrors'] as Map<String, dynamic>?;
      if (fields != null && fields.isNotEmpty) {
        return fields.entries.map((kv) => '${kv.key}: ${kv.value}').join(', ');
      }
      if (message != null) return message;
    }
    return e.message ?? '저장에 실패했습니다';
  }

  @override
  Widget build(BuildContext context) {
    final kindLabel = _isAsset ? '자산' : '부채';
    final title = _isEdit ? '$kindLabel 편집' : '$kindLabel 추가';
    return Scaffold(
      appBar: AppBar(
        title: Text(title),
        actions: [
          if (_isEdit)
            IconButton(
              tooltip: '삭제',
              icon: const Icon(Icons.delete_outline),
              onPressed: _submitting ? null : _delete,
            ),
        ],
      ),
      body: SafeArea(
        child: ReactiveForm(
          formGroup: _form,
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                ReactiveTextField<String>(
                  formControlName: 'name',
                  decoration: InputDecoration(
                    labelText: '이름',
                    hintText: _isAsset ? '예: 신한 적금' : '예: 전세 대출',
                  ),
                  validationMessages: {
                    'required': (_) => '이름을 입력해주세요',
                    'maxLength': (_) => '최대 100자',
                  },
                ),
                const SizedBox(height: 16),
                ReactiveTextField<String>(
                  formControlName: 'type',
                  decoration: InputDecoration(
                    labelText: '종류',
                    hintText: _isAsset ? '예: 예금, 주식, 현금' : '예: 대출, 신용카드',
                  ),
                  validationMessages: {
                    'required': (_) => '종류를 입력해주세요',
                    'maxLength': (_) => '최대 50자',
                  },
                ),
                const SizedBox(height: 16),
                ReactiveTextField<String>(
                  formControlName: 'balance',
                  keyboardType:
                      const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(
                    labelText: '잔액 (원)',
                    hintText: '예: 1500000',
                  ),
                  validationMessages: {
                    'required': (_) => '잔액을 입력해주세요',
                    'balance': (e) => e.toString(),
                  },
                ),
                const SizedBox(height: 16),
                _YearMonthField(form: _form, onTap: _pickMonth),
                if (_serverError != null) ...[
                  const SizedBox(height: 16),
                  Text(
                    _serverError!,
                    style:
                        TextStyle(color: Theme.of(context).colorScheme.error),
                  ),
                ],
                const SizedBox(height: 24),
                FilledButton(
                  onPressed: _submitting ? null : _submit,
                  child: _submitting
                      ? const SizedBox(
                          height: 20,
                          width: 20,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Text('저장'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _YearMonthField extends StatelessWidget {
  const _YearMonthField({required this.form, required this.onTap});
  final FormGroup form;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return ReactiveValueListenableBuilder<String>(
      formControlName: 'yearMonth',
      builder: (context, control, _) {
        return InkWell(
          onTap: onTap,
          child: InputDecorator(
            decoration: const InputDecoration(
              labelText: '기록 월',
              helperText: '월별 스냅샷 — 매월 같은 자산/부채의 잔액을 새로 기록하면 추이 차트에 반영.',
            ),
            child: Text(control.value ?? ''),
          ),
        );
      },
    );
  }
}

DateTime _parseYearMonth(String s) {
  final parts = s.split('-');
  return DateTime(int.parse(parts[0]), int.parse(parts[1]));
}

String _currentYearMonth() {
  final now = DateTime.now();
  return '${now.year.toString().padLeft(4, '0')}-${now.month.toString().padLeft(2, '0')}';
}
