# Create 6.0.8および関連アドオンの時刻表命令・時刻条件調査

調査日：2026-09-23
調査種別：ローカルソースの静的調査（Minecraft起動・ビルド・実機検証なし）

## 0. この報告書の位置付け

本書は、CATの新規再設計で時刻表対応を決めるための調査報告である。過去のCAT仕様・旧実装は要件として使用していない。

記述は次の4種類を区別する。

- **確認できた標準動作**：手元のCreate 6.0.8ソースから確認できた事実。
- **CATの確定方針**：利用者が今回までに承認したCATの要件。
- **整合のための提案**：実装・仕様の候補であり、未承認。
- **要決定**：利用者の判断が必要な事項。

`docs/organize/整理3.md`は参照だけを行い、本調査では変更していない。

## 1. 調査対象・版・不足ソース

### 1.1 実際に確認したもの

| 対象 | 確認状態 | 確認した版・根拠 |
| --- | --- | --- |
| Create | ソース確認済み | Minecraft 1.20.1、Create 6.0.8。`vendor/Create-mc1.20.1-6.0.8/gradle.properties:9-13` |
| Create: Railways Navigator | ソース不足 | プロジェクト、`vendor/`、実行用`run/mods/`、ローカル依存キャッシュに対象ソース／JARを確認できなかった |
| Create: Steam 'n' Rails | ソース不足 | 同上。過去ワールドに`railways-server.toml`等の痕跡はあるが、版・登録項目・実装の根拠には使用できない |

Createのvendorスナップショットには`.git`メタデータがないため、コミットIDまでは現在のファイルから確認できない。本書の行番号は、上記ローカルスナップショットに対するものである。

### 1.2 追加で必要な提供物

関連アドオンについて完全な項目一覧と動作を確定するには、次を`vendor/`へ提供する必要がある。

1. CATが対応対象とする正確な版のCreate: Railways Navigatorソース（展開済みソース、ソースリポジトリのスナップショット、または`-sources.jar`）。
2. CATが対応対象とする正確な版のCreate: Steam 'n' Railsソース（同上）。
3. 各版がMinecraft 1.20.1／Create 6.0.8向けであることを確認できる版情報ファイル。

通常の実行JARだけでも登録IDやクラス名を部分的に調べられる可能性はあるが、今回要求されたメソッド・該当行を伴う報告にはソースが望ましい。ソースが提供されるまでは、両アドオンが時刻表項目を追加するか、既存項目だけを変更するかも未確認とする。

### 1.3 現行の`整理3.md`との差

`整理3.md:439-440,462,950,1036`では、全対応命令・時刻系が未決定と記載されている。今回の会話では次が新たに確定しており、将来の仕様反映時にこの記述を更新する必要がある。

- Create標準で時刻表GUIから入力できる命令・待機条件へ確実に対応する。
- Create: Railways NavigatorおよびCreate: Steam 'n' Railsが追加する時刻表命令・待機条件にも対応する。
- 時刻指定はMinecraftのワールド内時刻を基準とし、現実の時計を使用しない。

ただし「対応」は、CATが標準のフィールドと意味を認識する要件であり、Createの速度代入や発車処理を安全制約より優先してそのまま実行する決定ではない。

## 2. 最優先結論：指定時刻を過ぎて到着した場合

### 2.1 結論

**確認できた標準動作**：Create 6.0.8の`create:time_of_day`は「指定時刻以降なら常に成立」ではない。現在のワールド時刻が、指定した周期内の目標tickから**0～40tick後**にあるときだけ成立する。40tickを越えて到着すると、その回は未達成のままで、次の周期の同じ位相まで待つ。

根拠は`TimeOfDayCondition.tickCompletion()`で、`level.getDayTime()`を周期で剰余し、`diff >= 0 && diff <= 40`を返す処理である。
参照：`vendor/Create-mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/schedule/condition/TimeOfDayCondition.java:37-45`

この40tickは通常速度なら現実時間で約2秒、Minecraft表示時刻では約2.4分に相当する。これは猶予窓であり、「到着済みなら過去の指定時刻を達成済みとみなす」機能ではない。

### 2.2 8:00指定の具体例

次の例は、周期を**24時間ごと**に設定し、昼夜サイクルが通常進行し、時刻条件が現在評価対象になっていることを前提とする。Minecraftでは`dayTime=0`が6:00相当なので、8:00は`dayTime=2000`となる。

| 到着時刻 | おおよその`dayTime` | 標準Create 6.0.8の結果 |
| --- | ---: | --- |
| 7:50 | 1833 | まだ成立しない。8:00の窓まで待つ |
| 8:00 | 2000 | 次の条件評価で成立する。処理遅延用の40tick窓がある |
| 8:05 | 2083 | 40tick窓を越えているため成立しない。次の日の8:00を待つ |

