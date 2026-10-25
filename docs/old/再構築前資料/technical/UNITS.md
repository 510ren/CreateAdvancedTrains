# 単位

## 原則

| 量 | CAT公開側 | Create境界 |
| --- | --- | --- |
| 速度 | blocks/s | blocks/tick |
| 加速度・減速度 | blocks/s² | blocks/tick² |
| 距離 | blocks | blocks |
| 時間 | seconds | ticks |

## 変換

```text
blocks/s       = blocks/tick × 20
blocks/tick    = blocks/s ÷ 20
blocks/s²      = blocks/tick² × 400
blocks/tick²   = blocks/s² ÷ 400
km/h           = blocks/tick × 72
blocks/tick    = km/h ÷ 72
```

`SpeedLimitController`の公開制限値はblocks/sであり、`Train.speed`と
`Train.targetSpeed`へ触れる境界でblocks/tickへ変換する。

Phase 5以降では、生の`double`の取り違えを防ぐため、BlocksPerTick、BlocksPerSecond、
Acceleration、Distance、Durationなどの型付きValue Object導入を検討する。
