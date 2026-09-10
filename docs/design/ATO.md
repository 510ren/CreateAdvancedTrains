# ATO

## 現在の役割

現行の `AtoController` は、Createが要求した `createTargetSpeed` にSpeed Limitを適用し、
CAT側の `atoTargetSpeed` を返す。ATOが無効、またはSpeed Limitが無効なら、対象の値を
そのまま返す。

```text
createTargetSpeed
      ↓
SpeedLimitController
      ↓
atoTargetSpeed
      ↓
Train.targetSpeed
```

## 設計原則

- `createTargetSpeed`と`atoTargetSpeed`は常に区別する。
- Createの `targetSpeed == 0` を、CATの停止理由や最終目標速度として無条件に解釈しない。
- ATOは将来のBrakingCurve、TASC、Signal、ATCなどを一クラスへ抱え込まない。
- 将来の統合はTargetSpeedResolverが行い、ATOはその結果をCreate境界へ渡す役割へ縮小する。

Phase 5でBrakingCurveは純粋計算として実装済みである。Phase 6でTargetSpeedResolverがその最大許容速度を
制約として受け取り、ATOは統合済みの最終CAT目標をCreate境界へ渡す役割へ移行する。