8:00の成立窓は概算で8:00～8:02.4である。8:05は約83tick後なので窓外となる。

### 2.3 GUIで指定できる値

**確認できた標準動作**：

- 時：0～23。Shift操作の増減幅は6時間。
- 分：0～59。Shift操作の増減幅は15分。
- 周期：24時間、12時間、6時間、4時間、3時間、2時間、1時間、45分、30分、15分。
- コンストラクタ既定値：8:00、周期インデックス5＝2時間ごと。分はNBTの未設定値0として扱われる。

参照：`TimeOfDayCondition.java:31-33,48-61,113-163`

周期は単なる表示ではない。目標tickと現在時刻の両方を周期で剰余するため、例えば「8:00・2時間ごと」は8:00だけでなく、同じ位相に当たる6:00、10:00、12:00等にも成立窓を持つ。

### 2.4 時刻の取得元と表示時刻

**確認できた標準動作**：判定値は`Level.getDayTime()`であり、時刻→tick変換は次式である。

```text
targetTicks = ((((hour + 18) % 24) * 1000)
               + ceil(minute / 60 * 1000)) % rotationTicks
dayTime     = level.getDayTime() % rotationTicks
成立        = 0 <= dayTime - targetTicks <= 40
```

`+18`はMinecraftの`dayTime=0`（6:00）と画面の24時間表記を対応させるための変換である。待機表示も同じ変換を使い、次の成立時刻を算出する。
参照：`TimeOfDayCondition.java:37-45,169-186`

Createの列車群は`GlobalRailwayManager`からオーバーワールドtick時に更新され、そこで`Train.tick(level)`→`ScheduleRuntime.tick(level)`が呼ばれる。したがって、この実装経路では時刻条件に渡る`Level`はオーバーワールドである。
参照：`GlobalRailwayManager.java:190-203,219-228`、`Train.java:264-284`

### 2.5 一度成立した条件の保持

**確認できた標準動作**：時刻条件そのものには「過去に時刻を通過した」というラッチはない。しかし、条件が成立したtickで、その条件列の`conditionProgress`が次へ進む。以後は同じ条件を再評価しないので、**実際に評価されて成立した後は達成済みとして保持される**。

参照：`ScheduleRuntime.tickConditions()`、`ScheduleRuntime.java:153-184`

重要な違いは次のとおり。

- 時刻条件が現在の評価対象で、成立窓中に評価された：達成済みとして保持する。
- 同じAND列の手前に未達成条件があり、時刻条件まで進んでいなかった：時刻を通過しても記録されず、次周期を待つ。
- ランタイム全体が一時停止中だった：条件を評価しないため、その間に窓を通過すると標準動作では達成されない。

### 2.6 複数条件のAND／OR

**確認できた標準動作**：`ScheduleEntry.conditions`は二重リストで保存される。GUIの縦方向に追加した条件は同じ列で順番に評価されるAND、横方向の「いずれか条件」は代替列のORである。

- 同じ列：現在の条件が成立すると次の条件へ進む。全条件の成立が必要（順次AND）。
- 別の列：どれか1列が最後まで進むと待機全体を終了（OR）。
- 各OR列は同じtickで並行して現在条件を評価する。

参照：`ScheduleEntry.java:14-16,26-45`、`ScheduleRuntime.java:153-184`、`ScheduleScreen.java:767-835`

したがって「8:00になり、かつ貨物搬入が終わるまで待つ」なら、時刻条件と貨物条件を同じ列へ置く必要がある。ただし順序が意味を持つ。貨物条件→時刻条件の順で貨物完了が8:05なら、8:00は未評価のまま過ぎており次周期待ちになる。時刻条件→貨物条件なら8:00の成立を保持してから貨物を待てる。

### 2.7 時刻変更・睡眠・昼夜停止

以下はイベント専用処理がないことと、毎回`getDayTime()`の現在値だけを判定することから導ける**静的コード上の帰結**であり、今回Minecraft内では検証していない。

- `/time set`等で前方へ飛ばし、目標+40tickを越えた位置へ着地すると、その周期を逃す。
- 目標から40tick以内へ着地すれば、次の評価で成立できる。
- 時刻を戻した場合、履歴は見ず、再び目標窓へ入った時に成立する。すでに`conditionProgress`が進んだ条件は再評価されない。
- 睡眠による朝への時刻移動も特別扱いされない。移動後の時刻から次の窓を待つ。
- `doDaylightCycle=false`で窓外に停止すると永続的に成立しない。窓内で停止しており条件が評価対象なら成立する。
- サーバー停止中はランタイムtickもワールド時刻も通常は進まず、現実の経過時間は加算されない。再開後の保存ワールド時刻で再評価する。

