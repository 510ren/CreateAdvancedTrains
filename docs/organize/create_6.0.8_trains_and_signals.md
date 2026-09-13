# Create 6.0.8（Forge / MC1.20.1）列車・信号システム解析

> 解析対象：`Creators-of-Create/Create` GitHubリポジトリ、タグ `mc1.20.1-6.0.8` のソースコードを直接取得して読解。
> パッケージ：`com.simibubi.create.content.trains.*`
> 事実はソースからの直接確認、断定を避けた箇所は「推測」と明記。

---

## 目次

1. [列車システム概要](#1-列車システム概要)
2. [信号システム詳細](#2-信号システム詳細)
3. [列車と信号の連携ポイント](#3-列車と信号の連携ポイント)
4. [主要クラス・関数一覧表](#4-主要クラスー関数一覧表)

---

## 1. 列車システム概要

### 1.1 tickの全体呼び出し順序

大元は `GlobalRailwayManager#tick(Level)`（`content/trains/GlobalRailwayManager.java`）。

```
GlobalRailwayManager#tick(level)
 ├─ signalEdgeGroups の trains リストと reserved をクリア
 ├─ 各 TrackGraph の graph#tickPoints(true) / resolveIntersectingEdgeGroups
 ├─ tickTrains(level)
 │   ├─ waitingTrains → Train#earlyTick
 │   ├─ movingTrains  → Train#earlyTick
 │   ├─ waitingTrains → Train#tick   ← 実際の移動処理
 │   └─ movingTrains  → Train#tick
 └─ 各 TrackGraph の graph#tickPoints(false)
```

`waitingTrains`（信号待ち等）を先にtickすることで、信号ブロックを先に解放させてから動いている列車を処理する順序保証がある。

### 1.2 `Train#tick(Level)` の処理順（`entity/Train.java`）

1. `updateConductors()` — 運転士（Controls block搭乗プレイヤー）の判定更新
2. `runtime.tick(level)` — スケジュール（自動運行）の状態遷移
3. `navigation.tick(level)` — 目的地への経路と目標速度の計算
4. `tickPassiveSlowdown()` — 目的地がない時の自然減速
5. `derailed` なら `tickDerailedSlowdown()`
6. 各Carriage間の連結ストレス計算
7. 各Carriageに `Carriage#travel(...)` を呼び実移動
8. `blocked`（行き止まり／非対応トラック）または `maxStress > 4`（連結ストレス過多）を判定
9. `updateNavigationTarget(level, distance)` — 距離減算＆再経路計算のトリガー

### 1.3 加速・減速

**`Train#approachTargetSpeed(float accelerationMod)`** が唯一の速度更新ロジック：

```java
if (speed < targetSpeed)
    speed = Math.min(speed + acceleration * accelerationMod, targetSpeed);
else if (speed > targetSpeed)
    speed = Math.max(speed - acceleration * accelerationMod, targetSpeed);
```

呼び出し元は2箇所のみ：

| 呼び出し元 | accelerationMod | 用途 |
|---|---|---|
| `Navigation#tick` | 常に `1` | 自動運転 |
| `CarriageContraptionEntity#control` | 通常時`1`、逆方向急制動時`2` | 手動運転 |

- `Navigation#tick` は冒頭 `if (destination == null) return;` があり、**目的地未設定時は丸ごとスキップ**される（手動運転はこのメソッドを通らず `control` 側で独立して加速処理される）。
- `Train#tickPassiveSlowdown()` は目的地なし＆非手動tick時に、毎tick `acceleration()` 分だけ速度を0へ近づける自然減速。
- `Train#tickDerailedSlowdown()` は脱線中、毎tick速度を1/3に減衰。

速度・加速度パラメータ：

```java
maxSpeed()     = (fuelTicks>0 ? poweredTrainTopSpeed : trainTopSpeed) / 20
maxTurnSpeed() = (fuelTicks>0 ? poweredTrainTurningTopSpeed : trainTurningTopSpeed) / 20
acceleration() = (fuelTicks>0 ? poweredTrainAcceleration : trainAcceleration) / 400
```

`fuelTicks > 0`（燃料燃焼中＝動力あり）かどうかで参照するコンフィグが切り替わる。デフォルト値（`CTrains.java`）：

| コンフィグ | デフォルト |
|---|---|
| `trainTopSpeed` | 28 |
| `trainTurningTopSpeed` | 14 |
| `trainAcceleration` | 3 |
| `poweredTrainTopSpeed` | 40 |
| `poweredTrainAcceleration` | 3 |
| `manualTrainSpeedModifier` | 0.75 |
| `maxAssemblyLength` | 128 |

### 1.4 自動運転（`Navigation#tick`）の目標速度決定

制動距離の公式 `d = v²/(2a)` を用いた二値判定がベース：

```java
double brakingDistance = (train.speed * train.speed) / (2 * acceleration);
...
double targetSpeed = targetDistance > brakingDistance ? topSpeed * speedMod : 0;
```

これに加えて：

- **駅への最終進入**（残距離<10）：`maxApproachSpeed = topSpeed * (targetDistance/10)` で緩やかに減速し停止位置のオーバーシュートを防止。
- **カーブ対策**：カーブ用最高速度 `maxTurnSpeed()` に間に合うよう、カーブ手前の減速開始距離を別途算出し、駅／信号向けの目標速度と比較して小さい方を採用。
- **スロットル**：`train.throttle` を最高速度に乗算（ディスプレイリンク等の外部制御を想定、推測）。
- **信号待ち**：`waitingForSignal != null` の場合、目的地距離の代わりに信号までの距離を使用。

### 1.5 経路探索

- `Navigation#search(...)` — 優先度付きキュー（`FrontierEntry`：`distance + penalty + remaining` でソート）によるダイクストラ法風探索。`remaining` はオクタイル距離で駅までの残距離を推定。
- コストには**他列車由来のペナルティ**（`Train#getNavigationPenalty()`）が加算される：

  | 状況 | ペナルティ |
  |---|---|
  | 駅停車中の他列車 | `STATION_WITH_TRAIN = 300` |
  | 手動運転中の列車 | `MANUAL_TRAIN = 200` |
  | スケジュール未設定／一時停止の列車 | `IDLE_TRAIN = 700` |
  | 到着間近の列車 | `ARRIVING_TRAIN = 50` |
  | 信号待ちの列車 | `WAITING_TRAIN = 50` + 待機時間比例（最大1000） |
  | 通常走行中の列車 | `ANY_TRAIN = 25` |
  | 赤信号区間 | `RED_SIGNAL = 25` |
  | レッドストーンで強制赤の信号 | `REDSTONE_RED_SIGNAL = 400` |

- `findPathTo(destinations, maxCost)` — 前進・後退両方向で探索し、双方向対応車両ならコストの低い方を採用。
- `findNearestApproachable(forward)` — 手動運転中にスペースキー長押しで最寄り駅へ自動進入する機能のための探索。

### 1.6 自動運行スケジュール（`ScheduleRuntime`）の状態機械

`enum State { PRE_TRANSIT, IN_TRANSIT, POST_TRANSIT }`

- **PRE_TRANSIT**：`startCurrentInstruction` → 各命令の `start()`（例：`DestinationInstruction#start` は正規表現で駅名マッチ→`Navigation#findPathTo`）→ 経路発見で `IN_TRANSIT` へ。
- **IN_TRANSIT**：`Navigation` が経路追従。駅到着（`Train#arriveAt`）で `destinationReached()` → `POST_TRANSIT` へ。
- **POST_TRANSIT**：`tickConditions()` が待機条件（`ScheduleWaitCondition` リスト、例：`ScheduledDelay`＝一定tick待機）を順に評価し、全て満たせば次エントリーへ進み `PRE_TRANSIT` に戻る。

スケジュール末尾到達時（`checkEndOfScheduleReached`）、循環設定でなければ `paused = true` で自動運行停止。

### 1.7 駅停車

- `GlobalStation#canApproachFrom(side)` — 正しい方向からの進入か、組立中でないかを判定（逆走進入不可）。
- `Train#arriveAt(station)`：`setCurrentStation` → `reservedSignalBlocks.clear()` → `runtime.destinationReached()` → `station.runMailTransfer()`。
- `Train#leaveStation()`：発車は「速度が0→非0になる」瞬間ではなく、このメソッド呼び出し自体で管理される。呼ばれるのは `approachTargetSpeed`（手動tick時）と `Navigation#tick`（信号青＆残距離十分小の時）。
- `GlobalStation` は `WeakReference<Train> nearestTrain` を1つだけ保持し、同一駅への複数列車競合を距離で調停（`reserveFor`/`cancelReservation`）。

### 1.8 脱線・衝突判定

`train.derailed = true` のトリガーは3パターン：

**(a) 連結ストレス過多**（`Train#tick`）
```java
} else if (maxStress > 4) {
    speed = 0; navigation.cancelNavigation(); derailed = true; status.highStress();
}
```
隣接Carriage間隔のズレやボギー伸縮が許容範囲（4ブロック）を超えると脱線。`blocked`（行き止まり／非対応トラック）は脱線ではなく単純停止（`status.endOfTrack()`）で区別される。

**(b) 列車同士の衝突**（`Train#collideWithOtherTrains` → `findCollidingTrain` → `crash()`）
幾何学的交差判定の後、`combinedSpeed = |speed|+|他列車speed|` が0.2を超えると爆発発生。両列車が `crash()`（`derailed=true; graph=null;` 速度反転減衰）。

**(c) トラック消失後の再接続失敗**（`Train#reattachToTracks`）
トラック破壊等で `graph=null` になった列車は毎tick再接続を試み、失敗が続くと `derailed=true`（`migrationCooldown=40`tickごとに再試行）。成功時のみ `derailed=false` に自動復帰。

### 1.9 手動運転（`CarriageContraptionEntity#control`）

Controls block搭乗時の入力処理。前進/後退/左右/スペースの `heldControls` から `targetSpeed`/`targetSteer` を算出し、`topSpeed = maxSpeed() * manualTrainSpeedModifier`（既定0.75倍）、カーブ上なら `maxTurnSpeed()`、逆入力なら1/4速に調整して `approachTargetSpeed` を呼ぶ。駅停車中はスペースキー長押しで最寄り駅へ自動進入するアシスト機能もここに実装されている。

### 1.10 燃料（`Train#burnFuel()`）

進行方向側のCarriageから優先的に燃料アイテムを探索・消費し `fuelTicks` を加算。`Navigation#tick` 内でのみ呼ばれ、手動運転時は新規消費が発生しない（コード構造からの推測）。

---

## 2. 信号システム詳細

### 2.1 信号ブロックの基本（`SignalBlock` / `SignalBlockEntity`）

- 信号は `SignalBlock`（Block）＋ `SignalBlockEntity`（BlockEntity）の組。
- ブロックステート `TYPE`：`SignalType.ENTRY_SIGNAL` または `SignalType.CROSS_SIGNAL`。ワンチ（`onWrenched`）でサイクル切替（`SignalBoundary#cycleSignalType`）。
- ブロックステート `POWERED`：レッドストーン入力の有無。`neighborChanged`/`tick` で近傍のレッドストーン信号を監視し、遅延4tick後に反映（チャタリング防止と思われる、推測）。
- **強制赤**：`POWERED=true` の信号は、閉塞区間の空き状況に関わらず常に赤（`SignalBoundary#isForcedRed`）。コンピュータ（CC:Tweaked連携、`computerBehaviour.hasAttachedComputer()`）が接続されている場合はレッドストーン入力を無視しコンピュータ制御を優先。
- コンパレータ出力：赤信号のとき15、それ以外0（`getAnalogOutputSignal`）。

### 2.2 信号の状態（`SignalBlockEntity.SignalState`）

```java
enum SignalState { RED, YELLOW, GREEN, INVALID; ... }
```

- `RED`：閉塞区間が占有されている、または強制赤。
- `GREEN`：閉塞区間が空いている（ENTRY_SIGNALは常にこの二値判定）。
- `YELLOW`：CROSS_SIGNALでのみ発生。連鎖先の一部だけが空いている状態（後述）。
- `INVALID`：閉塞区間グループ未確定（両側が同一グループ等、構成不正時）。

`enterState()` には**チラつき防止のグレース期間**がある：GREEN/YELLOWになった信号は15tickの間は強制的にREDへ戻らない（`switchToRedAfterTrainEntered` カウンタ）。列車の先頭/末尾判定点が境界付近で細かく前後する際の点滅対策と考えられる（推測）。

コンピュータ連携：状態が変わるたびに `SignalStateChangeEvent` をCC:Tweakedの接続コンピュータへ送出。

### 2.3 信号の左右2面（`SignalBoundary`）

1つの `SignalBoundary`（信号機の設置単位＝トラック上の1点）は**進行方向ごとに独立した2つの面**を持つ（`Couple<T>` で表現）：

- `types`：面ごとの `SignalType`
- `groups`：面ごとに所属する `SignalEdgeGroup`（閉塞区間）のUUID
- `blockEntities`：面ごとに紐づく実際の `SignalBlockEntity` 群（1つの境界点に対し前後で別々のブロックを設置できる＝単線での両方向信号など）
- `cachedStates`：面ごとの現在の `SignalState`

`isPrimary(side)` でどちら面かを判定し、`getGroup(side)` / `getTypeFor(pos)` / `getStateFor(pos)` などが面ごとの値を返す。

### 2.4 閉塞区間の自動生成（`SignalPropagator#propagateSignalGroup`）

信号が置かれる（またはトラックが変化する）と、その信号の片面から**次の信号に出会うまでトラックグラフをBFSで塗りつぶし**、通過した全エッジに同一の `SignalEdgeGroup`（UUID）を割り当てる（`walkSignals` 内部メソッド）。

- 分岐点（ポイント）があれば各分岐先すべてに探索が広がる（フロンティアに複数ノードを追加）＝閉塞区間は分岐を含む「面」として広がりうる。
- 次の信号に到達したらそこで探索を打ち切り、その信号の該当面に自分のグループUUIDをセットする。
- 信号が存在しない区間全体は `EdgeData#setSingleSignalGroup` によりグラフ全体で共有される1つの「無信号グループ」（`EdgeData.passiveGroup`、いわば区間全体が1つの巨大な閉塞）にまとめられる。

### 2.5 閉塞区間の占有判定（`SignalEdgeGroup`）

```java
isThisOccupiedUnless(Train train):
    reserved != null || trains.size() > 1 || (!trains.contains(train) && !trains.isEmpty())
```

- `trains`：現在その閉塞区間内にいる列車の集合（`Train#earlyTick` 内 `addToSignalGroups` で毎tick再構築）。
- `reserved`：ある信号がその閉塞区間を「これから進入するために予約中」であることを示す `SignalBoundary` 参照（自列車の通過待ちで他列車に割り込まれないようにする仕組み）。
- 「自分以外の列車がいる」「複数列車がいる」「誰かに予約されている」のいずれかで占有扱い＝赤信号。

**交差判定の拡張（`intersecting` / `intersectingResolved`）**：グラフ上つながっていなくても物理的に交差する2本のトラック（立体交差でない平面交差、`TrackEdgeIntersection`）は、`SignalEdgeGroup#putIntersection` で互いの占有判定が連動するようにリンクされる（`walkIntersecting` で連結成分を辿り、どれか1つでも占有されていれば全体が占有扱い）。踏切のような「別グループだが同時に使えない」区間を表現する仕組みと考えられる。

### 2.6 クロス信号の連鎖判定（3値信号 = 出発信号／遠方信号的な機能）

`SignalBoundary#resolveSignalChain`：

- `ENTRY_SIGNAL`（通常の閉塞信号）は赤／青の二値のみ（占有されていなければ常に`GREEN`）。
- `CROSS_SIGNAL` は、`SignalPropagator#collectChainedSignals` で**次の通常信号（ENTRY_SIGNAL）に到達するまで連鎖する全信号**を収集し（クロス信号が連続していれば更に先まで連鎖）、その先の全信号の状態を見て：
  - 全て `GREEN`（または無効）→ 自分も `GREEN`
  - 全て `RED` → 自分も `RED`
  - 一部だけ空いている → `YELLOW`（黄色＝「この先のどこかで詰まっている可能性がある、注意」を示す一種の遠方信号的表現）

`Navigation#tick` 側でもこのクロス信号の連鎖を辿って `waitingForChainedGroups` に候補を蓄積し、途中に空いていない区間があればチェーンの起点（最初のクロス信号）で待機、全て空いていれば `reserveChain()` で一括予約する処理になっている。

### 2.7 閉塞区間の識別色（`EdgeGroupColor`）

```java
enum EdgeGroupColor { YELLOW, GREEN, BLUE, ORANGE, LAVENDER, RED, CYAN, BROWN, WHITE }
```

各 `SignalEdgeGroup` はトラック描画（推測：閉塞区間ごとにトラック下の発光帯などで視覚的に区別する機能）用に8色＋白（予備）を持ち、`SignalEdgeGroup#resolveColor()` が隣接グループ（`adjacent`／交差で連結されたグループ`intersectingResolved`経由の隣接）と色が被らないよう**簡易グラフ彩色アルゴリズム**（ビットマスク `strikeFrom` で使用済み色を除外し `findNextAvailable` で最小の未使用色を選ぶ）で自動割当てを行う。8色を使い切ると `WHITE` にフォールバックする。

### 2.8 列車進入・退出時の信号連携（`Train.java` 側）

- `Train#frontSignalListener()`：列車先頭が信号境界を通過→`occupiedSignalBlocks` に占有登録（`occupy(groupId, boundaryId)`）。未スケジュール／一時停止中の列車が赤信号区間に進入すると `AllAdvancements.RED_SIGNAL` 実績を付与するコードもここにある。
- `Train#backSignalListener()`：列車末尾が信号境界を通過→`occupiedSignalBlocks` から解放。
- `Train#collectInitiallyOccupiedSignalBlocks()`：列車生成・再接続時、末尾から逆方向にスキャンして初期占有状態を再構築する初期化専用メソッド。
- 列車自身は `occupiedSignalBlocks`（`Map<UUID groupId, UUID boundaryId>`）と `reservedSignalBlocks`（`Set<UUID>`）の2つを持ち、`Train#earlyTick` で `addToSignalGroups` によりグローバルな `SignalEdgeGroup#trains` へ自分を登録する。

### 2.9 信号周辺の変更通知（`SignalPropagator#notifyTrains`）

トラックの追加・削除・信号設置変更があった際、その変更が影響する `TrackEdge` 上に居る全列車の `updateSignalBlocks` フラグを立てる。このフラグが立った列車は次の `earlyTick` で `collectInitiallyOccupiedSignalBlocks()` により占有状態を再計算する（＝信号レイアウトを後から編集しても、既存の列車の占有状態が正しく再同期される仕組み）。

---

## 3. 列車と信号の連携ポイント（まとめ図）

```
Navigation#tick
  └─ signalScout(TravellingPoint) を列車先頭から前方へ走らせる
      └─ SignalBoundary に当たるたびに:
          ├─ CROSS_SIGNAL なら waitingForChainedGroups に追加して連鎖続行
          ├─ ENTRY_SIGNAL かつ SignalEdgeGroup#isOccupiedUnless(train)=true
          │     → waitingForSignal にセット、targetSpeed=0 方向へ
          └─ 空いていれば signalEdgeGroup.reserved = signal で先行予約

Train#tick 移動後
  └─ 先頭/末尾が実際に信号境界を通過
      ├─ frontSignalListener → occupiedSignalBlocks に登録（占有開始）
      └─ backSignalListener  → occupiedSignalBlocks から削除（占有解除）

Train#earlyTick（毎tick）
  └─ addToSignalGroups(occupiedSignalBlocks / reservedSignalBlocks)
      → SignalEdgeGroup#trains に自列車を再登録
         （SignalBlockEntity#tick が isOccupiedUnless を見て信号機の見た目を更新）
```

自動運転の「減速判断」（`Navigation#tick` の制動距離計算）と、「実際に信号機のランプが赤/青/黄のどれを表示するか」（`SignalBoundary#tickState`→`SignalBlockEntity#enterState`）は、**同じ `SignalEdgeGroup#isOccupiedUnless` を参照しているが計算されるタイミング・呼び出し元が別**という点は実装上の注意点。

---

## 4. 主要クラス・関数一覧表

### 列車系

| 機能 | 主な関数 |
|---|---|
| 全体tickループ | `GlobalRailwayManager#tick`, `#tickTrains` |
| 列車の物理tick | `Train#earlyTick`, `Train#tick` |
| 加減速の適用 | `Train#approachTargetSpeed`, `#tickPassiveSlowdown`, `#tickDerailedSlowdown` |
| 速度・加速度パラメータ | `Train#maxSpeed`, `#maxTurnSpeed`, `#acceleration` |
| 自動運転の目標速度計算 | `Navigation#tick` |
| 経路探索 | `Navigation#search`, `#findPathTo`, `#findNearestApproachable` |
| スケジュール状態機械 | `ScheduleRuntime#tick`, `#destinationReached`, `#tickConditions`, `#startCurrentInstruction` |
| 駅停車 | `GlobalStation#canApproachFrom`, `#reserveFor`, `Train#arriveAt`, `#leaveStation` |
| 脱線判定 | `Train#tick`内`maxStress>4`, `#crash`, `#collideWithOtherTrains`, `#findCollidingTrain`, `#reattachToTracks` |
| 手動運転 | `CarriageContraptionEntity#control` |
| 燃料 | `Train#burnFuel` |
| 実移動計算 | `Carriage#travel`, `TravellingPoint#travel` |

### 信号系

| 機能 | 主な関数 |
|---|---|
| 信号ブロックの状態管理 | `SignalBlock`（POWERED/TYPE）, `SignalBlockEntity#tick`, `#enterState` |
| 信号の左右2面管理 | `SignalBoundary`（`types`/`groups`/`blockEntities`/`cachedStates`） |
| 強制赤（レッドストーン） | `SignalBoundary#isForcedRed` |
| 閉塞区間の自動生成 | `SignalPropagator#propagateSignalGroup`, `#walkSignals` |
| 閉塞区間の占有判定 | `SignalEdgeGroup#isOccupiedUnless`, `#isThisOccupiedUnless` |
| クロス信号の連鎖（3値） | `SignalBoundary#resolveSignalChain`, `SignalPropagator#collectChainedSignals` |
| 平面交差の連動 | `SignalEdgeGroup#putIntersection`, `#walkIntersecting`, `EdgeData#refreshIntersectingSignalGroups` |
| 閉塞区間の識別色 | `EdgeGroupColor#resolveColor`, `#findNextAvailable` |
| 列車の占有登録・解除 | `Train#frontSignalListener`, `#backSignalListener`, `#collectInitiallyOccupiedSignalBlocks` |
| レイアウト変更時の再同期 | `SignalPropagator#notifyTrains` → `Train#updateSignalBlocks` |
| ナビゲーション側の信号スキャン | `Navigation#tick`内`signalScout`, `#currentSignalResolved`, `#reserveChain` |

---

*本ドキュメントは Create Mod 6.0.8（Forge, MC1.20.1）のソースコード直読に基づく。深堀りしていない箇所（`TrackEdge#incrementT` のカーブ上距離計算式の詳細、`ComputerCraft` 連携の具体的なイベントペイロード等）は含まれない。*
