# 走行試験

## Sol実装依頼：受動型列車走行データロガー（TrainDataDebugger）

### 1. 目的と位置付け

Create標準の自動運転が実際に計算・適用した値を、列車ごとに継続して
`.log` ファイルへ記録する。取得したデータは、Phase 5Aの制動試験の
基準データ、ならびに将来のTASC・BrakingCurve・EB設計の根拠とする。

これは **観測専用の試験支援機能** であり、CATの制御機能でも、Phase 5の
BrakingCurve実装でもない。CreateおよびCATの走行値を変えることは絶対に
許可しない。

現在の `TrainDebugEvents` は `TrainControllerManager.updateAll()`、
`removeMissing()`、および `getOrCreate()` を呼ぶため、本機能の実装基盤に
使用してはならない。TrainDataDebuggerは、TrainController、AtoController、
SpeedLimitController、Mixin、Network/HUDのいずれにも依存しない独立機能とする。

### 2. 範囲

#### 実装すること

- サーバー側でCreateの列車一覧を読み取り、各列車のスナップショットを採取する。
- 列車UUIDごと、かつロギングセッションごとに別の `.log` ファイルへ追記する。
- 設定が有効な間だけファイル作成・書き込み・Writer保持を行う。
- 将来フィールドを追加しやすい、バージョン付き・行単位の構造化ログを出力する。
- 設定変更、列車消滅、レベルアンロード、サーバー停止時に安全にWriterを閉じる。
- 入出力エラーがあっても、列車制御やサーバーtickを停止・変更しない。

#### 実装してはならないこと

- `Train.speed`、`Train.targetSpeed`、`Train.runtime`、`Train.navigation`、
  carriage、contraption、world状態を変更すること。
- `Train.approachTargetSpeed()`を呼ぶこと、またはMixinを追加すること。
- `TrainController`の生成・更新・削除、`TrainControllerManager`へのアクセス、
  ATO/Speed Limit/TASC/Notch/EBの呼び出し。
- クライアントへのPacket送信、HUDへの表示、操作GUI、コマンドによる制御。
- CAT Controller Blockの有無をロギング対象の条件にすること。試験時は、
  読み取れる全Create列車を対象にする。

### 3. 設定

`AdvancedTrainsConfig` のServer Configに、少なくとも次を追加する。

```toml
[debug.train_data]
enabled = false
sample_interval_ticks = 1
```

| 設定                                     | 要件                                                                                                                                                   |
| ---------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `debug.train_data.enabled`               | 既定値は `false`。`false` のとき、TrainDataDebuggerはWriter・出力ディレクトリ・ログファイルを作らず、ファイルI/Oを一切実行しない。                     |
| `debug.train_data.sample_interval_ticks` | 既定値は `1`。正の整数のみ許可する。1は毎tick採取を意味する。長距離の通常区間で出力量を抑えるための将来用設定であり、駅進入の精密試験では1を使用する。 |

設定は各採取判定時に読み取る。稼働中に `enabled` が `false` になった場合は、
以後の採取・書き込みを停止し、開いているWriterを閉じる。`true` へ戻った場合は、
既存ファイルを再利用せず新しいロギングセッションとして開始する。

### 4. 独立した構成

配置案は以下とする。Solは現在のpackage構造を確認し、同等の責務分離を守る。

```text
dev.edudio.createadvancedtrains.debug.traindata
├─ TrainDataDebugger.java       # ログ機能のライフサイクルと設定ゲート
├─ TrainDataSnapshot.java       # 読み取り専用・不変の採取データ
├─ TrainDataLogWriter.java      # セッション別ファイルの生成・追記・終了
└─ TrainDataDebugEvents.java    # サーバーtick / shutdown等の受動的イベント接続
```

- 補助クラスを追加してよいが、上記以外のCAT制御クラスへ依存を広げない。
- `TrainDataSnapshot` はCreateの値を読むだけで、CreateまたはCATのオブジェクトを
  外部へ公開しない値オブジェクトとする。
- イベント接続はサーバー側だけとし、列車の速度更新を妨げない観測時点を、
  Create 6.0.8とForgeの実際のtick順序を確認して選ぶ。
- 既存の `TrainDebugEvents` を変更・流用してはならない。必要なら別のイベント
  subscriberを作る。

### 5. 出力先とファイル単位

出力先は、ゲーム実行ディレクトリ配下の次とする。

```text
logs/create_advanced_trains/train_data/
```

ファイル名は、少なくとも列車UUIDと開始時刻を含み、既存ログを上書きしない。

```text
train-<train-uuid>-<session-start-utc>.log
```

一つのファイルは「一つの列車 × 一つの有効化セッション」である。複数列車の
データを一つのファイルへ混在させない。これによりTerraが個別列車の挙動を
迅速に読め、後で列車間比較もできる。

### 6. ログ形式

形式は、拡張子が `.log` の **JSON Lines（1行に1個のJSON object）** とする。
人間がそのまま読みやすく、Terraが会話中に確認しやすく、将来の集計処理も
行いやすいためである。行の並びは発生順とする。

必須の共通フィールド：

```text
schemaVersion     # 初期値 1
recordType        # session_start / sample / session_end / error
trainId           # UUID文字列
```

#### `session_start`

ファイルの最初の行に一度だけ書き込む。