### 2.8 一時停止・保存・未読込

**確認できた標準動作**：

- `ScheduleRuntime.paused`中はランタイム処理全体が即時returnし、時間待機のカウンタも時刻条件も評価しない（`ScheduleRuntime.java:102-107`）。ワールド時刻そのものは別に進む。
- 現在項目、PRE／IN／POST_TRANSIT状態、各OR列の進捗、条件context、paused/completedは列車NBTへ保存・再読込される（`ScheduleRuntime.java:381-415`、`Train.java:1176,1220`）。
- 列車オブジェクトはチャンク内の車両Entityだけに依存せず、`GlobalRailwayManager`の待機列車／走行列車リストからtickされる。そのため、グラフが有効な通常列車では車両Entityが未読込でも時刻条件は進行し得る。駅チャンク未読込を検知する専用条件も存在する。
- ただし`Train.graph == null`なら`Train.tick()`はランタイム更新より前にreturnする（`Train.java:267-270`）。異常なグラフ欠損まで「常に進む」とは言えない。

時刻表アイテムを列車から取り出す標準処理は、現在項目を`savedProgress`へ保存するだけで、各条件の途中contextや列内進捗をアイテムへ移さない。再適用時はランタイムをリセットする。
参照：`ScheduleRuntime.java:199-208,418-427`、`Schedule.java:84-100`

## 3. Create 6.0.8の登録一覧

### 3.1 一覧の完全性

**確認できた標準動作**：Create 6.0.8は`Schedule.INSTRUCTION_TYPES`と`Schedule.CONDITION_TYPES`をGUI選択肢とNBT復元の正本にしている。標準登録は`Schedule`のstatic初期化に全件が並んでおり、命令5種・待機条件9種である。

参照：

- 登録：`vendor/Create-mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/schedule/Schedule.java:35-63`
- GUI選択肢：同`Schedule.java:65-71`、`ScheduleScreen.java:201-244`
- 日本語画面名：`vendor/Create-mc1.20.1-6.0.8/src/main/resources/assets/create/lang/ja_jp.json:2698-2786`

関連アドオンはこれらの公開Listへ項目を加える、Mixin等で登録処理を変更する、または独自GUIを使う可能性がある。しかしソースがないため、登録方式を推測で確定しない。

### 3.2 標準命令（5種）

| 登録ID／画面名 | 実装クラス | 入力・基本動作 | 完了・条件・依存 | CATでの主な接続論点 |
| --- | --- | --- | --- | --- |
| `create:destination`／駅へ移動 | `DestinationInstruction` | 駅名フィルター。`*`は最大3個。全駅から名前一致候補を集め、経路探索で候補を選ぶ | 経路を返して走行開始。条件対応。運転士席が必要。候補なし／経路なしは状態表示後に再試行 | CATの駅名・ワイルドカード仕様、進路許可、候補順位、安全制御と統合 |
| `create:package_delivery`／小包を配送 | `DeliverPackagesInstruction` | 列車内の小包の宛先と接続郵便箱アドレスが合う駅を収集して経路探索 | 条件対応。小包がなければ項目をスキップ。小包はあるが宛先駅なしなら待機・再試行。郵便箱／Package Portと車上貨物に依存 | CAT経路保護を通す。配送そのものと「配送可能駅へ移動」を分けて扱う |
| `create:package_retrieval`／小包を回収 | `FetchPackagesInstruction` | 宛先フィルター（空欄は`*`、`*`最大3個）。駅の接続郵便箱／offline bufferにある未配送小包を探索 | 条件対応。該当小包なしなら項目をスキップ。経路なしは再試行。郵便箱／Package Portに依存 | 未読込郵便箱bufferを含む標準探索を保持しつつ、CAT進路・再開承認と統合 |
| `create:rename`／時刻表名の変更 | `ChangeTitleInstruction` | 文字列を現在の時刻表表示タイトルへ即時設定 | 条件非対応。経路・速度を変えず、その場で次項目へ進む | 列車名変更ではなく案内用の運行タイトル。ATOS／HUD表示との正本を決める |
| `create:throttle`／最大速度を制限する | `ChangeThrottleInstruction` | 5～100%、5%刻み（Shiftは25%）。`train.throttle`へ直接代入 | 条件非対応。その場で完了し次項目へ。Navigationは`maxSpeed * throttle`を上限へ使う | Create直接速度制御はCAT方針と競合。INTEROS上の制限入力へ変換する意味・優先順位を決める |

主な根拠：

