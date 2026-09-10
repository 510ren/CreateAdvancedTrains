# Train Control

## 現在の構成

`TrainControllerManager`がCreateのTrain UUIDごとに`TrainController`を保持する。
`TrainController`はTrain UUID、AtoController、Create本来の目標速度、CAT目標速度、
TrainStateを保持する。

```text
Train UUID → TrainControllerManager → TrainController
```

`TrainState`は現在、speed、createTargetSpeed、atoTargetSpeedを記録する。Debug/HUDは
これらを利用してCreate本来の要求とCATの制約結果を比較できる。

## 共有運転フラグ

Create由来の状態は各Controllerが個別に解釈せず、列車UUID単位の`FlagDeterminer`（名称は実装時に
責務を保ったまま調整可）が一元的に導出する。`TrainController`は毎server tick、ATO・TASC・ATC・
信号の各Controllerより先にこの結果を更新し、各Controllerは読み取り専用で参照する。

最初の共有フラグは`AT_DESTINATION_OR_ARRIVAL_PENDING`である。これは任意に書き換える操作状態ではなく、
`CreateTrainQueryUtil`が正規化したNavigation状態から導出する。

| Navigation状態 | 共有フラグ | ATO/TASC等への意味 |
| --- | --- | --- |
| `AHEAD` | false | 通常の制約計算を行う。 |
| `AT_DESTINATION_OR_ARRIVAL_PENDING` | true | 到着処理中。ATOは新しい加速意図を出さず、将来のTASCは到着優先の内部動作へ切替えられる。 |
| `PAST_DESTINATION_OR_INVALID_STATE` | false | オーバーラン異常を脱線相当として扱う。 |
| `NO_ACTIVE_DESTINATION` | false | Navigation由来の停止制約なし。 |

ATOControllerはこの共有フラグを消費して自動運転の状態を切り替えるが、フラグ自体の真偽を所有・上書きしてはならない。
これにより将来のTASC、ATC、信号処理が同じ到着状態を利用でき、Controller間の解除漏れを防ぐ。

## 不変条件

- 通常制御で`Train.speed`を直接変更しない。
- Controllerの識別子はUUIDであり、列車名・配列位置・アドオン名ではない。
- Controllerは機能別の計算を抱え込まず、各Controller/Resolverへ責務を委譲する。
- CAT Controller Blockによる対象判定を導入するまでは、対象範囲を明示的に検証する。

## 現行実装の留意点

現行のLevelTick Debug処理は全発見列車についてControllerを更新・生成する。これは将来の
「CAT Controller Block搭載列車だけを制御する」方針とは一致していないため、Block検出を
実装する作業で見直しが必要である。
