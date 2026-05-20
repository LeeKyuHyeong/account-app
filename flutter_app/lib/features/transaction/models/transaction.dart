/// 거래 응답 모델 — 백엔드 [TransactionDtos.TransactionResponse] 와 1:1.
class TransactionItem {
  const TransactionItem({
    required this.id,
    required this.categoryId,
    required this.categoryName,
    required this.categoryType,
    required this.amount,
    required this.occurredAt,
    required this.status,
    this.merchant,
    this.paymentMethod,
    this.memo,
    this.receiptId,
    this.confidence,
  });

  factory TransactionItem.fromJson(Map<String, dynamic> json) {
    return TransactionItem(
      id: (json['id'] as num).toInt(),
      categoryId: (json['categoryId'] as num).toInt(),
      categoryName: json['categoryName'] as String,
      categoryType: json['categoryType'] as String,
      amount: (json['amount'] as num).toDouble(),
      occurredAt: DateTime.parse(json['occurredAt'] as String),
      status: json['status'] as String,
      merchant: json['merchant'] as String?,
      paymentMethod: json['paymentMethod'] as String?,
      memo: json['memo'] as String?,
      receiptId: (json['receiptId'] as num?)?.toInt(),
      confidence: (json['confidence'] as num?)?.toDouble(),
    );
  }

  final int id;
  final int categoryId;
  final String categoryName;
  final String categoryType;
  final double amount;
  final DateTime occurredAt;
  final String status;
  final String? merchant;
  final String? paymentMethod;
  final String? memo;
  final int? receiptId;
  final double? confidence;

  bool get isIncome => categoryType == 'INCOME';
  bool get isDraft => status == 'DRAFT';
}

/// 페이지네이션 응답 — 백엔드 [TransactionDtos.PageResponse] 와 1:1.
class PageResponse<T> {
  const PageResponse({
    required this.content,
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
    required this.hasNext,
  });

  factory PageResponse.fromJson(
    Map<String, dynamic> json,
    T Function(Map<String, dynamic>) parseItem,
  ) {
    return PageResponse(
      content: (json['content'] as List<dynamic>)
          .cast<Map<String, dynamic>>()
          .map(parseItem)
          .toList(),
      page: (json['page'] as num).toInt(),
      size: (json['size'] as num).toInt(),
      totalElements: (json['totalElements'] as num).toInt(),
      totalPages: (json['totalPages'] as num).toInt(),
      hasNext: json['hasNext'] as bool,
    );
  }

  final List<T> content;
  final int page;
  final int size;
  final int totalElements;
  final int totalPages;
  final bool hasNext;
}

/// 카테고리 (`/api/categories` 응답 항목).
class Category {
  const Category({
    required this.id,
    required this.name,
    required this.type,
    required this.budgetMonthly,
  });

  factory Category.fromJson(Map<String, dynamic> json) {
    return Category(
      id: (json['id'] as num).toInt(),
      name: json['name'] as String,
      type: json['type'] as String,
      budgetMonthly: (json['budgetMonthly'] as num).toDouble(),
    );
  }

  final int id;
  final String name;
  final String type;
  final double budgetMonthly;
}