```json
{
    "schemaVersion": 1,
    "recordType": "session_start",
    "trainId": "<uuid>",
    "startedAtUtc": "2026-09-09T12:34:56Z",
    "dimension": "minecraft:overworld",
    "sampleIntervalTicks": 1,
    "units": {
        "speedInternal": "blocks/tick",
        "speedDisplay": "blocks/s",
        "accelerationInternal": "blocks/tick^2",
        "accelerationDisplay": "blocks/s^2"
    }
}
```

#### `sample`

採取時点ごとに一行書き込む。初期版で必須とする値は以下である。

```json
{
    "schemaVersion": 1,
    "recordType": "sample",
    "trainId": "<uuid>",
    "serverTick": 123456,
    "levelGameTime": 123456,
    "speed": {
        "blocksPerTick": 0.25,
        "blocksPerSecond": 5.0
    },
    "targetSpeed": {
        "blocksPerTick": 0.0,
        "blocksPerSecond": 0.0
    },
    "createAcceleration": {
        "blocksPerTickSquared": 0.0075,
        "blocksPerSecondSquared": 3.0
    },
    "observation": {
        "source": "Create 6.0.8 Train",
        "controlModifiedByTrainDataDebugger": false
    }
}
```

`speed` と `targetSpeed` は符号を保持する。`blocksPerSecond` は
`blocksPerTick × 20`、`blocksPerSecondSquared` は
`blocksPerTickSquared × 400` とする。桁の丸めで内部値を失わないこと。

初期版の`sample`には、次の`navigation`オブジェクトも**必須**とする。

```json
{
  "navigation": {
    "leadingPointReference": "Create Navigation leading TravellingPoint",
    "leadingTravellingPoint": {
      "railPosition": "<TravellingPointを線路上で一意に特定できる構造化位置>"
    },
    "destination": {
      "type": "GlobalStation",
      "edgePoint": "<destinationであるGlobalStationの線路上EdgePointを一意に特定できる構造化位置>"
    },
    "distanceToDestinationBlocks": 123.45
  }
}
```

このオブジェクトには次の意味を与える。

| フィールド | 必須仕様 |
| --- | --- |
| `leadingTravellingPoint` | 車体モデルの最前端、carriageの先頭、推測した座標ではない。**Create Navigationが現在の進行方向に応じて先頭側として使用するTravellingPoint**を記録する。 |
| `destination.edgePoint` | 停止位置は任意のワールド座標ではない。**Navigation destinationであるGlobalStationの線路上EdgePoint**を記録する。 |
| `distanceToDestinationBlocks` | `Navigation.distanceToDestination` の値を、絶対値化・丸め・符号反転せず、そのまま符号付きblocksとして記録する。 |

SolはCreate 6.0.8実ソースを確認し、TravellingPointとEdgePointを後から曖昧さなく
追跡できる構造化データとして出力する。取得可能であれば、グラフ上のedge識別子、
edge内距離、端点、およびワールド座標を含めてよい。Create APIに存在しないフィールドを
推測してログ形式に追加してはならない。

Navigation destinationがない、またはdestinationがGlobalStationではない列車では、
`leadingTravellingPoint`は取得可能な場合に記録し、`destination`と
`distanceToDestinationBlocks`は`null`とする。その場合は、理由を示す
`destinationAvailability`文字列を同じ`navigation`オブジェクトへ追加する。

Create 6.0.8の実ソースで安全に読めると確認できた場合、次の任意オブジェクトを
将来追加してよい。未確認のAPIを推測して出力してはならない。

```text
runtime          # auto schedule、paused等の観測値
track            # カーブ・勾配などの観測値
trainComposition # carriage数などの観測値
```

新規フィールドは既存の値を改名・削除せず、任意のネストしたオブジェクトとして
追加する。互換性を壊す変更時だけ `schemaVersion` を上げる。

#### `session_end` と `error`

終了時は `session_end` を可能な限り出力し、終了理由を記録する。
I/Oエラーをログファイルへ安全に出せる場合は `error` を出力してから閉じる。
書き込み不能な場合でも、サーバーログへの簡潔な警告に留め、ゲーム状態には
影響させない。

### 7. データ取得とライフサイクル

1. Server Configが無効なら直ちに終了する。このときファイル操作を行わない。
2. 有効なら、既存の読み取り実績がある
   `Create.RAILWAYS.sided(level).trains.values()` から列車を列挙する。
3. 各列車について、設定されたtick間隔で読み取り専用Snapshotを生成する。
4. その列車のセッションWriterがなければ、出力ディレクトリ・ファイルを作成し、
   `session_start` の後に最初の `sample` を書く。
5. 列車が列挙対象から消えた場合、または設定無効・レベルアンロード・サーバー
   停止時は、該当Writerを閉じる。

Writerはバッファリングしてよいが、メモリ上に無制限のサンプルを蓄積しては
ならない。長距離・複数列車試験を想定し、定期的なflushと確実なcloseを行う。

### 8. 不変条件

Solは次を確認し、実装報告へ明記すること。

1. TrainDataDebuggerが `TrainControllerManager` を参照しない。
2. TrainDataDebuggerが `train.speed` と `train.targetSpeed` に代入しない。
3. TrainDataDebuggerが `approachTargetSpeed()`、ATO、TASC、Speed Limit、
   Notch、EBを呼ばない。
