# 開発状況

## 完了済み

| Phase | 内容 | 状態 |
| --- | --- | --- |
| 0 | Forge/Create開発環境、開発起動、Release build | 完了 |
| 1 | Create列車のUUID、`speed`、`targetSpeed`取得 | 完了 |
| 2 | Train UUID単位のTrainController管理 | 完了 |
| 3 | `atoTargetSpeed`を`Train.targetSpeed`へ反映する介入 | 完了 |
| 4 | SpeedLimitController、複数制約の最小値、設定、テストコマンド | 完了 |

## 完了：Phase 5の制動基盤

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
  `INACTIVE`へ遷移することを確認し、観測性の不備も解消した。
- `docs/reference/logs/09-10_1/`でB1〜B7を各3回、計21走行測定した。B2〜B7は全反復で10 tick後に
  Profile目標の定常減速度（-1.5〜-4.0 blocks/s²）へ到達し、実測値も一致した。B1も定常-1.0 blocks/s²と
  応答は確認できたが、停止点残距離0.22 blocks・速度6.92 blocks/sからCreateの強制停止補正を受けるため、
  自然停止距離・停車精度には使用しない。詳細は`docs/research/EXPERIMENTS.md`第21節を正本とする。
- Phase 5Aの試験ノッチJava実装と測定治具は、暫定Profileを指定どおりに適用・観測できる状態まで受入済みである。
  ただし測定した値は試験用入力の再現確認であり、最終ゲーム内Profile・速度依存表・制動数学を確定したものではない。
  BrakingCurve、EB、TASC、製品版自動ノッチのJava実装は引き続き未開始である。
- BrakingCurve、EB、TASC、製品版自動ノッチのJava実装は未開始であり、ユーザーの明示承認と
  Create 6.0.8実ソースによる境界確認が必要である。
- 最終B1〜B7性能、EB、実測後の速度依存減速度表、安全余裕、制動距離逆算、低速域制御は未確定。

## 完了扱い：手動運転の停車対象距離照会

次回のSolによるJava実装では、B1〜B7の次の測定工程または制動数学へ進む前に、Create列車情報を
一元的に照会する読み取り専用ユーティリティを実装する。Navigation値を優先し、手動運転ではCreateと
同じstation探索から次の停車対象までの距離を導出する。あわせて停車に向けた減速判定等のCreate関連
判定をこのユーティリティへ集約し、HUDの未取得距離を0ではなく未取得表示へ変更する。

詳細な実装範囲、Create 6.0.8の確認事項、停止条件、非対象、Solの完了条件は
`docs/technical/CREATE_NAVIGATION.md`の「次回Sol実装仕様」を正本とする。SolはCreate 6.0.8実ソースで
探索の副作用・進行方向・単位を確認し、Java実装とGradle buildだけを完了条件とする。Minecraftの起動・
HUD確認・手動運転試験・ログ採取はユーザーが行う。

ユーザーの手動確認では、手動運転時に距離を正しく取得できないことが判明した。これは現時点の
Phase 5の制動仕様・試験の前提としては重要ではないため、ユーザー承認により当該工程は**完了扱い**とし、
この不具合はPhase 5Bの開始を妨げない。手動探索の改善はPhase 9の独立HUD要件へ繰り越す。
この工程のレビューは、ユーザーからの実装完了・手動確認結果の報告をもって完了とする。別途のSol報告は
この工程の完了条件としない。

## 完了記録：Phase 5B — 常用制動数学と最終Profile

Phase 5Bは、Phase 5Aの固定ノッチ測定の後に行った、常用制動の数学・要件・受入条件の確定と
製品基盤Java実装のPhaseである。数学・受入条件・実装範囲へのユーザー承認およびSolの実装完了報告を
受け、Phase 5全体を完了扱いとする。

### 開始条件

1. Phase 5Aの固定ノッチ測定が受入済みであること。
2. 停車対象距離照会について、手動運転時の未取得はPhase 9へ繰り越すというユーザー承認があること。

### Phase 5Bで確定すること

- 実車資料とは分離した、ゲーム内の最終B1〜B7 Profileの考え方と数値根拠
- V1で速度依存Profileを持つか、持つなら速度点・補間・低速域の扱い
- 既決の10 tick線形応答を含む常用制動距離の前向き計算
- 残距離から最大許容速度を得る逆算・数値探索の方法
- 距離余裕・減速度安全係数など安全側パラメータの意味、単位、初期値の決め方
- BrakingCurveの入力、出力、責務境界、受入試験とログで確認すべき値

Phase 5Bでは、B4を全速度で`baseAcceleration`に固定し、隣接ノッチ倍率差`δ(u)`は定数関数として
定義する。速度依存の`δ(u)`補正は、全体機能を統合後に走行データで評価する新設Phase 10調整フェーズで扱う。

### Phase 9へ繰り越すHUD・距離表示

