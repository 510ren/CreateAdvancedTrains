# Create 6.0.8 技術前提

CATはCreate `mc1.20.1-6.0.8` の実ソースを技術上の正とする。別バージョンのCreateや
一般的なMinecraft知識からAPI・tick順序を推測しない。

現在参照している主要型は次のとおりである。

```text
Create.RAILWAYS
GlobalRailwayManager
Train
Navigation
CarriageContraptionEntity
ScheduleRuntime
GlobalStation
TravellingPoint
```

サーバー側の列車一覧は、既存コードで
`Create.RAILWAYS.sided(level).trains.values()`を使用している。Trainの`id`、`speed`、
`targetSpeed`は現在のPhase 0〜4で使用・検証済みである。

Navigationの先頭TravellingPoint、GlobalStationのEdgePoint、destinationまでの距離の
正確な取得経路は、TrainDataDebugger実装時にCreate 6.0.8実ソースで確認する。