4. TrainDataDebuggerがMixin、Packet、HUD、クライアント専用コードを使用しない。
5. `debug.train_data.enabled = false` 時は、ログディレクトリとログファイルが
   作成されず、ファイルI/Oが発生しない。
6. ロガーのI/O障害が、列車のtickやCreateの通常走行を停止・変更しない。

### 9. Solの完了条件とユーザー手動試験

Solの完了条件は、仕様どおりのJava実装、Create 6.0.8実ソースを根拠とする静的確認、
およびGradle buildの成功に限る。SolはMinecraftを起動・操作せず、列車走行、ログ採取、
実行時受入試験を行わない。

次の項目は、実装後にユーザーがMinecraft上で手動確認する実行時試験である。結果はこの文書の
実験結果として記録し、Solの完了条件には含めない。

- Server Configに `debug.train_data.enabled` があり、初期値は `false` である。
- 無効状態で列車を走らせても、ログディレクトリ・`.log`ファイルが作成されず、
  `speed`・`targetSpeed`・Controller数に変化がない。
- 有効状態で複数列車を走らせると、列車UUIDごとに別の`.log`ファイルが作成される。
- 各ファイルは `session_start` から始まり、`schemaVersion`、tick、UUID、
  speed、targetSpeed、Create acceleration、単位を含む有効なJSON Linesである。
- Navigation destinationがGlobalStationである走行では、各`sample`に現在の進行方向に
  対応するleading TravellingPoint、destination GlobalStationのEdgePoint、符号を保持した
  `Navigation.distanceToDestination`（blocks）が記録される。
- `leadingTravellingPoint`が車体モデル・carriage順・自作の座標計算ではなく、
  Navigationが実際に使用する先頭側TravellingPointであることを、Create 6.0.8実ソースを
  根拠に確認する。
- `sample_interval_ticks = 1` では、連続する通常サンプルのserverTickが原則1ずつ
  進むことを確認する。サーバー停止・ロード境界等の例外は報告する。
- `speed`・`targetSpeed`の符号、bpt→bps変換、accelerationの単位変換が正しい。
- 有効中に無効へ切り替えた場合、以後の書き込みを停止してWriterを閉じる。
- 既存のSpeed Limit、HUD/Debug、ATO介入の動作を変更しない。
- Gradle buildが成功する。

### 10. Solへの確認事項と報告形式

Solは実装前に、次をCreate 6.0.8・Forge 47.4.10の実ソースまたは現行コードで
確認すること。

- Create列車tickの後に受動的に値を読むための適切なForgeイベントとphase。
- `Train.acceleration()`、`speed`、`targetSpeed`の現在の型・単位・安全な読み取り方。
- Navigationが進行方向に応じて採用するleading TravellingPointの正確な取得経路。
- Navigation destinationのGlobalStationと、その線路上EdgePointの正確な取得経路。
- `Navigation.distanceToDestination`の型・単位・符号の意味、およびdestinationなしの場合の値。
- Server Configの再読込時に設定値が反映される既存の振る舞い。
- ゲーム実行ディレクトリ配下に安全にログを作成するForge標準の方法。

実装後のSol報告には、変更ファイル、設定キー、想定出力パス、ビルド結果、ソースレベルの
不変条件確認、観測時点に関するCreate実ソース上の根拠、未解決事項を含める。実際の
サンプルログと走行結果は、ユーザー手動試験後にTerraがこの文書へ記録する。

### 11. 実装開始の扱い

この文書はSolが実装判断に使う仕様書である。TrainDataDebuggerは制御値を変更しない
観測機能だが、Java実装を開始する前に、ユーザーがこの個別タスクをSolへ依頼する
ことを明示する。Phase 5AのBrakingCurve・TASC・Notch・EBの実装開始を、この
ロガー仕様だけで許可するものではない。

### 12. ベースライン結果：2026-09-09 駅間走行

#### 原本

```text
docs/reference/logs/09-09_3/
└─ [1.0mpss, 160mps, 80mps]Station B to A_train-7887e07c-c2d9-456d-8483-d599ce89a08e-20260909T061150.629Z copy.log
```

原本は無加工で保持する。4680行（`session_start`、4678個の`sample`、
`session_end`）、serverTick 181〜4858であり、JSONとして不正な行およびtick欠番は
確認されていない。sample intervalは1 tickである。

#### 測定条件

- CATのATO等の制御は無効
- Create acceleration設定：1.0 blocks/s²
- Create最高速度設定：160 blocks/s
- カーブ最高速度設定：80 blocks/s
- 対象：1駅間、約5000 blocks、カーブを含む循環線

#### 観測結果

| 観測 | 結果 |
| --- | --- |
| 発車要求 | tick 728、targetSpeed 160 blocks/s |
| 制動要求 | tick 2140、速度70.55 blocks/sでtargetSpeed 0 blocks/s |
| 実測最高速度 | 70.6 blocks/s。160 blocks/sには到達しなかった。 |
| 通常加速・減速 | 約±1.0 blocks/s²。設定値と一致。 |
| 完全停止 | tick 3488。制動開始から67.4 seconds後。 |
| 停止直前の特例 | tick 3487の3.20 blocks/sからtick 3488の0.0 blocks/sへ変化。通常の-1.0 blocks/s²ではない。 |
| Navigation状態 | 発車・制動中はdestination available、完全停止時からno destination。 |

