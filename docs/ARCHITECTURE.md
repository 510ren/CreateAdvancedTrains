# アーキテクチャ

## 現在の制御経路

```text
Create Schedule / Navigation / Manual control
                ↓
        Train.approachTargetSpeed()
                ↓
       TrainMixin（HEAD inject）
                ↓
          TrainController
                ↓
           AtoController
                ↓
       SpeedLimitController
                ↓
         Train.targetSpeed
                ↓
     Create標準の速度追従
                ↓
            Train.speed
```

`TrainMixin`は `Train.approachTargetSpeed(float accelerationMod)` の先頭へ介入する。
`accelerationMod` は目標速度ではない。Navigationと手動運転側の双方がこのメソッドを
呼ぶため、目標速度への介入点はここへ集約する。

## 列車単位の状態

`TrainControllerManager`は `Map<UUID, TrainController>` を保持する。`TrainController`は
Create本来の目標速度 `createTargetSpeed`、CAT側目標速度 `atoTargetSpeed`、および
`TrainState`を保持する。両目標速度を分離することで、CATの制約内容をDebugで追跡できる。

## 将来の責務分離

将来のControllerは一つの巨大クラスにせず、速度制約を個別に計算し、統合担当だけが
最終目標速度を決める。

```text
Speed Limit / BrakingCurve / TASC / Signal / ATC
                    ↓
         TargetSpeedResolver（将来）
                    ↓
              atoTargetSpeed
```

BrakingCurveは最大許容速度、TASCは停止位置と残距離、NotchControllerは加減速要求の
適用、EmergencyBrakeControllerは非常状態を担当する。これらのPhase 6以降の実装詳細は
各設計が確定するまで未記載とする。

## Server / Client

物理制御、列車状態、制約計算はServer側の責務である。Client側はHUD、GUI、入力、
Debug表示を担当し、物理値の決定・変更を行わない。
