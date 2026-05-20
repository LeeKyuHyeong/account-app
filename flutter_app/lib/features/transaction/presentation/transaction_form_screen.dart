import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:reactive_forms/reactive_forms.dart';

import '../data/transaction_api.dart';
import '../models/transaction.dart';
import '../providers/transaction_providers.dart';

/// 수동 거래 입력 폼.
///
/// reactive_forms 의 [FormGroup] 으로 검증 / 상태 / 제출을 일원화. 성공 시
/// [GoRouter.pop] 으로 호출자에게 `true` 를 돌려주면 목록 화면이 refresh 한다.
class TransactionFormScreen extends ConsumerStatefulWidget {
  const TransactionFormScreen({super.key});

  @override
  ConsumerState<TransactionFormScreen> createState() =>
      _TransactionFormScreenState();
}

class _TransactionFormScreenState extends ConsumerState<TransactionFormScreen> {
  static final _dateLabel = DateFormat('yyyy. M. d. HH:mm', 'ko_KR');

  late final FormGroup _form;
  bool _submitting = false;
  String? _serverError;

  @override
  void initState() {
    super.initState();
    _form = FormGroup({
      'amount': FormControl<String>(
        validators: [Validators.required, Validators.delegate(_amountValidator)],
      ),
      'categoryId': FormControl<int>(validators: [Validators.required]),
      'occurredAt': FormControl<DateTime>(
        value: DateTime.now(),
        validators: [Validators.required],
      ),
      'merchant': FormControl<String>(),
      'paymentMethod': FormControl<String>(),
      'memo': FormControl<String>(),
    });
  }

  static Map<String, Object>? _amountValidator(AbstractControl<dynamic> c) {
    final raw = (c.value as String? ?? '').replaceAll(',', '').trim();
    if (raw.isEmpty) return null;
    final value = double.tryParse(raw);
    if (value == null) return {'amount': '숫자만 입력해주세요'};
    if (value <= 0) return {'amount': '0보다 큰 금액을 입력해주세요'};
    return null;
  }

  Future<void> _pickDate() async {
    final control = _form.control('occurredAt') as FormControl<DateTime>;
    final initial = control.value ?? DateTime.now();
    final date = await showDatePicker(
      context: context,
      initialDate: initial,
      firstDate: DateTime(2020),
      lastDate: DateTime(2100),
    );
    if (date == null || !mounted) return;
    final time = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(initial),
    );
    final picked = DateTime(
      date.year,
      date.month,
      date.day,
      time?.hour ?? initial.hour,
      time?.minute ?? initial.minute,
    );
    control.value = picked;
  }

  Future<void> _submit() async {
    _form.markAllAsTouched();
    if (!_form.valid) return;

    setState(() {
      _submitting = true;
      _serverError = null;
    });

    final raw = (_form.control('amount').value as String).replaceAll(',', '').trim();
    final amount = double.parse(raw);
    try {
      await ref.read(transactionApiProvider).create(
            categoryId: _form.control('categoryId').value as int,
            amount: amount,
            occurredAt: _form.control('occurredAt').value as DateTime,
            merchant: _form.control('merchant').value as String?,
            paymentMethod: _form.control('paymentMethod').value as String?,
            memo: _form.control('memo').value as String?,
          );
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
    final categoriesAsync = ref.watch(expenseCategoriesProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('거래 추가')),
      body: SafeArea(
        child: ReactiveForm(
          formGroup: _form,
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                ReactiveTextField<String>(
                  formControlName: 'amount',
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(
                    labelText: '금액 (원)',
                    hintText: '예: 12000',
                  ),
                  validationMessages: {
                    'required': (_) => '금액을 입력해주세요',
                    'amount': (e) => e.toString(),
                  },
                ),
                const SizedBox(height: 16),
                categoriesAsync.when(
                  loading: () => const LinearProgressIndicator(),
                  error: (e, _) => Text('카테고리 로드 실패: $e'),
                  data: (categories) => ReactiveDropdownField<int>(
                    formControlName: 'categoryId',
                    decoration: const InputDecoration(labelText: '카테고리'),
                    items: categories.map(_buildCategoryItem).toList(),
                    validationMessages: {
                      'required': (_) => '카테고리를 선택해주세요',
                    },
                  ),
                ),
                const SizedBox(height: 16),
                _OccurredAtField(form: _form, dateLabel: _dateLabel, onTap: _pickDate),
                const SizedBox(height: 16),
                ReactiveTextField<String>(
                  formControlName: 'merchant',
                  decoration: const InputDecoration(labelText: '가맹점 (선택)'),
                ),
                const SizedBox(height: 16),
                ReactiveTextField<String>(
                  formControlName: 'paymentMethod',
                  decoration: const InputDecoration(
                    labelText: '결제수단 (선택)',
                    hintText: '예: 신용카드, 체크카드, 현금',
                  ),
                ),
                const SizedBox(height: 16),
                ReactiveTextField<String>(
                  formControlName: 'memo',
                  decoration: const InputDecoration(labelText: '메모 (선택)'),
                  maxLines: 2,
                ),
                if (_serverError != null) ...[
                  const SizedBox(height: 16),
                  Text(
                    _serverError!,
                    style: TextStyle(color: Theme.of(context).colorScheme.error),
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

  DropdownMenuItem<int> _buildCategoryItem(Category c) {
    return DropdownMenuItem<int>(
      value: c.id,
      child: Text('${c.name} · ${c.type}'),
    );
  }
}

class _OccurredAtField extends StatelessWidget {
  const _OccurredAtField({
    required this.form,
    required this.dateLabel,
    required this.onTap,
  });
  final FormGroup form;
  final DateFormat dateLabel;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return ReactiveValueListenableBuilder<DateTime>(
      formControlName: 'occurredAt',
      builder: (context, control, _) {
        final value = control.value ?? DateTime.now();
        return InkWell(
          onTap: onTap,
          child: InputDecorator(
            decoration: const InputDecoration(labelText: '일시'),
            child: Text(dateLabel.format(value)),
          ),
        );
      },
    );
  }
}