- 駅移動：`DestinationInstruction.java:32-46,54-76,80-111`
- 小包配送：`DeliverPackagesInstruction.java:33-69,75-124`
- 小包回収：`FetchPackagesInstruction.java:39-100,105-162`
- 表示名：`ChangeTitleInstruction.java:22-45,61-67`
- スロットル：`ChangeThrottleInstruction.java:26-77,91-97`
- 標準Navigationによる反映：`Navigation.java:263-278`

#### 「時刻表名の変更」の作用範囲

`ChangeTitleInstruction.start()`が変更するのは`ScheduleRuntime.currentTitle`であり、`Train.name`ではない。列車そのものの名前変更とは区別が必要である。

#### スロットルの作用範囲

Create標準では`ChangeThrottleInstruction.start()`が`runtime.train.throttle`を5～100%の値で更新し、Navigationが通常最高速度へ乗算する。値は`Train` NBTの`Throttle`として保存される。
参照：`ChangeThrottleInstruction.java:68-77,91-97`、`Navigation.java:263-278`、`Train.java:1197-1201`

### 3.3 標準待機条件（9種）

| 登録ID／画面名 | 実装クラス | 入力・基本動作 | 成立条件・依存 | 保存中の主な状態 |
| --- | --- | --- | --- | --- |
| `create:delay`／待機する | `ScheduledDelay` | 値0～120、単位tick／秒／分。既定5秒 | contextの`Time`を評価tickごとに+1し、指定tick以上で成立 | `Time`、表示更新版 |
| `create:time_of_day`／指定時刻 | `TimeOfDayCondition` | 時0～23、分0～59、周期24h～15分 | ワールド`dayTime`が周期内目標tick～+40tickで成立 | 条件自体の履歴contextなし。成立後の列進捗はRuntime側で保存 |
| `create:fluid_threshold`／液体貨物の状態 | `FluidThresholdCondition` | 液体／フィルター、比較`>`・`<`・`=`、バケツ数 | 全車両fluid handlerの一致量（mB）を合計し、入力バケツ数×1000と比較 | storage version、最新表示量、lazy tick用Time |
| `create:item_threshold`／アイテム貨物の状態 | `ItemThresholdCondition` | アイテム／フィルター、比較`>`・`<`・`=`、個数またはスタック数 | 全車両item handlerの一致量を比較。「スタック」は満杯スロットだけを1として数える | storage version、最新表示量、lazy tick用Time |
| `create:redstone_link`／レッドストーンリンク | `RedstoneLinkCondition` | 2個の周波数アイテム、powered／unpowered | Createのグローバルリンク網で、読込済み送信元の電力有無が指定状態と一致 | 最後に確認したglobal power version |
| `create:player_count`／座っているプレイヤー数 | `PlayerPassengerCondition` | 0～20人、ちょうど／以上 | `train.countPlayerPassengers()`の現在値が条件一致 | 前回人数、表示更新版 |
| `create:idle`／貨物のやりとりが停止しているなら | `IdleCargoCondition` | 値0～120、単位tick／秒／分 | 全車両の「最後の貨物交換からのtick」の最小値が指定時間を**超える** | 表示用Time。実時間源は各Carriage storage側 |
| `create:unloaded`／チャンクロードが解除されたら | `StationUnloadedCondition` | なし | 現在駅のディメンションが存在し、駅BE位置がentity-tickingでなくなったら成立 | 固有contextなし |
| `create:powered`／駅が赤石信号を受けたら | `StationPoweredCondition` | なし | 現在駅のディメンション・駅位置が読込済みで、駅位置に隣接レッドストーン信号がある | 固有contextなし |

主な根拠：

- 経過待機と単位：`ScheduledDelay.java:14-35`、`TimedWaitCondition.java:20-56,92-107`
- 時刻：`TimeOfDayCondition.java:29-61,111-187`
- 貨物共通比較と20tick単位の遅延評価：`CargoThresholdCondition.java:22-68,92-130`、`LazyTickedScheduleCondition.java:8-28`
- アイテム：`ItemThresholdCondition.java:25-74,106-123`
- 液体：`FluidThresholdCondition.java:27-71,98-120`
- レッドストーンリンク：`RedstoneLinkCondition.java:26-43,70-119`
- 乗客：`PlayerPassengerCondition.java:35-78`
- 貨物アイドル：`IdleCargoCondition.java:15-34`
- 駅未読込：`StationUnloadedCondition.java:20-49`
- 駅受電：`StationPoweredCondition.java:20-50`

### 3.4 条件評価に関する注意点

#### 貨物条件は毎tick全量走査ではない