この走行は、約5000 blocksの駅間でCreateが最高速度へ到達する前に、理論的な
通常制動距離を見込んでtargetSpeedを0へ切り替える挙動と整合する。また、停止直前の
直接的な速度補正を毎tick精度で確認した。CATの将来TASC/低速制御は、このCreate標準の
補正へ依存せず、競合も避ける設計が必要である。

#### Navigationデータの完全性と追加測定

destinationがavailableである2760個のsampleすべてに、次の値が記録されている。

- `navigation.leadingTravellingPoint.railPosition`
- `navigation.destination`（GlobalStationと線路上EdgePoint）
- `navigation.distanceToDestinationBlocks`（符号付きblocks）

例えば制動開始tick 2140では、速度70.55 blocks/s、残距離2481.9508 blocksが記録され、
停止直前tick 3487では、速度3.20 blocks/s、残距離0.2368 blocksが記録されている。
完全停止tick 3488以降はdestinationがなくなるため、`destinationAvailability`は
`no_destination`となる。これは停車完了後の状態として扱う。

この原本は制動開始残距離と停止直前の位置関係を分析できる。同一条件の反復走行は
第13節で完了している。正式な制動曲線・停止精度を決めるには、さらにPhase 5Aの
ノッチ別試験を行う。

### 13. 確定ベースライン：09-09_3の11走行

`docs/reference/logs/09-09_3/`を、この時点のCreate標準走行の正本とする。すべての
ファイル名は、`[加速度, 最大速度, カーブ最大速度]`として正しい測定条件を含む。
11本すべてが毎tick記録、JSON正常、tick欠番なしである。

| 条件・方向 | 本数 | 最高速度 | 制動開始速度 | 制動開始残距離 | 停止までの時間 |
| --- | ---: | ---: | ---: | ---: | ---: |
| [1.0, 160, 80] Station A → B | 3 | 71.20 bps | 71.15 bps | 2525.369 blocks | 68.3 s |
| [1.0, 160, 80] Station B → A | 5 | 70.60 bps | 70.55 bps | 2481.951 blocks | 67.4 s |
| [1.6, 40, 30] Station A → B | 3 | 40.00 bps | 39.92 bps | 496.423 blocks | 24.1 s |

各グループ内で、制動開始速度、制動開始残距離、停止までの時間、停止直前補正の
観測値は一致した。従って、各条件・方向について最低3本というCreate標準走行の
反復基準を満たす。A→BとB→Aは駅間距離・進行方向が異なるため、統計値を混在させず、
別の基準走行として扱う。

#### 停止直前のCreate補正

| 条件・方向 | 通常減速から外れた直前速度 | 直前残距離 |
| --- | ---: | ---: |
| [1.0, 160, 80] Station A → B | 2.90 bps | 0.119 blocks |
| [1.0, 160, 80] Station B → A | 3.20 bps | 0.237 blocks |
| [1.6, 40, 30] Station A → B | 1.467 bps | 0.030 blocks |

各走行は、上表の速度・残距離から次tickで0.0 bpsへ補正された。これはCreate標準の
通常減速度とは別の、駅停止直前の補正として扱う。

反復走行によるCreate標準基準は収集済みである。次の計測は、Phase 5Aの試験用制動ノッチを
導入した後、ノッチ・初速・残距離ごとの減速度、停止距離、応答時間、停止精度を測定する。

### 14. Phase 5A初回ノッチ試験：B1とB7

#### 原本と前提

原本は `docs/reference/logs/09-09_4/` の次の二つである。ユーザー入力中の
`09-09_04` はこのディレクトリを指す表記として扱う。

| 固定ノッチ | ファイル | sample数 | セッション終了理由 |
| --- | --- | ---: | --- |
| B7 | `train-7887e07c-c2d9-456d-8483-d599ce89a08e-20260909T124658.649Z.log` | 1,903 | `server_stopping` |
| B1 | `train-7887e07c-c2d9-456d-8483-d599ce89a08e-20260909T125153.372Z.log` | 3,058 | `server_stopping` |

両ファイルはJSON Linesとして読め、`session_start`、連続sample、`session_end`を持つ。
基準加速度は両試験とも約1.600000076 blocks/s²である。

#### B1：応答モデルは確認できた

B1では、tick 1から減速要求が存在し、tick 2〜11で実効加減速度と実測加速度が
`-0.16, -0.32, …, -1.60 blocks/s²` と0.16 blocks/s²刻みで変化した。これは
10 tick線形応答とB1の暫定倍率1.0に一致する。

| 観測 | 結果 |
| --- | --- |
| 制御適用sample | 1,483 |
| `appliedNotch = B1` の制御適用sample | 1,473 |
| 安定したB1目標値 | -1.600000076 blocks/s² |
| 安定区間の通常実測減速度 | 約-1.6 blocks/s² |
| 連続した減速区間 | tick 1〜478、tick 1699〜2703 |

停止直前にはCreate標準側の直接停止補正が入り、通常のB1減速度とは別の大きな実測値が
記録される。これは09-09_3で確認済みのCreate停止補正と整合し、B1 Profileの性能値へ
混在させない。

#### 旧B7試行：有効な性能試験になっていない

