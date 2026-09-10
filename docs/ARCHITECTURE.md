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

## Create列車情報の照会境界

Create内部値の読み取り・Createと同じ探索規則による導出は、将来の各ControllerやHUDへ分散させない。
Server側の単一ユーティリティ（名称候補: `CreateTrainQueryUtil`）を設け、Create列車に関する読み取り専用の
問い合わせをここへ集約する。

最初に必要な問い合わせは次のとおりである。

| 問い合わせ | 要件 |
| --- | --- |
| 次の停車対象までの距離 | `Navigation.destination`と有効な`distanceToDestination`がある場合は、その符号付きblocks値を正本として返す。 |
| 手動運転時の次の停車対象までの距離 | Navigation値が利用不能な場合に限り、Create 6.0.8の手動運転で使う次の到達可能GlobalStation探索と同じ入力・進行方向規則で、線路上の距離を導出する。Navigation状態を開始・変更してはならない。 |
| 停車に向けた減速か | Createが出したnative target、現在速度、進行方向、停車対象の有無を一箇所で判定する。個々のControllerが独自に比較式を持ってはならない。 |

距離が求められないことは有効な状態であり、数値0を代用してはならない。問い合わせ結果は距離・単位・
取得元（Navigation / 手動探索 / unavailable）を区別できる値とし、HUDはunavailableを`0`ではなく
`--`などの未取得表示にする。将来のBrakingCurve、TASC、信号、HUDはこの照会境界を利用する。

Create 6.0.8の手動探索API・進行方向・距離の符号・副作用は、実装前に実ソースで確認する。安全な
読み取りだけで手動探索を再現できない場合、Solは状態変更を伴う代替案を実装せず、根拠と選択肢を報告する。

現在の単一停車対象距離が手動運転時に未取得となる問題は、ユーザー承認によりPhase 5では非阻害とする。
Phase 9ではこの問い合わせを拡張または別の読み取り専用問い合わせとして設計し、到達可能な近傍駅を
おおむね4駅まで独立HUDへ提供する。単一destinationの欠損を数値0で埋めてはならない。

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

Phase 9のCAT独立運転では、Clientの設定可能なCAT専用前進/後進キーが操作意図だけをServerへ渡す。
Serverは列車単位の操作権限と安全制約を確認して`TrainController`へ反映する。CAT独立操作が有効な
当該列車の入力経路では、従来の`W`/`S`前後進操作を無効化し、二重の運転指令を許可しない。これは
CAT以外を含むゲーム全体のキーを無効化する要件ではない。詳細は`docs/design/DRIVING_MODE.md`を正本とする。
