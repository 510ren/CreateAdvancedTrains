# 未解決事項

## 最優先：Phase 5の制動仕様

| 項目 | 必要な決定・根拠 |
| --- | --- |
| B1〜B7 | B4は全速度で`baseAcceleration`とし、隣接ノッチ倍率差はPhase 5Bでは定数`δ_0 = 1/5`とする。速度依存の`δ(u)`補正はPhase 10で扱う。 |
| EB | 性能Profileと実際の発動・復旧実装は後続フェーズ。Phase 5BではB7不足・予測過走量の返却だけを実装する。 |
| 制動距離 | Phase 5Bの式・10 tick応答・停止閾値・最大反復回数は確定。Phase 10で速度依存補正を調整する。 |
| 逆算 | Phase 5Bは呼出側`u_ceiling`を引数として受ける二分探索。速度依存補正後の探索方法はPhase 10で再評価する。 |
| 安全余裕 | Phase 5Bは`M = 10 blocks`、`κ = 1.0`。速度依存の調整はPhase 10で行う。 |

## Create Navigation調査

- 進行方向に対応するleading TravellingPointの取得経路
- GlobalStation destinationと線路上EdgePointの取得経路
- `Navigation.distanceToDestination`の符号・単位・destinationなし時の値
- Create標準の駅近傍速度補正の正確な条件とtick順序

## Phase 5B実装を製品接続する前の決定

| 項目 | 現状 | 必要な決定 |
| --- | --- | --- |
| BrakingCurveの`u_ceiling`供給元 | Server ConfigにCAT `maxSpeed`キーはない。製品版TargetSpeedResolver・自動ノッチ選択も未実装のため、現時点では未接続でよい。 | 後続の統合制御Phaseで、CAT `maxSpeed`を設定化するか、Createの上限を採用するかを明示決定する。 |
| CAT中立ノッチN | Nは`targetSpeed = 0`と、同じtickにノッチ制御が`accelerationMod = 0`を返す組合せとする。 | 製品版NotchController実装時に、N中の外部Create target更新との優先順位・解除条件を決める。 |
| Navigation負距離の「脱線相当」 | CATエラーラッチとレンチ復旧待ちまで実装。Create `Train.crash()`は禁止境界と衝突するため未使用。 | Create物理脱線ではないCAT独自の安全ロックアウトイベントを用意できるか、Forge/CATのイベント境界を調査して決める。 |

## Phase 5で完了扱い：手動運転の停車対象距離照会

実装仕様と完了条件は`docs/technical/CREATE_NAVIGATION.md`の「次回Sol実装仕様」を正本とする。
本節はSolがCreate 6.0.8実ソースで確認すべき未解決の技術事項を示す。

- Navigationが有効なら`Navigation.distanceToDestination`を符号付きblocksの正本として返す。
- ユーザーの手動確認では、手動運転時の距離が正しく取得できない。Phase 5の制動設計では非阻害として
  ユーザー承認により完了扱いとし、解決はPhase 9へ繰り越す。
- このユーティリティは、停車に向けた減速かどうか、進行方向、停車対象の利用可否などのCreate関連判定も
  一元化する。HUD、BrakingCurve、TASC、各ControllerがCreate内部値や比較式を独自に読むことを禁止する。
- 未取得距離を0で表してはならない。取得元とunavailableを区別できる結果を返し、HUDは未取得値を
  `--`等で表示する。
- Phase 9では、単一の停車対象探索の再実装だけを前提にせず、到達可能な近傍駅をおおむね4駅まで
  取得して独立HUDへ表示する要件として再定義する。探索・分岐・並べ順・件数はその時点で決定する。

## 計測

- 各駅間のCreate標準走行を、位置・残距離を含めて毎tick記録する。
- 同一条件で最低3回測定し、速度、加速度、目標速度変更点、停止誤差を表へ集計する。
- 80/160 blocks/sへ実際に到達する区間と、カーブ制限区間を分けて測定する。

## Phase 5Aノッチ試験の設計・実装照合

- 旧B7初回試験では、減速中に7〜8 tickごとに`no_braking_demand`が入り、10 tick応答が
  毎回リセットされた。`appliedNotch = B7`が0件のため、B7性能として評価できない。
- 新B7試行では149 tick連続のB7完了区間を確認したが、同一server tick内の複数
  `approachTargetSpeed()`呼出が疑われる。Profile目標-4.0 blocks/s²に対しserver tick単位の
  実測値が-3.0、-4.0、-8.0 blocks/s²となる理由を、Create 6.0.8実ソースと呼出回数の記録で
  確認する必要がある。
- 現行`TrainMixin`はATO適用前の`train.targetSpeed`を
  `Phase5ANotchTestManager.modifyAccelerationMod()`へ渡す一方、その直前に
  `TrainController.applyAtoTargetSpeed()`が`train.targetSpeed`を変更し得る。
  Phase 5Aの減速要求判定が、Createの実際の速度追従で用いる目標速度と同じ値を参照しているか、
  Create 6.0.8実ソースと現在のMixin呼出順で確認する必要がある。
- 減速要求が一時的に見えないtickについて、`NotchResponseModel`をリセットするのか状態を
  保持するのかを、Createの複数`approachTargetSpeed()`呼出とtick順序の確認後に決める必要がある。
  この決定前にB7を再測定しても、有効な性能曲線にはならない。
