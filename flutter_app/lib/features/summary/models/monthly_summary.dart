/// 월별 합계 응답 (백엔드 [MonthlySummaryDtos.MonthlySummaryResponse] 와 1:1).
class MonthlySummary {
  const MonthlySummary({
    required this.yearMonth,
    required this.income,
    required this.expenseFixed,
    required this.expenseVariable,
    required this.invest,
    required this.totalExpense,
    required this.surplus,
    required this.byCategory,
  });

  factory MonthlySummary.fromJson(Map<String, dynamic> json) {
    return MonthlySummary(
      yearMonth: json['yearMonth'] as String,
      income: (json['income'] as num).toDouble(),
      expenseFixed: (json['expenseFixed'] as num).toDouble(),
      expenseVariable: (json['expenseVariable'] as num).toDouble(),
      invest: (json['invest'] as num).toDouble(),
      totalExpense: (json['totalExpense'] as num).toDouble(),
      surplus: (json['surplus'] as num).toDouble(),
      byCategory: (json['byCategory'] as List<dynamic>)
          .cast<Map<String, dynamic>>()
          .map(CategoryAmount.fromJson)
          .toList(),
    );
  }

  final String yearMonth;
  final double income;
  final double expenseFixed;
  final double expenseVariable;
  final double invest;
  final double totalExpense;
  final double surplus;
  final List<CategoryAmount> byCategory;
}

class CategoryAmount {
  const CategoryAmount({
    required this.categoryId,
    required this.name,
    required this.type,
    required this.total,
    required this.budgetMonthly,
    required this.sortOrder,
  });

  factory CategoryAmount.fromJson(Map<String, dynamic> json) {
    return CategoryAmount(
      categoryId: (json['categoryId'] as num).toInt(),
      name: json['name'] as String,
      type: json['type'] as String,
      total: (json['total'] as num).toDouble(),
      budgetMonthly: (json['budgetMonthly'] as num).toDouble(),
      sortOrder: (json['sortOrder'] as num).toInt(),
    );
  }

  final int categoryId;
  final String name;
  final String type;
  final double total;
  final double budgetMonthly;
  final int sortOrder;
}