`CargoThresholdCondition`は20tick周期のlazy条件で、Carriage storageのversion合計が前回と同じなら再集計せずfalseを返す。初回は`LastChecked=-1`なので評価する。CAT側で貨物状態を別管理・複製する場合は、このversion変化との整合が必要である。

#### レッドストーンリンク条件も変化版を利用する

Redstone Link条件は`globalPowerVersion`が前回と同じなら再判定せずfalseを返す。初回は評価される。周波数に対して「読込済みの電力が1つでもあるか」を判定するため、未読込送信元を電力ありとして保持する条件ではない。

#### `unloaded`は列車未読込ではなく駅位置の状態

画面名だけでは誤解しやすいが、`StationUnloadedCondition`は「列車Entityが未読込」を見るのではない。現在駅のBlock Entity位置がentity-tickingかを判定する。

## 4. 保存・再開・アドオン欠如時の標準動作

### 4.1 時刻表定義と実行状態は別に保存される

**確認できた標準動作**：

時刻表定義には、命令IDとData、条件IDとData、条件の二重リスト、繰返し設定、アイテム上の開始位置が入る。列車上の実行状態には、現在項目、状態、paused/completed、各OR列の進捗とcontext、予測移動時間が入る。

| 層 | 主な保存内容 | 根拠 |
| --- | --- | --- |
| 命令 | `Id`、`Data` | `ScheduleInstruction.java:25-53` |
| 条件 | `Id`、`Data` | `ScheduleWaitCondition.java:24-52` |
| 項目 | 命令、AND／OR条件二重リスト | `ScheduleEntry.java:26-46` |
| 時刻表 | Entries、Cyclic、必要時Progress | `Schedule.java:84-100` |
| 列車Runtime | CurrentEntry、State、Paused、Completed、ConditionProgress、ConditionContext等 | `ScheduleRuntime.java:381-415` |
| 列車本体 | Runtime全体を列車NBTへ格納 | `Train.java:1176,1182-1221` |

### 4.2 標準の「取り出して再挿入」は途中条件を継承しない

`ScheduleRuntime.returnSchedule()`は現在項目を`schedule.savedProgress`へ移して時刻表アイテムへ書くが、Runtimeの`ConditionProgress`／`ConditionContext`はアイテムへ書かない。その後ランタイムを破棄する。再挿入は`setSchedule()`でランタイムをresetする。

このため標準Createだけを見れば、例えば60秒待機を30秒まで進めて取り出した場合、同じ待機の30秒経過をアイテム経由で引き継ぐ仕組みはない。CATでは「適用時点の状態継承」「待ち時間維持」が既決定なので、Createの`savedProgress`だけでは不足する。

### 4.3 アドオン未導入・未知IDの読込

**確認できた標準動作**：

- 未登録の命令ID：警告を出し、新しい空の`DestinationInstruction`へ置き換えてreturnする。その命令の元Dataを保全して後で復元する処理はない（`ScheduleInstruction.java:34-45`）。
- 未登録の条件ID：警告を出し、`null`を返す（`ScheduleWaitCondition.java:33-44`）。
- 条件リスト読込側が`null`を除外するかは、手元にCatnipの`NBTHelper`実装ソースがなく未確認である。少なくとも、未知条件を不透明データとして安全に保持・再有効化する仕組みはこのCreateコードには見えない。

**CATとの整合上の結論**：関連アドオンの項目が入った時刻表を、そのアドオンなしでCreate標準のデシリアライズへ渡してから保存し直すと、元データを損なう可能性がある。CATは「未知項目を勝手に標準駅移動へ変換する」動作を採用すべきではない。

**整合のための提案（未承認）**：CAT側では命令／条件の元ResourceLocationと生NBTを不透明データとして保持し、実行時だけ「提供MOD・対応アダプタがあるか」を検証する。欠如時は項目を削除・置換せず、時刻表エラーとして停止・表示する。

## 5. 関連アドオンの調査結果

### 5.1 Create: Railways Navigator

| 項目 | 結果 |
| --- | --- |
| 対象版 | 未確認 |
| ソース／JAR | 今回のローカル調査範囲にはなし |
| 追加命令 | 未確認。追加があると断定しない |
| 追加待機条件 | 未確認。追加があると断定しない |
| 既存項目の変更 | 未確認 |
| CAT接続論点 | ソース提供後、登録箇所・ID・NBT schema・完了条件・保存状態・表示用途を調査する必要あり |

### 5.2 Create: Steam 'n' Rails

| 項目 | 結果 |
| --- | --- |
| 対象版 | 未確認 |
| ソース／JAR | 今回のローカル調査範囲にはなし |
| 追加命令 | 未確認。追加があると断定しない |
| 追加待機条件 | 未確認。追加があると断定しない |
| 既存項目の変更 | 未確認 |
| CAT接続論点 | ソース提供後、特に連結・分離・運転台方向・追加線路設備に関係する命令の有無と処理順を調査する必要あり |

