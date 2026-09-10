# Phase 6 引継ぎ — Terra / Sol

> **作成目的:** Phase 5完了後、Phase 6の仕様整理を担当するTerraと、承認後にJavaを実装するSolが、
> 現在の実装境界・開始条件・未決定事項を同じ前提で扱うための個別引継ぎである。
>
> **重要:** この文書はPhase 6のJava実装許可ではない。未決定事項をSolが補完してはならない。

## 1. 最初に読むもの

1. ユーザーの最新指示
2. `AGENTS.md`
3. `docs/handoff/ROLE_AND_WORKFLOW_BASELINE.md`
4. `docs/PHASE_ROADMAP.md`
5. `docs/DEVELOPMENT_STATUS.md`
6. `docs/design/TARGET_SPEED_RESOLUTION.md`
7. `docs/design/NOTCH.md`
8. `docs/design/ATO.md`
9. `docs/design/BRAKING_MODEL.md`
10. `docs/technical/CREATE_SPEED_PHYSICS.md`、`docs/technical/MIXIN.md`、`docs/technical/UNITS.md`
11. 現在のcheckoutと、必要ならCreate `mc1.20.1-6.0.8`実ソース

この引継ぎと現在のコードが異なる場合は、黙って一方に合わせない。差分と根拠をユーザーへ報告する。

## 2. Phase 5から引き継ぐ確定事項

- 通常CAT制御は`Train.speed`を直接変更しない。`Train.targetSpeed`とCreate標準の
  `Train.approachTargetSpeed(float accelerationMod)`経路だけを使用する。
- CAT公開値・設定・HUD・デバッグの単位は、速度blocks/s、加速度blocks/s²、距離blocks、時間seconds。
  Create境界だけblocks/tickへ変換する。
- Phase 5BのBrakingCurveは純粋計算であり、最大許容速度・予測停止距離・利用可能距離・状態を返す。
  `Train`、world、targetSpeed、ノッチ、EBを変更しない。
- B1〜B7はB4基準の初期Profileである。`delta(u) = 1/5`、`kappa(u) = 1.0`、B1〜B7倍率は
  `0.4, 0.6, 0.8, 1.0, 1.2, 1.4, 1.6`。速度依存補正はPhase 10まで導入しない。
- ノッチの加減速度応答は10 tick線形遷移である。新しい要求は、その瞬間の実効加減速度から新目標へ
  改めて10 tickで遷移する。
- Navigationは`destination != null && distanceToDestination > 0`だけをBrakingCurveへ渡せる`AHEAD`として
  正規化する。距離0は到着待ち、負距離はCATオーバーラン異常である。
- `AT_DESTINATION_OR_ARRIVAL_PENDING`はFlagDeterminerが作る列車単位・読み取り専用共有フラグであり、
  ATO、TASC、ATC等の利用側が変更してはならない。
- 計算異常は列車UUID単位のCATエラーラッチで保持し、原因が直っても自動解除しない。将来のレンチ復旧は
  「レンチ使用→条件検証→回復または理由通知」の順で行う。
- `destination != null && distanceToDestination < 0`はCreateの物理脱線を呼ばない。将来、CAT独自の
  脱線相当安全ロックアウトイベントとして扱う方向である。
- Phase 5Aの固定ノッチ・停止目標保持・ログは測定専用であり、製品版自動ノッチやATO/TASC/EBへ流用しない。

## 3. Phase 5終了時の実装状態

Phase 5Bで、少なくとも次の基盤クラスが追加されている。

| 区分 | 主なクラス | 現在の役割 |
| --- | --- | --- |
| 制動数学 | `control.braking.BrakingCurve`、`BrakingCurveInput`、`BrakingCurveResult` | B7安全側停止距離と最大許容速度の純粋計算。 |
| BノッチProfile | `control.notch.NotchProfile`、`NotchResponseModel` | B1〜B7の初期Profileと10 tick応答。製品版自動選択は未実装。 |
| Navigation境界 | `train.query.CreateTrainQueryUtil`、`NormalizedNavigationStop` | Create Navigationの状態・方向・前方残距離を読み取り専用で正規化。 |
| 共有状態 | `train.FlagDeterminer`、`TrainOperationalFlags` | 到着待ちフラグの一元導出。 |
| 異常状態 | `train.error.*` | 計算エラー・オーバーランのラッチと将来のレンチ復旧境界。 |
| ATO境界 | `control.AtoController`、`AtoTargetSpeedDecision` | 到着待ち・エラー時に新規CAT指令を抑制する。 |

次の機能は**未接続または未実装**である。

- BrakingCurve結果をCreateの実際の最終targetへ接続する`TargetSpeedResolver`
- 製品版NotchSelector / NotchController
- 力行P1〜P5
- TASC、信号、ATC/ATS、EB、CAT安全ロックアウトイベント
- CAT Controller Blockを搭載した列車だけに制御対象を絞る判定
- CAT `maxSpeed`設定。現時点でBrakingCurveの`u_ceiling`実運用の供給元は未決定。

## 4. Phase 6の目標

Phase 6は、Phase 5の計算基盤を実走行の通常制御へ接続するPhaseである。

