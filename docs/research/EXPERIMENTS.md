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

### 9. 受入条件

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

実装後の報告には、変更ファイル、設定キー、出力パス、実際のサンプルログ、
ビルド結果、受入条件ごとの確認結果、観測時点に関する根拠、未解決事項を含める。

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