### 5.3 現段階で確定できる対応範囲の表現

**CATの確定方針**は「両アドオンが対象版で時刻表GUIへ追加する命令・条件に対応する」である。一方、本調査で確定できた具体的ID一覧はCreate標準の14項目だけである。したがって実装前の対応表は次の2段階にする必要がある。

1. 提供元＋対象版ごとに、登録IDとNBT schemaをソースから凍結した互換表。
2. 各IDをCATのINTEROS・ATO・進路・表示へ接続するアダプタ仕様。

「GUIに現れた任意の未知項目を汎用的に実行する」だけでは、速度・方向・安全制約への作用を検証できないため、確実な対応とは言えない。

## 6. CATとの整合上の論点

### 6.1 確認できた標準動作とCAT確定方針の比較

| 論点 | 確認できた標準動作 | CATの確定方針 | 整合のための提案／要決定 |
| --- | --- | --- | --- |
| 発車条件 | いずれかのOR列が完了すると次項目へ進み、駅移動命令を開始する | 時刻表条件成立だけで安全制約・信号・進路許可を無視しない | 条件完了と「発車許可」を別状態にし、条件進捗を保持したままCAT許可を待つ案。採否要決定 |
| 時刻 | 現在のオーバーワールド`dayTime`を周期剰余し、40tick窓で判定 | ワールド内時刻を使う | Createと同じ周期・40tick窓まで互換要件に含めるか要決定 |
| 手動切替 | Runtimeの`paused`中は時刻条件を評価しないが、ワールド時刻は進む | 手動中は時刻表計時を止め、同じ停車でATO復帰時に経過時間を継承 | 経過待機は停止できるが、絶対時刻を「停止」できない。手動中の目標窓を逃した場合の扱いを要決定 |
| 経過待機 | contextのTimeをtickで増加し、Runtime NBTへ保存 | サーバーtick基準、手動中断中は加算しない | CATの既決定と整合可能。差替え時のcontext対応付けが必要 |
| スロットル | `Train.throttle`へ直接5～100%を代入し、Create Navigationの上限へ乗算 | Createの重複速度制御はCAT側へ置換し、安全制御を優先 | 「CAT基準速度への割合」「車両性能上限への割合」「独立制限」のどれに変換するか要決定 |
| 駅移動 | 名前／`*`で候補収集しCreate Navigationで最良経路探索 | CAT独自の候補順位、進路許可、信号・安全制約を適用 | GUI入力互換を保ち、候補決定・発車はCAT共通進路系へ接続する案 |
| 小包命令 | 貨物／郵便箱から行先候補を動的算出 | 全標準命令へ対応 | 標準の候補抽出は利用しつつ、実際の進路確保はCATへ委譲する境界を要設計 |
| 表示名 | Runtimeの運行タイトルを変更 | HUD・案内表示候補あり | INTEROS上の表示用タイトルとして保持するか要決定。列車名と混同しない |
| 時刻表差替え | 標準`setSchedule()`はRuntimeを全面reset | CATは適用時点の実行状態・待ち時間を条件付きで継承 | IDだけでなく項目・OR列・条件順・設定値を照合する継承規則が必要 |
| 未知項目 | 未知命令は空の駅移動へfallback、未知条件はnull | 対応MODの項目へ確実に対応 | CATはraw NBT保持＋明示エラーとする案。要承認 |

### 6.2 安全制約で発車できない場合

**CATの確定方針**：信号・進路・安全条件が未成立なら、時刻条件が成立しても発車しない。`整理3.md:462-463`の経過待機についても、条件達成後に信号待ちを並行し、数え直さない方針がある。

**整合のための提案（未承認）**：すべての待機条件について次の3段階を分離する。

1. `WAIT_CONDITION_PENDING`：時刻表条件を評価中。
2. `WAIT_CONDITION_SATISFIED`：条件は達成済みでラッチ。時間・貨物・外部信号を再要求しない。
3. `DEPARTURE_AUTHORIZATION_PENDING`：CATの信号・進路・安全・ATO開始条件を待つ。

この分離なら、8:00条件が成立した後に信号が8:10まで停止でも、次の日の8:00まで待ち直さずに済む。Create標準の`conditionProgress`による達成保持とも整合する。

### 6.3 手動運転と時刻指定

経過待機はカウンタを止めれば「手動中は進めない」を実現できる。一方、ワールド絶対時刻は止められない。標準CreateとCATの既決定をそのまま組み合わせると、時刻条件が評価対象のまま7:55に手動へ切り替え、8:05に同じ停車でATOへ戻した場合、8:00の40tick窓を逃して次周期待ちになる。

