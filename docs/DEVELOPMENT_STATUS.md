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
- 初回B1試験では10 tick線形応答と-1.6 blocks/s²の安定区間を確認した。旧B7試行は
  応答未完了だったが、新B7試行では149 tickのB7完了区間を確認した。ただしserver tick内の
  複数呼出による目標速度・実測加速度の混在があるため、B7性能データは未受入である。詳細は
  `docs/research/EXPERIMENTS.md` と `docs/research/OPEN_QUESTIONS.md` を参照する。
- schemaVersion 3の改善後B7試行で、呼出単位の観測は完了した。一tick当たり最大一回の
  `approachTargetSpeed()`とB7修飾が確認され、修飾されたtickの実測減速度は実効値と一致した。
  旧ログの-8.0 blocks/s²混在はこの走行では再現しなかった。
- ATO・速度制限を無効にしたV3分離条件B7再測定では、B7を実際に適用した569 tickのうち、
  定常559 tickで実測-4.0 blocks/s²を確認した。一方、同じ走行にtarget 160 blocks/s・
  修飾なし・+1.0 blocks/s²のtickが843あり、走行全体の平均減速度は約-1.0 blocks/s²となった。
  B7の一回当たり性能は確認できたが、継続性能試験としては未受入である。
- schemaVersion 4の停止目標保持B7試験では、連続10 server tickの応答後、定常-4.0 blocks/s²を
  339 tick連続で確認した。初回ラッチから停止までの距離は約605.400 blocksである。固定Bノッチの
  継続性能測定は可能になった。後続の`RELEASEDあり.log`で、保持解除は一sampleの`RELEASED`の後に
  `INACTIVE`へ遷移することを確認し、観測性の不備も解消した。B1〜B7の反復測定へ進める。
- Phase 5Aの試験ノッチJava実装は作業ツリーに存在するが、設計受入は未完了である。
  160 blocks/s要求はCreate Navigationが基準加速度だけで再計算する標準制動距離に由来することを確認した。
  停止目標保持を採用し、連続したB7減速要求で10 server tick応答と継続性能を確認した。次は
  `RELEASED`状態ログの修正後、B1〜B7の反復測定を行う。BrakingCurve、EB、TASC、製品版自動ノッチの
  Java実装は引き続き未開始である。
- BrakingCurve、EB、TASC、製品版自動ノッチのJava実装は未開始であり、ユーザーの明示承認と
  Create 6.0.8実ソースによる境界確認が必要である。
- 最終B1〜B7性能、EB、実測後の速度依存減速度表、安全余裕、制動距離逆算、低速域制御は未確定。

## 次回Sol作業の前提工程

次回のSolによるJava実装では、B1〜B7の次の測定工程または制動数学へ進む前に、Create列車情報を
一元的に照会する読み取り専用ユーティリティを実装する。Navigation値を優先し、手動運転ではCreateと
同じstation探索から次の停車対象までの距離を導出する。あわせて停車に向けた減速判定等のCreate関連
判定をこのユーティリティへ集約し、HUDの未取得距離を0ではなく未取得表示へ変更する。

SolはCreate 6.0.8実ソースで探索の副作用・進行方向・単位を確認し、Java実装とGradle buildだけを
完了条件とする。Minecraftの起動・HUD確認・手動運転試験・ログ採取はユーザーが行う。この実装報告を
設計側が確認してから、次の工程へ進む。

## 現行コードと設計の照合が必要な項目

以下は現行コードで確認された事実であり、今後の実装前に設計意図との照合が必要である。

- `TrainController`生成時に `SpeedLimitSource.TEST = 20.0 blocks/s` を登録している。
- `TrainDebugEvents`はLevelTickごとにControllerを更新し、結果としてATO適用経路を呼ぶ。
- CAT Controller Blockによる対象列車判定は、現行コードには未実装である。

上記は本書で仕様変更するものではない。Solが将来作業する際は、ユーザー承認済みの
仕様とCreate 6.0.8実ソースを根拠に扱うこと。