- Phase 5Aログはsampleごとに最終観測targetSpeedを一つだけ残すため、同一server tick内の
  「B7を適用した呼出」と「制御対象外だった呼出」を区別できない。実装を修正する場合は、
  呼出回数、適用呼出のtargetSpeed、最終観測targetSpeed、各呼出で返した加速度修飾を
  混在させずに記録する形式を決める必要がある。
- schemaVersion 2の最新B7試行では、ATO後の最終targetSpeedは記録できたが、応答進捗が
  `approachTargetSpeed()`の呼出回数で進み、10 server tickと一致していない。応答時間の
  時間基準をserver tick、呼出回数、または連続した減速適用tickのどれにするかを、Create 6.0.8の
  呼出順を根拠に決める必要がある。
- Profile目標-4.0 blocks/s²に対してserver tick実測値-4.0/-8.0が混在する。次のログ形式では、
  server tickごとの`approachTargetSpeed()`全呼出を順番に記録し、各呼出についてpre-CAT target、
  final target、減速要求判定、返却したaccelerationMod、固定ノッチ適用有無を残す必要がある。

### schemaVersion 3後：Create標準制動モデルとの統合方法

- schemaVersion 3の原本では一server tick内の複数`approachTargetSpeed()`呼出は観測されず、
  B7修飾を返したtickの実測減速度は実効値と一致した。したがって「全ての`speed`更新関数へ
  介入する」ことは解決策ではない。
- 初回V3の160→100はATO速度制限による試験条件だったが、ATO・速度制限を無効にした
  `分離条件再測定.log`でも、B7適用tick（target 0）とtarget 160 blocks/sの通常加速tickが交互に
  存在する。ユーザー確認により手動運転は除外され、Create Navigationの標準制動距離再計算が原因と
  確定した。Navigationは基準`train.acceleration()`だけで`speed² / (2a)`を計算するため、B7で
  想定より速く減速すると、次tickに再加速targetを出す。
- `appliedNotch = B7`は状態保持であり、実際のB7適用指標ではない。性能値には
  `applicationState = applied`かつ`notchModifierAppliedCallCount = 1`のsampleだけを用いる。
- Phase 5Aで連続B7を測る方法を決める必要がある。候補は、(A) 初回のCreate減速要求を契機に
  試験専用のtarget 0要求を停止まで保持する、(B) BrakingCurve/TargetSpeedResolverを先に実装し、
  CATが最終targetを一貫して出せる段階まで性能測定を延期する、の二つである。Aは試験専用であり、
  製品版の通常制御へ`Train.speed`直接変更を持ち込んではならない。
- 「10 tick線形遷移」の時間基準は、上記の連続減速要求が確保された後に判定する。

## 解決済み：Phase 5A停止目標保持の解除ログ

- `RELEASEDあり.log`で、停止目標保持の解除は一回の`RELEASED` sampleの後に`INACTIVE`へ遷移することを
  二回の制動サイクルで確認した。前回の「RELEASEDが記録されない」不備は解消済みである。
- 速度0後に再加速することは、現在の試験治具が「速度0で解除」する仕様による。駅手前で停止することは
  Phase 5Aの単独B7性能測定では想定内であり、製品版の駅停止仕様の失敗ではない。

## B1の応答距離を含む自然停止距離

- B1は暫定ProfileでCreate基準と同じ-1.0 blocks/s²だが、10 tick線形応答中は即時の-1.0 blocks/s²より
  弱い。Create標準の制動開始地点から停止目標保持を開始すると、駅停止位置直前のCreate直接clampが
  入る可能性がある。
- 強制停車を含むB1ログは、応答と定常減速度の検証には使えるが、自然停止距離・停止精度の性能値には
  使わない。開始から駅停止位置までの距離は下限としてだけ記録する。
- B1の自然停止距離を正式測定する試験専用の早期ラッチ条件を、応答モデル・開始速度・安全余裕を踏まえて
  定義する必要がある。この条件は将来のBrakingCurveの停止数学と整合させ、ユーザー承認前に実装しない。

### 提案：B2〜B7から等間隔則でB1を導出する

- 日本の電車を基本とする「B1〜B7は等間隔の比例関係」という方針に従い、最終B1をCreate強制停車を
  含むB1停止距離から決めない。B2〜B7の定常減速度を主な根拠にして、速度点ごとに等間隔のB1〜B7系列を
  逆算する案を検討する。
- 各速度点で制動の大きさを正値`d_n(v) = -a_Bn(v)`とする。B2〜B7の測定値へ
  `d_n(v) = d_1(v) + (n - 1) × Δ(v)`を最小二乗で当てはめ、外挿した`d_1(v)`を最終B1候補とする。
  同じ`d_1(v)`と`Δ(v)`からB2〜B7も再構成し、測定誤差による不均一をProfileへ持ち込まない。
- 外挿したB1が0以下、B2測定値との乖離が許容範囲外、または速度点間で非物理的な曲線となる場合は、
  測定を追加し、安易にclampや個別例外を作らない。
- B1の実測は10 tick応答・定常減速度・上記外挿の妥当性確認に使う。ただしCreate強制停車を含む停止距離は
  フィット入力へ使わない。

## 現行実装との照合

- `SpeedLimitSource.TEST = 20.0 blocks/s`の初期登録が意図した挙動か。
- LevelTick Debug処理がController更新・ATO適用を行う現在の設計が、Mixin基準の介入方針と
  両立するか。
- CAT Controller Block未搭載列車をController対象から除外する正確な方法。
