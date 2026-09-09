# Create: Advanced Trains

## 目的

Createの列車へ、日本の電車システムを基本とした高度な運転・保安装置を追加する。
最終的には、Create標準の走行を活かしたまま、ATO、制動曲線、TASC、速度制限、
信号、ATC/ATS、独自ノッチ、高性能運転台、HUD/Debugを段階的に提供する。

## 固定前提

| 項目 | 値 |
| --- | --- |
| Minecraft | Java Edition 1.20.1 |
| Forge | 47.4.10 |
| Create | 6.0.8-289 |
| Java | 17 |
| Gradle | 8.8 |
| Mod ID | `create_advanced_trains` |
| Package root | `dev.edudio.createadvancedtrains` |

これらはユーザーが明示的に変更を承認しない限り変更しない。

## 基本方針

- 通常制御では `Train.speed` を直接変更しない。
- CATが制約を統合した目標速度を `Train.targetSpeed` へ渡し、Create標準の
  速度追従を利用する。
- 列車制御状態はTrain UUID単位で管理する。
- CAT公開値は速度をblocks/s、加速度をblocks/s²、距離をblocks、時間をsecondsで扱う。
- 不明なCreate仕様は、必ずCreate 6.0.8の実ソースで確認する。
- 制御、表示、入力、計測は責務を分離し、将来の機能追加で既存制御を壊さない。

## フェーズ

Phase 0〜4（環境、列車取得、TrainController、ATO targetSpeed介入、Speed Limit）は
実装済みである。現在はPhase 5の制動モデル設計およびCreate標準運転の計測段階である。
Phase 5の制御実装は、制動数学と受入条件が確定し、ユーザーが明示的に開始を承認するまで
行わない。