Phase 9では、単一の「次の停車対象までの距離」だけに依存しないCAT独立HUDを設計する。進行可能な
近傍駅をおおむね4駅まで列挙し、それぞれの距離を表示する方向を採用する。正確な駅数、探索範囲、
分岐時の到達可能性、並び順、destinationとの関係はPhase 9で確定する。現在の手動距離未取得を
`0`で偽装して製品機能へ流用してはならない。

### 実装済み範囲

- 製品版`NotchProfile`、`NotchResponseModel`、定数`delta(u) = 1/5`、定数`kappa(u) = 1.0`
- `BrakingCurve`の応答込み停止距離、二分探索、状態分離、予測過走量
- Create Navigation残距離を取得元に依存しない状態型へ正規化する読み取り専用照会
- `FlagDeterminer`による`AT_DESTINATION_OR_ARRIVAL_PENDING`共有フラグと、ATOControllerがそれを参照する状態遷移
- 列車単位のCAT計算エラー状態と、将来のマスコン/HUDが表示できる原因情報

### Phase 5から繰り越すこと

- `TargetSpeedResolver`、製品版自動ノッチ選択、TASC、ATC、ATS、信号制約のJava実装
- 実際のEB発動・EB性能Profile・脱線相当状態への変更。Phase 5BはEB要求に必要な状態・予測過走量を返すだけとする。
- `Train.speed`への直接介入、Create標準速度追従経路の変更
- マスコンBlockのGUI/HUD実装。Phase 5Bは表示用データを提供するだけとする。

範囲外のTargetSpeedResolver、製品版自動ノッチ選択、TASC、ATC/ATS、信号、EB実動作は、
`docs/PHASE_ROADMAP.md`に従って後続Phaseで仕様を完成・承認してから開始する。

### Phase 5B実装報告の記録（2026-09-10）

- Solの実装報告により、製品版Profile・応答モデル・純粋なBrakingCurve・Navigation正規化・
  到着共有フラグ・列車単位の計算エラーラッチが追加されたことを確認中である。`build/libs/`には
  当該Java変更後のjarが生成されているが、正式なbuild結果はSolの実装報告に記載された結果を正本とする。
- 自動運転がBrakingCurveの最大許容速度からノッチを選択・適用する接続は、今回の明示的な非対象であり、
  現時点では存在しない。Phase 5A設定が有効なときだけ固定ノッチを適用する試験治具は、製品版自動ノッチではない。
- BrakingCurveの製品呼出は未接続である。現在は製品版のTargetSpeedResolverおよび自動ノッチ選択を
  実装しないため、`u_ceiling`の実際の供給元をまだ必要としない。これらを実装する後続Phaseで、
  CAT `maxSpeed`設定を導入するか、Createの上限を明示的に採用するかを決める。
- Navigationの負距離は`NAVIGATION_DESTINATION_OVERRUN`として列車単位のCATエラーにラッチされ、
  新規ATO指令を抑制してレンチ復旧待ちとなる。`Train.crash()`は`Train.speed`変更とgraph切断を伴うため、
  CATからCreateの物理的な脱線状態へ接続していない。
- 将来のCAT中立ノッチNは`targetSpeed = 0`かつ製品版ノッチ制御が`accelerationMod = 0`を返す状態とする。
  `targetSpeed = 0`だけではCreate標準の`approachTargetSpeed()`が減速するため、Nにはならない。Phase 5Bの
  現実装は製品版ノッチ制御を持たず、新たなCAT targetを発行しない。
- Phase 5A停止目標保持の解除通知は、当該関数が毎tick呼ばれるとクライアントメッセージも毎tick送信されるため、
  ユーザー承認により削除した。解除状態は既存ログの状態値で観測する。
- `SpeedLimitSource.TEST = 20.0 blocks/s`の初期登録は不要なテスト制限として、ユーザー承認により削除した。

## 次段階：Phase 6

Phase 6はTargetSpeedResolver、ATO統合、製品版自動常用ノッチの設計・実装である。開始前に
`u_ceiling`供給元、CAT対象列車の判定、Nを含む`targetSpeed`と`accelerationMod`の組合せ、
ノッチ選択規則を確定する。詳細とPhase 7〜10の計画は`docs/PHASE_ROADMAP.md`を正本とする。
Terra/SolのPhase 6移行時に読む具体的な引継ぎは`docs/handoff/PHASE_6_TERRA_SOL_HANDOFF.md`である。

## 現行コードと設計の照合が必要な項目

以下は現行コードで確認された事実であり、今後の実装前に設計意図との照合が必要である。

- `TrainController`生成時に `SpeedLimitSource.TEST = 20.0 blocks/s` を登録している。
- `TrainDebugEvents`はLevelTickごとにControllerを更新し、結果としてATO適用経路を呼ぶ。
- CAT Controller Blockによる対象列車判定は、現行コードには未実装である。

上記は本書で仕様変更するものではない。Solが将来作業する際は、ユーザー承認済みの
仕様とCreate 6.0.8実ソースを根拠に扱うこと。