B7は制御適用sampleが493あるが、`appliedNotch = B7`は0件、最大遷移進捗は7 tickである。
すなわち10 tick応答が完了せず、B7の目標値-6.400000304 blocks/s²へ一度も到達していない。
このログからB7の実測減速度・停止距離・B1との倍率関係を確定してはならない。

tick 1344からのB7減速中、7〜8 tickの`applied`後に1 tickの
`no_braking_demand`（`target_not_lower_than_current_speed`）が繰り返される。そのtickでは
ログ上の`createTargetSpeedBlocksPerSecond`が160となり、次の減速tickで遷移が0から
やり直される。例えばtick 1344〜1351の後、tick 1352で減速要求がないと判定され、
tick 1353で再び遷移0となる。B7セッションはtick 1903でサーバー停止により終了し、
停止までのデータもない。

#### 現時点の判定

| 項目 | 判定 |
| --- | --- |
| B1 Profileと10 tick線形応答 | ログ上で確認できた。 |
| B1の最終性能値 | 暫定Profileの確認段階。複数初速・反復測定は未完了。 |
| B7の10 tick応答 | 未達。問題あり。 |
| B7の性能曲線・停止距離 | このログでは評価不可。 |
| B1〜B7の比例関係 | 未検証。 |

次のB7試験は、未解決事項に記録した「減速要求の判定対象と、減速要求が一時的に見えないtickでの
応答状態保持」を設計・実装報告で解決してから行う。rawログは変更しない。

#### 新B7試行：B7到達は確認、性能確定には未使用

`new_train-7887e07c-c2d9-456d-8483-d599ce89a08e-20260909T130536.861Z.log`は、
旧B7試行とは別の新しいB7ログである。15,197 sampleのうち、制御適用sampleは5,506件、
`appliedNotch = B7`は769件である。tick 13971〜14119には149 tick連続でB7が完了状態にあり、
`transitionElapsedTicks = 10`、`effectiveAcceleration = -4.0 blocks/s²`を記録している。
この区間の基準加速度は約1.0 blocks/s²であり、B7暫定倍率4.0と一致する。

同区間のserver tick単位の実測減速度は-4.0 blocks/s²が119件、-8.0 blocks/s²が29件、
-3.0 blocks/s²が1件で、平均は約-4.77 blocks/s²だった。いずれも負値であり、B7が適用された
安定区間に列車が加速しているという記録はない。ただしProfile目標値-4.0 blocks/s²と
server tick単位の実測値が一致しないため、この平均をB7の確定性能値にしてはならない。

tick 14119は`applicationState = applied`かつ実効加減速度-4.0である一方、
ログ上の`createTargetSpeedBlocksPerSecond`は160である。次tick 14120は
`no_braking_demand`となる。この組合せは、一つのserver tick中に複数の
`approachTargetSpeed()`呼出があり、先の減速呼出でB7を適用した後、後の呼出がログの
targetSpeed観測値を160へ上書きした可能性と整合する。現行ログは一tick中の呼出回数・
各呼出のtargetSpeed・各呼出で返した加速度修飾を区別していない。

したがって、新B7試行から確認できるのは「B7 Profileと10 tick応答が少なくとも一度は
完了した」ことまでである。複数呼出を含む実効減速度、B7の継続適用、停止距離、B1との
倍率関係を確定するには、呼出単位の観測と応答状態の扱いを明確化した後の再試験が必要である。

### 15. Phase 5A最新B7試行：schemaVersion 2

#### 原本の完全性

対象は `docs/reference/logs/09-09_4/` の
`latest_train-7887e07c-c2d9-456d-8483-d599ce89a08e-20260909T133244.293Z.log` である。
schemaVersionは2、sampleはserverTick 1〜3660の3,660件で欠番はない。一方、この取得時点では
`session_end`がないため、セッション終了理由・最終結果を表す完結したログではない。

schemaVersion 2では、`preCatTargetSpeedBlocksPerSecond`と
`finalTargetSpeedBlocksPerSecond`が分離された。制御適用401件はいずれも
`finalTargetSpeedBlocksPerSecond = 0`であり、ATO前の値ではなく最終値を用いる減速要求判定は
機能している。

#### B7応答と実測

Profile目標は基準加速度約1.0 blocks/s²に対するB7倍率4.0、すなわち-4.0 blocks/s²である。
制御適用401件のうち、B7完了状態（`appliedNotch = B7`、遷移10 tick）は391件である。

最初の連続B7完了区間はtick 2546〜2787の242 sampleで、速度75.19 bpsから17.39 bps、
残距離709.181 blocksから153.064 blocksまで減速した。この区間ではProfile・実効値は
-4.0 blocks/s²で一定だったが、server tick単位の実測減速度は-4.0が194件、-8.0が48件、
平均約-4.79339 blocks/s²であった。tick 3128にはCreate停止補正とみられる-15.8 blocks/s²が
あり、Profile性能値から除外する。

#### 次段階へ進めない理由

`transitionElapsedTicks`はtick 2531で1、2534で2、2539で3、2546で10となっている。
したがって、応答は実時間の連続10 server tickではなく、減速要求を受けた
`approachTargetSpeed()`呼出の回数に応じて進んでいる。これは本仕様の「10 tick応答」を
性能測定で評価できる状態ではない。

