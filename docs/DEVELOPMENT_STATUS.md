# 開発状況

## 完了済み

| Phase | 内容 | 状態 |
| --- | --- | --- |
| 0 | Forge/Create開発環境、開発起動、Release build | 完了 |
| 1 | Create列車のUUID、`speed`、`targetSpeed`取得 | 完了 |
| 2 | Train UUID単位のTrainController管理 | 完了 |
| 3 | `atoTargetSpeed`を`Train.targetSpeed`へ反映する介入 | 完了 |
| 4 | SpeedLimitController、複数制約の最小値、設定、テストコマンド | 完了 |

## 現在：Phase 5の設計・計測

- Create標準自動運転の反復ベースライン（11走行）は収集・集計済み。
- 受動型のTrainDataDebugger仕様は `docs/research/EXPERIMENTS.md` に記録済み。
- Phase 5Aの固定ノッチ試験仕様（B1〜B7暫定倍率、10 tick線形応答、再遷移規則、
  毎tickProfile再評価）は `docs/design/NOTCH.md` に記録済み。
- 制動曲線・ノッチのJava実装は未開始。Phase 5実装開始には、ユーザーの明示承認と
  Create 6.0.8実ソースによる境界確認が必要である。
- 最終B1〜B7性能、EB、実測後の速度依存減速度表、安全余裕、制動距離逆算、低速域制御は未確定。

## 現行コードと設計の照合が必要な項目

以下は現行コードで確認された事実であり、今後の実装前に設計意図との照合が必要である。

- `TrainController`生成時に `SpeedLimitSource.TEST = 20.0 blocks/s` を登録している。
- `TrainDebugEvents`はLevelTickごとにControllerを更新し、結果としてATO適用経路を呼ぶ。
- CAT Controller Blockによる対象列車判定は、現行コードには未実装である。

上記は本書で仕様変更するものではない。Solが将来作業する際は、ユーザー承認済みの
仕様とCreate 6.0.8実ソースを根拠に扱うこと。
