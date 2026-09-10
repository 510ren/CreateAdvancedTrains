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

## Phase 6の統括責務

Phase 6以降の`AtoController`は、CAT通常走行の統括担当とする。これはBrakingCurve、
TargetSpeedResolver、NotchSelector、NotchControllerの計算式を一つの巨大クラスへ統合する意味ではない。
各専用クラスが計算した結果を、列車単位の状態優先に従って受け取り、次の担当へ渡し、
Create境界へ反映する責務である。

```text
Create native target / Create上限 / BrakingCurve
                  ↓
        TargetSpeedResolver
                  ↓
 AtoController（状態優先・統括・受渡し）
                  ↓
 NotchSelector → NotchController
                  ↓
 Createの既存approachTargetSpeed()一回
```

## 設計原則

- `createTargetSpeed`と`atoTargetSpeed`は常に区別する。
- Createの `targetSpeed == 0` を、CATの停止理由や最終目標速度として無条件に解釈しない。
- ATOはBrakingCurve、TASC、Signal、ATC、ノッチ応答の数式を一クラスへ抱え込まない。
- 通常の速度制約統合はTargetSpeedResolverが行う。ATOは状態優先と制御フローを統括する。

Phase 5でBrakingCurveは純粋計算として実装済みである。Phase 6でTargetSpeedResolverがその最大許容速度を
制約として受け取り、ATOは統合済みの最終CAT目標とノッチ結果をCreate境界へ渡す。