また、目標・実効値が-4.0でも実測値が-8.0となるtickが存在する。現行実装は一server tickに
一度だけ応答状態を進める一方、同一tick内の複数`approachTargetSpeed()`呼出へ同じ修飾値を
返し得る。ログは呼出単位の記録を持たないため、-8.0が複数適用によるものか、Create側の
別処理によるものかを、このログだけで確定できない。

このため、現在の結果は「ATO後の減速要求判定とB7到達を確認できた」という段階に留まる。
B1〜B7の性能テーブル、速度依存曲線、停止距離、B1:B7倍率を決める次段階へは進まない。
先に呼出単位の観測を追加し、ユーザー手動試験で応答時間と実測減速度の対応を再確認する。

### 16. Phase 5A改善後B7試行：schemaVersion 3

#### 原本と完全性

対象は `docs/reference/logs/09-09_4/改善後のログ（v3）.log` である。schemaVersionは3、
sampleはserverTick 1〜3042の3,042件で欠番・重複がなく、`session_start`と
`session_end`（`server_stopping`）を持つ。固定ノッチはB7、基準加速度は約
1.0 blocks/s²、Profile目標は-4.0 blocks/s²である。

schemaVersion 3はserver tickごとの全`approachTargetSpeed()`呼出と、各呼出における
pre-CAT/final target、減速要求、返却した修飾値、ノッチ修飾の有無を記録している。
`measuredAccelerationBlocksPerSecondSquared`は一呼出の値ではなく、そのserver tick全体の
速度差であることもsession_startに明記されている。

#### 呼出回数とB7適用の対応

| 観測 | 結果 |
| --- | ---: |
| 全sample | 3,042 |
| `approachCallCount = 1` | 2,978 tick |
| `approachCallCount = 0` | 64 tick |
| `approachCallCount > 1` | 0 tick |
| B7減速要求・ノッチ修飾あり | 547 tick |
| ノッチ修飾ありの一tick当たり呼出数 | 常に1回 |
| 修飾あり、実効値-4.0のsample | 537 tick |

修飾が入った547 tickでは、応答開始の0.0から-0.4、-0.8、…、-4.0 blocks/s²までを除き、
実測減速度は常に実効値と一致した。schemaVersion 2で見えた-8.0 blocks/s²は、このV3走行には
存在しない。したがって、この走行に限れば、`approachTargetSpeed()`境界で一回だけ返した
加速度修飾は、Createの通常速度追従へ意図どおり反映できている。複数呼出による二重適用は
この原本では起きていない。

#### 試験条件の混在：ATO速度制限がB7を中断する

しかし、B7制御の547 tickとは別に、同じ減速区間で810 tickは`approachTargetSpeed()`が一回
呼ばれたにもかかわらず、減速要求なしとなった。この全tickでログは次を示す。

```text
pre-CAT target = 160 blocks/s
final target   = 100 blocks/s
applicationReason = target_not_lower_than_current_speed
measuredAcceleration = +1.0 blocks/s²
```

対してB7が入るtickは`pre-CAT target = final target = 0 blocks/s`である。例えばB7遷移中、
serverTick 1641〜1647は連続して減速するが、1648は目標100 blocks/sへ戻って+1.0 blocks/s²で
再加速する。以後も0目標の減速tickと100目標の加速tickが交互に現れる。B7が定常値へ到達しても、
この再加速が継続する。

この走行ではATO設定が有効であり、既定の20 blocks/s制限を手動で100 blocks/sへ変更していた。
従って160→100は、CATが過去のtargetSpeedを不正に再適用した証拠ではなく、ATO速度制限が
Createの160 blocks/s要求へ適用された結果である。この原本からtargetSpeedの所有権競合を結論づけては
ならない。

ただしPhase 5Aの固定ノッチ性能試験としては、ATOの速度制限とB7を同時に有効にした条件である。
0 blocks/sを要求するtickだけB7が効き、100 blocks/sを要求するtickでは意図どおり加速するため、
B7の実際の平均減速性能、停止距離、B1:B7比をこのログから確定してはならない。次の性能測定は
ATOおよび速度制限を無効にし、Create標準の減速要求だけに固定ノッチを適用する条件で行う。

#### 10 tick応答との関係

応答は最初のB7適用tick 1641から、遷移進捗10となる1655まで、実時間では14 server tickを
要した。1648、1650、1652、1654には減速要求がなく、現行実装が応答状態を進めないためである。
これはログの`transitionTimebase`が「ノッチ修飾された呼出を含むserver tick一回につき一段」と
明記していることと一致する。

この14 tickは、ATO速度制限によりB7減速要求が中断された試験条件下での観測値である。
現行仕様の「10 tick線形遷移」が実時間の連続10 server tickを指すか、実際に減速要求が継続する
10 control tickを指すかは、ATOを無効にした再試験で切り分ける。再試験でも連続した減速要求下で
10 server tickと一致しなければ、時間基準を実装上の未解決事項として扱う。

### 17. Phase 5A分離条件B7再測定：schemaVersion 3

#### 試験条件

原本は `docs/reference/logs/09-09_4/分離条件再測定.log` である。ユーザー申告の条件は次のとおり。

```text
fixedNotch=B7
ato=false
speedLimit=false
tasc=false
eb=false
baseAcceleration=1.0 blocks/s²
maxSpeed=160 blocks/s
curveMaxSpeed=80 blocks/s
route=A to B
```