これは次のいずれかを選ぶ必要がある。

- **案A：Create標準準拠**：手動中は評価しない。復帰時に窓外なら次周期を待つ。
- **案B：通過ラッチ**：手動中も「目標時刻を通過した」事実だけ記録し、同じ停車へATO復帰したら時刻条件を達成済みとする。
- **案C：手動化時に時刻条件を中断扱い**：ATO復帰時に利用者へ、達成済み／次周期待ち／開始項目変更を選ばせる。

案Bは利用者の期待に合いやすいが、標準Createの40tick窓と「ランタイム停止中は未評価」という挙動からは変更になる。現時点では要決定である。

### 6.4 時刻表差替え時の状態継承

CATでは予約適用時の状態を引き継ぐ方針があるが、標準Runtimeのcontextは「OR列の番号」と「列内の現在条件番号」に依存する。編集で条件を追加・削除・並べ替えると、位置だけでcontextを移すのは危険である。

**整合のための提案（未承認）**：

- 同じ実行項目、同じOR列、同じ条件ID、同じ意味設定であることを確認できる条件だけcontextを継承する。
- 経過待機の時間を維持する既決定については、継承対象を明示的に特別扱いする。
- 貨物version、Redstone Linkのpower version、表示更新版など、外部状態のキャッシュは原則として再評価する。
- 時刻条件の「成立済み」は条件完了状態として継承するか、編集で時刻／周期が変われば必ず初期化する。

### 6.5 アドオン互換の最低要件

**整合のための提案（未承認）**：各対応項目について、少なくとも次を互換表に固定する。

- 提供MOD ID、対象版、登録ResourceLocation。
- GUIの入力フィールドとNBT key／型／既定値。
- 命令か待機条件か、条件対応の可否。
- 開始・完了・再試行・スキップ・エラーの状態遷移。
- 速度、経路、方向、駅、貨物、表示、外部信号への副作用。
- 実行途中に保存すべきcontext。
- MOD欠如、ID変更、NBT欠損・不正値時の停止と表示。

## 7. 未確認事項と必要なMinecraft内確認

今回はMinecraftを起動していない。次の手順は、静的調査の読み取りが実際のCreate 6.0.8環境と一致するかを利用者が確認するためのものである。

### 7.1 時刻条件の基本3例

共通設定：目的駅へ停車する時刻表項目に「指定時刻 8:00／24時間ごと」を1条件だけ設定し、昼夜サイクルを有効にする。`/time set`を使う場合、6:00＝0、8:00＝2000の対応を使う。

1. 7:50相当（概算`/time set 1833`）で停車させ、8:00付近で発車待機が解除されるか記録する。
2. 8:00相当（`/time set 2000`）で停車させ、直後に解除されるか記録する。
3. 8:05相当（概算`/time set 2083`）で停車させ、その日のうちに解除されず、次の日8:00まで待つか記録する。

記録項目：到着時`dayTime`、実際に条件が成立した`dayTime`、設定周期、他の条件・信号の有無。

### 7.2 40tick境界

8:00の目標tick 2000に対し、`dayTime=2039,2040,2041`付近から条件を評価させる。コード上は2040まで成立、2041は不成立のはずだが、コマンド実行と列車tickの順序で観測が1tickずれる可能性がある。実際の判定tickをログ等で確認する。

### 7.3 AND順序とOR

- 同じ列に「貨物条件→8:00」を設定し、貨物条件を8:05に成立させる。次周期待ちになるか。
- 同じ列に「8:00→貨物条件」を設定し、8:00通過後に貨物を成立させる。8:00達成が保持されて発車できるか。
- 上記2条件を別列（いずれか条件）へ置き、片方だけで待機終了するか。

### 7.4 時刻ジャンプ・睡眠・昼夜停止

- 7:50から8:05へ`/time set`で飛ばし、目標を逃すか。
- 8:05から7:59へ戻し、次に8:00を通過した際に成立するか。
- 夜に停車後、睡眠で朝へ移動し、その後8:00で成立するか。
- `doDaylightCycle=false`で窓外／窓内それぞれの状態を確認する。

### 7.5 保存・未読込・取り出し

- 時刻待機中と経過待機中にワールドを保存終了し、再起動後の進捗を確認する。
- 駅・車両Entityが未読込となる距離へ移動しても、列車Runtimeと時刻条件が進むか確認する。
- 経過待機途中で時刻表を取り出し、再挿入して待ち時間が初期化されるか確認する。
- `unloaded`条件が列車ではなく駅チャンクのentity-ticking解除で成立するか確認する。

