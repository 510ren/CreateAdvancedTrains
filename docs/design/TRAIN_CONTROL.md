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

## 不変条件

- 通常制御で`Train.speed`を直接変更しない。
- Controllerの識別子はUUIDであり、列車名・配列位置・アドオン名ではない。
- Controllerは機能別の計算を抱え込まず、各Controller/Resolverへ責務を委譲する。
- CAT Controller Blockによる対象判定を導入するまでは、対象範囲を明示的に検証する。

## 現行実装の留意点

現行のLevelTick Debug処理は全発見列車についてControllerを更新・生成する。これは将来の
「CAT Controller Block搭載列車だけを制御する」方針とは一致していないため、Block検出を
実装する作業で見直しが必要である。
