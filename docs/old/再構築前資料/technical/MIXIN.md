# Mixin

## 現在のMixin

`TrainMixin`はCreateの`Train`へ適用され、`approachTargetSpeed(float)`のHEADで
TrainControllerを取得してATO目標速度を適用する。

```text
CreateがapproachTargetSpeed()を呼ぶ
        ↓
TrainMixin HEAD
        ↓
CATがtargetSpeedを制約
        ↓
Create本来の速度追従
```

NavigationだけでなくCarriageContraptionEntity側も`approachTargetSpeed()`を呼ぶため、
Navigation固有の介入だけでは制御範囲が不足する。

## 制約

- Mixinは公開API・Forgeイベントで目的を達成できない場合だけ使用する。
- Inject位置、呼出元、tick順序、他Modとの競合をCreate 6.0.8実ソースで確認する。
- TrainDataDebuggerのような受動観測機能はMixinを使用しない。
