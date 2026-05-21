// 순자산 모델 (백엔드 NetWorthDtos 와 1:1).

class AssetItem {
  const AssetItem({
    required this.id,
    required this.name,
    required this.type,
    required this.balance,
    required this.recordedAt,
  });

  factory AssetItem.fromJson(Map<String, dynamic> json) {
    return AssetItem(
      id: (json['id'] as num).toInt(),
      name: json['name'] as String,
      type: json['type'] as String,
      balance: (json['balance'] as num).toDouble(),
      recordedAt: DateTime.parse(json['recordedAt'] as String),
    );
  }

  final int id;
  final String name;
  final String type;
  final double balance;
  final DateTime recordedAt;
}

class LiabilityItem {
  const LiabilityItem({
    required this.id,
    required this.name,
    required this.type,
    required this.balance,
    required this.recordedAt,
  });

  factory LiabilityItem.fromJson(Map<String, dynamic> json) {
    return LiabilityItem(
      id: (json['id'] as num).toInt(),
      name: json['name'] as String,
      type: json['type'] as String,
      balance: (json['balance'] as num).toDouble(),
      recordedAt: DateTime.parse(json['recordedAt'] as String),
    );
  }

  final int id;
  final String name;
  final String type;
  final double balance;
  final DateTime recordedAt;
}

class NetWorthSnapshot {
  const NetWorthSnapshot({
    required this.yearMonth,
    required this.assetsTotal,
    required this.liabilitiesTotal,
    required this.netWorth,
    required this.assets,
    required this.liabilities,
  });

  factory NetWorthSnapshot.fromJson(Map<String, dynamic> json) {
    return NetWorthSnapshot(
      yearMonth: json['yearMonth'] as String,
      assetsTotal: (json['assetsTotal'] as num).toDouble(),
      liabilitiesTotal: (json['liabilitiesTotal'] as num).toDouble(),
      netWorth: (json['netWorth'] as num).toDouble(),
      assets: (json['assets'] as List<dynamic>)
          .cast<Map<String, dynamic>>()
          .map(AssetItem.fromJson)
          .toList(),
      liabilities: (json['liabilities'] as List<dynamic>)
          .cast<Map<String, dynamic>>()
          .map(LiabilityItem.fromJson)
          .toList(),
    );
  }

  final String yearMonth;
  final double assetsTotal;
  final double liabilitiesTotal;
  final double netWorth;
  final List<AssetItem> assets;
  final List<LiabilityItem> liabilities;
}

/// 자산 / 부채 구분 — 폼 화면이 두 종류를 한 위젯으로 다루기 위한 열거형.
enum NetWorthKind { asset, liability }

/// 차트용 — 한 달치 합계만.
class NetWorthHistoryPoint {
  const NetWorthHistoryPoint({
    required this.yearMonth,
    required this.assetsTotal,
    required this.liabilitiesTotal,
    required this.netWorth,
  });

  factory NetWorthHistoryPoint.fromJson(Map<String, dynamic> json) {
    return NetWorthHistoryPoint(
      yearMonth: json['yearMonth'] as String,
      assetsTotal: (json['assetsTotal'] as num).toDouble(),
      liabilitiesTotal: (json['liabilitiesTotal'] as num).toDouble(),
      netWorth: (json['netWorth'] as num).toDouble(),
    );
  }

  final String yearMonth;
  final double assetsTotal;
  final double liabilitiesTotal;
  final double netWorth;
}