```text
Create由来の目標 / 速度制限 / BrakingCurve上限
                    ↓
          TargetSpeedResolver
                    ↓
             ATO境界の最終target
                    ↓
  NotchSelector（P1〜P5 / N / B1〜B7を選択）
                    ↓
  NotchController（10 tick応答をaccelerationModへ反映）
                    ↓
      Create.approachTargetSpeed()
```

Phase 6は駅の最終停止を実装しない。TASCはPhase 7、保安装置とEBはPhase 8、マスコン/HUD/専用入力は
Phase 9、走行データに基づく数値調整はPhase 10である。

## 5. Terraの次の担当

TerraはJavaを変更せず、以下を順に実施する。

1. Phase 6の未決定事項を、承認済み事項・提案・質問に分離する。
2. Create 6.0.8実ソースを確認し、`targetSpeed`と`accelerationMod`の組合せが、力行・N・Bの各状態で
   どのように`speed`へ作用するかを`docs/technical/CREATE_SPEED_PHYSICS.md`へ記録する。
3. CAT Controller Block搭載列車の検出方法をCreate 6.0.8実ソースから調査する。安全な読み取り経路が
   不明なら、候補を推測で仕様化せず`docs/research/OPEN_QUESTIONS.md`へ記録する。
4. `TargetSpeedResolver`、`NotchSelector`、`NotchController`の責務、入力、出力、単位、状態遷移、
   受入条件を各設計書へ記載する。
5. P1〜P5のProfileについて、倍率・速度依存・上限付近の力行抑制をユーザーと決める。Bノッチの式から
   自動的に導出しない。
6. `u_ceiling`供給元を決める。CAT `maxSpeed`を新設する場合は設定パス、キー、blocks/s既定値、
   Create側上限との関係を明文化する。
7. 実装に必要な仕様が揃ってユーザー承認を得た後だけ、Sol向けの個別実装依頼を作成する。

## 6. Phase 6で未決定のためSolが決めてはいけないこと

- P1〜P5の倍率、速度依存Profile、個別補正、最高速度付近の扱い
- P/B/Nの切替閾値、ヒステリシス、デッドバンド、快適性・ジャークの数値
- `u_ceiling`の供給元、CAT `maxSpeed`の設定キー・既定値、Create `train.maxSpeed()`採用の可否
- Resolverにおける制約の優先順位と、制約解除時の速度回復規則
- CAT Controller Blockの検出方法、対象外列車に対する挙動
- エラー・オーバーラン時のCAT安全ロックアウトイベントの具体形
- TASC、信号、ATC/ATS、EB、HUD、マスコン、専用キーの実装

これらが未承認のままなら、Solは該当するJava実装を開始せず、確認した事実と選択肢を返す。

## 7. Solへの将来のJava依頼に必ず含める事項

実装許可後の依頼は、少なくとも次を明記する。

1. 対象をPhase 6だけに限定すること。
2. 承認済みのP1〜P5/N/B1〜B7 Profile、ノッチ選択規則、Nの`targetSpeed = 0`と
   `accelerationMod = 0`の同時適用規則。
3. Resolverの候補制約型、最小値処理、停止・到着待ち・エラーの優先規則。
4. `u_ceiling`供給元と単位変換、CAT対象列車の判定根拠。
5. 確認対象のCreate 6.0.8クラス・メソッドと、確認すべきtick順序。
6. 変更してよい既存クラスと、Phase 5A測定治具・BrakingCurve純粋性・`Train.speed`非変更などの
   保護対象。
7. Solの完了条件をJava実装、静的確認、Gradle build、実装報告に限定すること。Minecraft起動、
   操作、走行試験、ログ採取はユーザー担当であり、Solの完了条件に含めないこと。

## 8. Phase 6の開始ゲート

以下を満たすまでは、Phase 6はTerraの設計・技術調査だけを行う。

- `u_ceiling`の供給元と単位が承認済みである。
- P1〜P5のProfileと、P/N/Bの選択規則が承認済みである。
- NをCreate境界へ反映する`targetSpeed`/`accelerationMod`の組合せとtick順序が、Create 6.0.8実ソースで
  確認済みである。
- CAT Controller Block対象判定が承認済みである。
- ResolverのI/O、状態優先、非対象が承認済みである。
- Solの実装範囲・受入条件・保護対象を含む個別依頼が作成済みである。

## 9. Solの実装後にTerraが確認すること

- 仕様上の値や安全・操作規則をSolが推測で追加していないこと。
- `Train.speed`を通常制御で直接変更していないこと。
- BrakingCurveが引き続きworld・target・ノッチを書き換えないこと。
- Phase 5A試験用の固定ノッチと製品版Phase 6自動ノッチが混線していないこと。
- Server制御とClient HUD/入力が混在していないこと。
- SolのGradle build結果と静的確認結果を記録し、ユーザーの手動走行試験とは区別していること。

## 10. Phase 6の完了判定

Phase 6は、承認済み仕様どおりのJava実装とSolのbuild/静的確認報告が揃い、Terraが仕様照合を完了した時点で
実装上の完了候補となる。Minecraft上の走行・ノッチ遷移・停止挙動の受入はユーザーが手動で判断し、
結果を`docs/research/EXPERIMENTS.md`へ記録する。

作成日: 2026-09-10
