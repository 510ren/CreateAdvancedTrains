# Target Speed Resolution

## Phase 6の目的

`TargetSpeedResolver`は、複数の独立した速度制約から、Createへ渡す最終CAT目標速度を決める
Server側の統合担当である。BrakingCurve、TASC、信号、ATC/ATS、EB、NotchControllerの責務を
このクラスへ混在させない。

## 入力と出力

| 区分 | 内容 |
| --- | --- |
| 入力 | Create由来の目標速度、現在速度、進行方向、各制約の速度上限・状態、CAT運転状態 |
| 出力 | 最終CAT目標速度、採用した制約、全候補制約と診断情報 |

公開・診断上の単位はblocks/sとし、Create境界でだけblocks/tickへ変換する。正方向・逆方向は
速度の絶対値による上限計算と方向符号の復元を分離する。

## 統合規則

- 各Controllerは「速度上限または状態」を返す。最終`targetSpeed`を書き込まない。
- 通常走行では、有効な正方向上限の最小値を採用する。
- 停止・到着待ち・EB・CAT安全ロックアウトは、単なる数値上限ではなく優先状態として扱う。
- 制約なしは上限値を勝手に0とせず、候補なしとして表す。
- BrakingCurveの`u_ceiling`はResolverまたはその上位設定から明示的に渡す。BrakingCurve自身は
  路線や設定から上限を選ばない。

## 未決定事項

- CAT `maxSpeed`設定の有無、配置、既定値、Create上限との関係。
- 信号・ATC/ATS・TASC・EBを含む最終的な優先順位。
- 制約が解除された際の目標速度の戻し方と、NotchSelectorとの分担。
- P1〜P5の力行Profileと、N・B1〜B7を含む自動ノッチ選択規則。
