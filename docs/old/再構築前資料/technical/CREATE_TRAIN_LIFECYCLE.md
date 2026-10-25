# Create列車のライフサイクル

## 現在の取得方法

サーバーのOverworld LevelTickで `Create.RAILWAYS.sided(level).trains.values()` を列挙し、
Train UUIDでControllerを対応付けている。`TrainControllerManager.removeMissing()`は、
その時点で列挙されないUUIDのControllerを削除する。

## 設計上の注意

- Controllerの生成・削除は、将来のCAT Controller Block対象判定と切り離してはならない。
- 受動型TrainDataDebuggerはControllerを生成・更新・削除せず、同じ列車一覧を読み取るだけにする。
- world reload、dimension、列車消滅、server stopping時の状態保存は未設計であり、
  Persistence導入時に別途仕様化する。