schemaVersionは3、sampleはserverTick 1〜3733の3,733件で欠番・重複がなく、
`session_end`の理由は`server_stopping`である。ATOおよびCAT速度制限を無効にしたため、
この原本にある`finalTargetSpeedBlocksPerSecond`はCATによる速度制限後の値ではない。

#### B7が実際に適用されたtick

| 観測 | 結果 |
| --- | ---: |
| `applicationState = applied` | 569 tick |
| ノッチ修飾あり | 569 tick |
| B7到達（遷移10、実効-4.0 blocks/s²） | 559 tick |
| B7適用tickの実測-4.0 blocks/s² | 559 tick |
| 遷移tickの実測値 | 0、-0.4、-0.8、…、-3.6 blocks/s²を各1 tick |

従って、B7が**実際に適用された一回の`approachTargetSpeed()`呼出**については、暫定仕様どおり
基準加速度1.0 blocks/s²の4.0倍、すなわち-4.0 blocks/s²が返され、server tick実測値も一致している。
ノッチ自動選択が未実装であることは、この固定B7の-4.0 blocks/s²を-1.0 blocks/s²にする理由ではない。

#### 見かけの平均減速度が約-1.0 blocks/s²となる理由

制動開始から最後のB7適用まで（serverTick 1498〜2909、1,412 tick）、速度は71.20から
0.65 blocks/sへ変化し、server tick実測加速度の平均は-0.9993 blocks/s²だった。この値だけを見ると、
B7であっても通常加速度相当の減速度しか出ていないように見える。

しかし同じ区間には、B7補正が実際に入った569 tickとは別に、843 tickの次の状態がある。

```text
pre-CAT target = 160 blocks/s
final target   = 160 blocks/s
applicationState = no_braking_demand
applicationReason = target_not_lower_than_current_speed
notchModifierAppliedCallCount = 0
measuredAcceleration = +1.0 blocks/s²
```

例えばB7完了後、tick 1512はB7修飾あり・実測-4.0 blocks/s²だが、tick 1513は
target 160 blocks/s・修飾なし・実測+1.0 blocks/s²である。この加速tickが繰り返されるため、
-4.0 blocks/s²のB7適用値と+1.0 blocks/s²の通常加速値の時間平均が約-1.0 blocks/s²となる。

また`appliedNotch = B7`は最後に到達した応答状態を保持する表示値であり、そのtickにB7修飾が
返されたことを単独では意味しない。この原本では`appliedNotch = B7`が2,222 tickある一方、
実際にノッチ修飾を返したのは569 tickだけである。実際のB7適用判定には
`applicationState = applied`および`notchModifierAppliedCallCount = 1`を用いる。

#### 現時点の判定

ユーザー確認により、この走行は全区間でCreate自動運転であり、手動運転は原因から除外された。
また残距離はtick 1498の2525.367 blocksからtick 2909の0.021 blocksまで単調に減少している。
Create 6.0.8のNavigationは毎tick、`speed² / (2 * train.acceleration())`で標準制動距離を計算し、
残距離より大きければtarget 0、小さければ最高速度を出す。ここで使われる加速度は基準
1.0 blocks/s²であり、CATが実際に加えたB7の-4.0 blocks/s²ではない。

例えばtick 1505のB7適用後は、B7により速度がCreate標準想定より速く下がるため、次のtick 1506で
Navigationは残距離2497.00 blocksに対して再加速可能と判定し、target 160 blocks/sを出す。+1.0の
加速後には再び標準制動距離が残距離を上回り、tick 1507でtarget 0とB7適用へ戻る。この
Create標準制動モデルと強い外部制動のフィードバック往復が、B7-4.0と通常加速+1.0の混在、ならびに
区間平均-0.9993 blocks/s²を生んでいる。

B7の一回当たり性能値-4.0 blocks/s²は確認できた。一方、Create標準Navigationへ外部制動だけを
追加する現在のPhase 5A条件では、連続B7の継続性能・停止距離・実時間応答を測定できない。これらを
測定するには、試験専用の持続的な減速要求を明示的に設けるか、将来のBrakingCurve/TargetSpeedResolverが
Createの標準制動距離に代わる最終targetを出す段階まで待つかを、要件として決定する必要がある。

### 18. Phase 5A停止目標保持B7試験：schemaVersion 4

#### 原本と完全性

原本は `docs/reference/logs/09-09_4/B7ブレーキ後速度0までずっと停止.log` である。schemaVersionは4、
sampleはserverTick 32〜1176の1,145件で欠番・重複がなく、`session_end`は
`server_stopping`で完結している。

`session_start`は`FIXED_FOR_TEST`、固定B7、`holdStopTargetUntilStop = true`を記録する。各
`approachCalls[]`にはnative/final target、停止目標保持状態、上書き有無、初回ラッチ理由が記録され、
schemaVersion 4の要求を概ね満たす。

#### 連続B7の性能確認

| 観測 | 結果 |
| --- | ---: |
| 初回ラッチ | tick 713、native target 0、残距離 2361.783 blocks |
| 停止目標保持付き速度追従呼出 | 350 tick |
| 実際のノッチ修飾あり | 350 tick |
| 10 tick遷移 | tick 713の0.0からtick 723の-4.0 blocks/s²まで、欠番なし |
| 定常B7（実効・実測とも-4.0） | 339 tick |
| 完全停止 | tick 1062、速度0.0 blocks/s |