### 7.6 関連アドオン提供後の確認

各アドオンについて、ソース調査後に次を行う。

1. Create標準の時刻表GUIに追加項目が全件表示されること。
2. 登録IDと保存NBTがソースの一覧と一致すること。
3. 実行、保存終了、再読込、時刻表取り出し・再挿入の各状態。
4. アドオンを一時的に外した複製ワールドでの読込結果。元ワールドでは行わない。
5. CAT導入後、項目成立が安全制約・信号・進路許可を越えて直接発車させないこと。

## 8. 次の仕様検討で決めるべき事項

優先順は次のとおり。

1. **時刻条件の互換範囲**：Create標準の周期選択・40tick窓・「窓を逃すと次周期」をCATでもそのまま採用するか。
2. **手動中の時刻通過**：手動中に指定時刻を通過した場合、未達成／通過ラッチ／復帰時選択のどれにするか。
3. **安全待ちとの境界**：時刻表条件達成を保存し、その後はCATの発車許可だけを待つ3段階状態案を採用するか。
4. **スロットル命令のCAT内意味**：Create最高速度への割合を、CATのどの制限値へ変換するか。手動運転時にも作用するか、ATOだけか。
5. **差替え時の条件状態継承**：同じ条件と判断する識別規則、AND／OR編集時の初期化、成立済み時刻条件の扱い。
6. **未知・欠如項目**：raw NBTを保持してエラー停止する案を採用するか。再導入時に自動復元するか、明示確認を求めるか。
7. **小包命令の経路境界**：標準候補抽出とCATの候補順位・進路許可・再発車承認の処理順。
8. **表示名命令**：INTEROS、HUD、案内表示のどこへ表示し、どの状態を正本とするか。
9. **アドオン具体一覧**：対象版のソース提供後、Railways Navigator／Steam 'n' Railsの全登録項目と互換表を確定する。
10. **版対応方針**：同じアドオンの版違いでID・NBT・動作が変わる場合の対応版範囲と拒否／移行方法。

## 9. 本調査からの推奨する次の進め方

1. まず時刻条件の上記1～3を仕様決定し、CATの「条件完了」と「発車許可」を分離するか決める。
2. 対象版を決めた2アドオンのソースを`vendor/`へ追加し、本書の第5章を具体的な登録一覧へ更新する。
3. Create標準14項目＋アドオン項目＋CAT独自方向命令を、共通の互換表へ整理する。
4. その後、予約差替え時の状態移行表と、MOD欠如時のエラー／raw NBT保持を決める。
5. 実装前に、上記Minecraft内確認を別工程として行い、静的調査と実機結果を追記する。

## 10. 主要ソース参照一覧

| 目的 | ローカルソース |
| --- | --- |
| 版 | `vendor/Create-mc1.20.1-6.0.8/gradle.properties:9-13` |
| 標準登録全件 | `.../schedule/Schedule.java:35-63` |
| 時刻条件 | `.../schedule/condition/TimeOfDayCondition.java:29-61,111-187` |
| AND／OR評価 | `.../schedule/ScheduleEntry.java:14-46`、`.../schedule/ScheduleRuntime.java:153-184` |
| GUIでの条件追加 | `.../schedule/ScheduleScreen.java:767-835` |
| Runtime更新・一時停止 | `.../schedule/ScheduleRuntime.java:102-150` |
| Runtime保存・読込 | `.../schedule/ScheduleRuntime.java:381-415` |
| 時刻表取出し | `.../schedule/ScheduleRuntime.java:418-427` |
| 列車NBTへのRuntime保存 | `.../trains/entity/Train.java:1176-1221` |
| グローバル列車tick | `.../trains/GlobalRailwayManager.java:190-228`、`.../trains/entity/Train.java:264-284` |
| 未知命令の読込 | `.../schedule/destination/ScheduleInstruction.java:34-53` |
| 未知条件の読込 | `.../schedule/condition/ScheduleWaitCondition.java:33-52` |
| スロットルと速度 | `.../schedule/destination/ChangeThrottleInstruction.java:68-97`、`.../trains/entity/Navigation.java:263-278` |

`.../schedule/`は`vendor/Create-mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/schedule/`、`.../trains/`は`vendor/Create-mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/`を表す。

## 11. 調査境界

- CAT実装コードは変更していない。
- `整理3.md`を含む既存仕様書は変更していない。
- vendorソースは変更していない。
- ビルド、Minecraft起動、ゲーム内検証、外部ソース取得は行っていない。
- Create: Railways Navigator／Create: Steam 'n' Railsの具体項目一覧は、対象版ソース不足により未完了である。