初回ラッチ以後、native targetが160 blocks/sへ戻った呼出でもfinal targetは0、
`stopTargetHoldState = LATCHED`、`stopTargetHoldOverrideApplied = true`であり、B7修飾が
連続して返された。従って停止目標保持は、Create Navigationの再加速要求を遮断し、固定B7の
10 tick遷移と定常-4.0 blocks/s²を連続測定できる状態にした。

tick 1062の実効値は-4.0 blocks/s²、実測値は-2.0 blocks/s²である。これは速度0.10 blocks/sから
0へclampされた最終tickであり、Profile性能値には混在させない。

#### 停止位置と保持解除

完全停止時もNavigation destinationは存在し、残距離は1756.383 blocksだった。初回ラッチから
停止までの移動距離は約605.400 blocksである。これはCreateが基準1.0 blocks/s²で開始した標準制動
位置に、B7の-4.0 blocks/s²を固定適用した結果であり、駅停止位置を目標とする製品版の結果ではない。

次tick 1063には保持状態が`INACTIVE`となり、native/final target 160 blocks/s、実測+1.0 blocks/s²で
再加速を始めた。現行試験治具の「速度0で保持解除」という動作自体には一致するが、ログ仕様で要求した
`RELEASED`状態は出力されず、`LATCHED`から`INACTIVE`へ直接遷移している。

#### 現時点の判定

| 項目 | 判定 |
| --- | --- |
| 停止目標保持による連続B7 | 合格。 |
| B7の10 tick線形遷移 | 合格。実時間の連続10 server tickで確認。 |
| B7定常減速度 | 合格。-4.0 blocks/s²を339 tick連続で確認。 |
| 停止距離の測定 | 取得可能。開始残距離と停止残距離を用い、約605.400 blocks。 |
| 駅停止精度 | 対象外。1756.383 blocks手前で停止する試験治具の想定どおり。 |
| 保持解除状態のログ | 要修正。`RELEASED`の観測可能な記録がない。 |

この原本により、B7単独の初期Profile性能を測定するPhase 5A試験治具は成立した。次はB1〜B7を
同一の保持条件で複数回測定し、ノッチごとの減速度・停止距離・応答時間を表へ集計する。

#### RELEASED状態の修正確認

後続原本 `docs/reference/logs/09-09_4/RELEASEDあり.log` は、途中で停止目標保持の記録が失われ、
`RELEASED`が出力されなかった処理をユーザーが修正した後のschemaVersion 4ログである。sampleは
serverTick 1〜1350の1,350件で欠番・重複がなく、`session_end = server_stopping`を持つ。

このログでは、停止目標保持の解除が二回発生し、いずれも一sampleだけ`RELEASED`が観測された後に
`INACTIVE`へ遷移した。

| 制動サイクル | LATCHED開始 | 速度0到達 | RELEASED | 次sampleの状態 | 停止時残距離 |
| --- | ---: | ---: | ---: | --- | ---: |
| 1回目 | tick 608 | tick 745 | tick 746 | tick 747で`INACTIVE` | 252.160 blocks |
| 2回目 | tick 1064 | tick 1143 | tick 1144 | tick 1145で`INACTIVE` | 94.152 blocks |

各`RELEASED` sampleでは、native/final targetは160 blocks/s、保持上書きは`false`、
`applicationState = no_braking_demand`であり、次tickからCreate標準の再加速へ復帰している。
これは試験治具の「速度0で保持解除」という仕様と整合する。`stopTargetHoldTriggerReason`が空なのは
初回ラッチ理由だけを記すフィールド定義と整合するため、解除理由の追加記録は要求しない。

よって停止目標保持の解除状態に関する観測性要件は合格とする。このログは同一セッション中に
二回の制動・解除サイクルを含むため、単一B7の停止距離性能表へ混在させず、状態遷移の確認原本として
扱う。

#### Phase 5A固定ノッチの測定区間

固定ノッチの性能測定では、A駅からB駅へ向かうCreate自動運転を開始してよい。ただし評価対象は
「B駅へ到着・停車するまで」ではない。停止目標保持が有効な場合、Create標準の初回停止要求地点で
強い固定Bノッチがラッチされ、B駅より大幅に手前で一度停止するためである。

一回の有効な測定区間は、同一列車・同一セッション内の次の範囲とする。

```text
開始: 最初の stopTargetHoldState = LATCHED
終了: そのラッチに対応する最初の stopTargetHoldState = RELEASED
```

この範囲から、開始速度・開始残距離・10 tick応答・定常実測減速度・停止速度・停止残距離・移動距離を
集計する。`RELEASED`後に列車が再加速し、次のラッチが起きた場合、それは別サイクルであり、同一の
ノッチ性能サンプルへ混在させない。

測定を簡潔にするため、ユーザーは最初の`RELEASED`を確認した後にワールド/サーバーを終了してよい。
ただし`session_end`を持つ完結ログとして保存すること。各ノッチ・各測定条件は最低3回反復し、
一回のラッチ・解除サイクルを一つの表の行として扱う。B駅への実際の停車精度は、BrakingCurve、
TargetSpeedResolver、ノッチ自動選択、TASCを統合する後続段階で測定する。
