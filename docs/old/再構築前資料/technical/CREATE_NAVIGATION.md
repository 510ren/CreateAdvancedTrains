# Create Navigation

## CATに必要な観測基準

CATの停止位置・残距離は車体モデルの見た目から推測しない。走行試験ログでは次を基準にする。

- 列車先頭位置：Navigationが現在の進行方向に応じて使用する先頭側TravellingPoint
- 停止位置：Navigation destinationであるGlobalStationの線路上EdgePoint
- 残距離：`Navigation.distanceToDestination`の符号付きblocks値

この定義はTrainDataDebuggerの必須ログ仕様であり、詳細は
`docs/research/EXPERIMENTS.md`を参照する。

## 現在分かっている挙動

Create標準Navigationは、制動距離を基に駅接近時に`targetSpeed = 0`を要求する。
さらに駅の近傍では通常の加速度追従と異なる速度補正を行う。CATの将来の停止制御は、
この補正との競合を実測と実ソースで確認して設計する。

## 2026-09-09のログで確認できた出力

`docs/reference/logs/09-09_2/` の毎tickログでは、destinationがavailableの全sampleに、
leading TravellingPointのgraph/edge/edge内距離/ワールド座標、GlobalStation EdgePointの
graph/edge/位置、符号付き`distanceToDestinationBlocks`が出力されている。

停止直前にはleading TravellingPointとdestination EdgePointが同一edge上にあり、
tick 3487で残距離0.2368 blocksを記録した後、tick 3488でdestinationがなくなった。

## 継続調査事項

`distanceToDestination`の符号規約、destinationなしの場合のCreate内部状態、駅停止位置と
列車先頭基準の厳密な対応は、反復測定とCreate 6.0.8実ソースの確認を続ける。

## 次回Sol実装仕様：読み取り専用の停車対象照会（Phase 5前提工程）

### 目的

自動運転ではCreate Navigationがすでに計算した停車対象までの距離を利用し、手動運転では
Navigationが`null`でも、Create 6.0.8と同じ停車対象探索規則に基づく距離を**読み取り専用で**
取得する。HUDと将来の制動計算が、Create内部値や探索式を個別に持つ状態を解消する。

これはPhase 5の制動数学へ進むための情報取得基盤である。BrakingCurve、製品版のノッチ自動選択、
TASC、EB、速度制限、ATOの制御仕様を実装または変更する作業ではない。

### Solが実装する範囲

Solは、現在のpackage構造を確認したうえで、責務が明確なサーバー側の
`CreateTrainQueryUtil`（または同等に明確な名称）を追加する。少なくとも次の問い合わせを提供する。

| 問い合わせ | 入力 | 出力と不変条件 |
| --- | --- | --- |
| 次の停車対象までの距離 | `Train` | 符号付きblocksの距離と取得元。Navigation値を利用できる場合は`Navigation.distanceToDestination`をそのまま返す。 |
| 手動運転時の停車対象距離 | `Train` | Navigation値が利用不能な場合だけ、Create 6.0.8の手動運転が用いる次の到達可能GlobalStation探索と同じ規則で導出を試みる。 |
| 停車に向けた減速の情報 | `Train`とCreate境界のnative target等、必要な読み取り値 | Create関連の観測・判定を一元化する。制動ノッチ、targetSpeed、速度を変更してはならない。 |

距離の公開結果は不変の値オブジェクトとし、少なくとも次を区別する。

- 利用可能な有限の符号付き距離（単位はblocks）
- 取得元：`NAVIGATION`、`MANUAL_CREATE_EQUIVALENT`、または`UNAVAILABLE`
- 利用不能の理由。距離未取得を`0.0`で表現してはならない。

具体的なrecord、enum、メソッド名は現在のコードとCreate 6.0.8 APIを確認して決めてよいが、
複数のControllerやHUDがCreate内部値へ直接アクセスする余地を残してはならない。

### Create 6.0.8の事前確認と停止条件

Solは実装前に、Create 6.0.8の実ソースで次を確認する。

1. `Navigation.distanceToDestination`が有効な条件、型、単位、符号、destinationなし時の値。
2. Navigationがない手動運転で、次の到達可能GlobalStationを決める実際のクラス、メソッド、
   入力、進行方向規則、距離算出規則。
3. その探索を再利用または同等に再現しても、`Train.navigation`、TravellingPoint、Graph、
   スケジュール、world状態、列車状態を変更しないこと。

手動探索に安全な読み取り方法がなく、Create APIの呼出が状態変更を伴う場合、Solは代替実装を
推測して追加してはならない。その時点で実装を止め、確認したソース箇所、副作用、可能な選択肢を
実装報告として返す。

### HUDへの適用

既存の開発HUDでは、距離欄だけをこの照会ユーティリティの結果へ置き換える。

- 利用可能なら、従来どおり符号付きblocksを表示する。
- 利用不能なら、`0`や`n/a`ではなく`--`を表示する。
- `TrainStatusHudPacket`が送る速度、加速度、目標速度、距離の内部単位はblocks系のままとする。
- HUD以外の既存の表示・Packet形式を、縮尺換算や実世界単位の追加を理由に変更してはならない。

### 明示的な非対象

- `Train.speed`、`Train.targetSpeed`、`Train.runtime`、`Train.navigation`、TravellingPoint、
  carriage、Graph、world状態への書き込み
- `Train.approachTargetSpeed()`の呼出順、Mixin、Phase 5A固定ノッチ試験、TrainDataDebuggerの変更
- BrakingCurve、TargetSpeedResolver、TASC、EB、製品版ノッチ自動選択、停止距離・最大許容速度計算の実装
- blocksとmの縮尺設定、実世界単位への換算、ログschemaの変更

### 完了条件

Solの完了条件は、上記のJava実装、Create 6.0.8実ソースに基づく静的確認、`./gradlew build`の成功、
および次の報告である。

1. 変更したファイルと各責務
2. Navigation経路・手動探索経路に関して確認したCreate 6.0.8のソース根拠
3. 読み取り専用であること、未取得を0で扱わないこと、制御値を書き換えないことの確認
4. build結果、未解決事項、仕様からの逸脱の有無

Minecraftの起動、HUD表示の確認、手動運転での探索確認、走行試験、ログ採取はユーザーが行う。
これらはSolの完了条件ではない。

## Create 6.0.8-289実ソース調査：Navigation残距離の符号とライフサイクル

### 調査範囲と正本

本節はPhase 5BのBrakingCurve仕様確定に先立ち、Create 6.0.8-289のmapped sourceだけを
静的に確認した結果である。確認対象は次のクラスである。

- `com.simibubi.create.content.trains.entity.Navigation`
- `com.simibubi.create.content.trains.entity.Train`
- `com.simibubi.create.content.trains.entity.Carriage`
- `com.simibubi.create.content.trains.entity.TravellingPoint`
- `com.simibubi.create.content.trains.graph.DiscoveredPath`
- `com.simibubi.create.content.trains.station.GlobalStation`
- `com.simibubi.create.content.trains.signal.SingleBlockEntityEdgePoint`
- `com.simibubi.create.content.trains.signal.TrackEdgePoint`
- `com.simibubi.create.content.trains.schedule.ScheduleRuntime`
- `com.simibubi.create.content.trains.entity.CarriageContraptionEntity`

調査したソースはGradle cache内の
`create-1.20.1-6.0.8-289_mapped_official_1.20.1-sources.jar`である。
本節の確認結果は、同じ文書内の従来記述にある
「`Navigation.distanceToDestination`の符号付きblocks値」という表現を次のとおり訂正する。

> `Navigation.distanceToDestination`は進行方向を符号で表す値ではない。通常は前進・後進ともに
> 正の残距離であり、進行方向は`Navigation.destinationBehindTrain`で別に保持される。

### 型・単位・符号規約

`Navigation`は次のフィールドを持つ（`Navigation.java:53-56`）。

```java
public GlobalStation destination;
public double distanceToDestination;
public double distanceStartedAt;
public boolean destinationBehindTrain;
```

`distanceToDestination`の型は`double`、単位はblocksである。経路探索は
`TrackEdge.getLength()`、TravellingPointのedge上`position`、GlobalStationの
`getLocationOn(edge)`から距離を構成し、毎tickではblocks/tickの列車移動量を差し引く。

`Navigation.findPathTo()`が生成する`DiscoveredPath.distance`は、前方向を正、後方向を負とする
方向付き距離である（`Navigation.java:492-494`）。一方、`Navigation.startNavigation()`は
`Math.abs(pathTo.distance)`を`distanceToDestination`へ格納し、元の負符号を
`destinationBehindTrain = pathTo.distance < 0`へ分離する（`Navigation.java:386-405`）。

したがって通常の有効Navigation中は次の規約になる。

| 値 | 意味 |
| --- | --- |
| `distanceToDestination` | 進行方向先端からdestinationまでの通常は非負の経路残距離 |
| `destinationBehindTrain == false` | Create上の前方向へ走行する経路 |
| `destinationBehindTrain == true` | Create上の後方向へ走行する経路 |

後方向のdestinationでも`distanceToDestination`は通常正であり、この値の符号だけから進行方向を
判断してはならない。

### 残距離の始点と終点

`Navigation.findPathTo()`および内部の`Navigation.search()`が使う始点は次のとおりである
（`Navigation.java:461-467, 595-612`）。

- 前方向：`train.carriages.get(0).getLeadingPoint()`
- 後方向：`train.carriages.get(train.carriages.size() - 1).getTrailingPoint()`

これは列車中心やcarriage entity座標ではなく、走行方向先端のTravellingPointである。
`Train.tick()`は速度が負ならcarriageの処理順を反転し、最初に処理した走行方向先頭carriageの
実移動距離を`Train.updateNavigationTarget()`へ渡す（`Train.java:374-410`）。
`Carriage.travel()`も同じ走行方向先端のTravellingPointへ`Train.frontSignalListener()`を割り当てる
（`Carriage.java:178-193`）。

終点の`GlobalStation`は`SingleBlockEntityEdgePoint`を経由して`TrackEdgePoint`を継承する
（`GlobalStation.java:38`）。`TrackEdgePoint`は線路edgeとedge内位置を保持し、
`getLocationOn(TrackEdge)`で対象edge上の位置を返す（`TrackEdgePoint.java:23-27, 71-78`）。
`Navigation.findPathTo()`はこのGlobalStation EdgePoint位置までの距離を計算するため、終点は
駅ブロックのBlockPosではなくdestination GlobalStationの線路上EdgePointである。

### 計算・更新・消去箇所

| 操作 | メソッド | 確認結果 |
| --- | --- | --- |
| 経路距離の計算 | `Navigation.findPathTo()` | 走行方向先端のTravellingPointからGlobalStation EdgePointまでを計算し、`DiscoveredPath.distance`へ方向付きで格納する。 |
| Navigation開始 | `Navigation.startNavigation()` | 経路距離の絶対値を`distanceToDestination`へ格納し、方向を`destinationBehindTrain`へ分離する。 |
| 毎tick更新 | `Train.updateNavigationTarget()` | `destinationBehindTrain`と実移動距離の符号を使い、destinationへ近づく量を正に正規化して残距離から減算する（`Train.java:521-535`）。 |
| 正常到着 | `Train.frontSignalListener()` | 速度と残距離を0にし、経路を消去し、`arriveAt()`後にdestinationをnullにする（`Train.java:437-449`）。 |
| Navigation解除 | `Navigation.cancelNavigation()` | 残距離を0にし、経路と予約を消去してdestinationをnullにする（`Navigation.java:375-383`）。 |
| 保存・読込 | `Navigation.write()` / `read()` | destinationがある場合にだけ距離・方向・経路を永続化し、読込時もdestinationをgraphから復元できた場合に距離を読む（`Navigation.java:846-899`）。 |

`Train.tick()`内の順序は、`runtime.tick()`、`navigation.tick()`、列車移動、
`updateNavigationTarget()`である（`Train.java:264-290, 397-434`）。Navigation制御中に読む残距離は、
直前tickまでの実移動量が反映された値である。

### 正常到着と通過時の挙動

`TravellingPoint.edgeTraversedFrom()`は走行区間上の`TrackEdgePoint`を列挙する
（`TravellingPoint.java:357-378`）。走行方向先端がdestinationと同一のGlobalStationへ正しい方向から
到達すると、`Train.frontSignalListener()`は`true`を返す。`TravellingPoint.travel()`はその戻り値を受け、
TravellingPointの位置と実移動量をGlobalStation EdgePoint位置で切り詰める
（`TravellingPoint.java:227-232, 282-288, 341-348`）。

同じ到着コールバック内では次の順に処理される。

1. `Train.speed = 0`
2. `navigation.distanceToDestination = 0`
3. `navigation.currentPath.clear()`
4. `Train.arriveAt(navigation.destination)`
5. `navigation.destination = null`

したがって、正常な有効Navigationではdestinationをそのまま通過して負の残距離を記録するのではなく、
駅EdgePointで停止し、距離0とdestination消去へ移行する。

ただし`distanceToDestination`の毎tick減算には0 clampがない。EdgePoint到着検出や経路状態に異常があり
destinationが残ったまま通過した場合は負値になり得る。`Navigation.tick()`は`targetDistance`へ0.25 blocksを
加えた後、`targetDistance < -10`で`cancelNavigation()`する（`Navigation.java:235-253`）。したがって
小さな負値は即座には0へclampされず、概ね`distanceToDestination < -10.25`まで残り得る。

また、移動区間の終端がEdgePoint位置と完全一致する場合、そのtickではEdgePoint横断判定が成立せず、
一時的に`destination != null && distanceToDestination == 0`となり、次tickの微小移動で到着処理される
可能性がある（`TravellingPoint.java:368-378`）。

### destinationの設定条件とライフサイクル

Create自身の有効判定は`Navigation.isActive()`であり、実装は`destination != null`である
（`Navigation.java:343-345`）。距離値だけをNavigationの有効判定にしていない。

destinationは、経路探索が成功し、必要な進行方向のconductor条件を満たした後、
`Navigation.startNavigation()`の`this.destination = pathTo.destination`で設定される
（`Navigation.java:386-438`）。主な呼出元は次のとおりである。

- スケジュール運転：`ScheduleRuntime.tick()`（`ScheduleRuntime.java:125-138`）
- 手動の駅Navigation開始：`CarriageContraptionEntity.control()`（`CarriageContraptionEntity.java:642-651`）

終了時の組合せは、正常到着と`cancelNavigation()`のどちらも最終的に
`destination == null && distanceToDestination == 0`となる。ただし次の不整合状態はソース上可能である。

#### `destination == null && distanceToDestination > 0`

`Navigation.startNavigation()`はconductor条件の検証より前に`distanceToDestination`、`currentPath`、
`destinationBehindTrain`を更新する。検証に失敗するとdestinationを設定せず`-1`を返すため、開始前の
destinationがnullなら、destinationなしで正の距離だけが残り得る。このため距離単独を有効判定に
使用してはならない。

#### `destination != null && distanceToDestination == 0`

EdgePoint位置へちょうど到達して横断判定が次tickへ持ち越された場合や、距離0のpathを受け取った場合に
成立し得る。destinationがあるだけでは「前方に正の残距離がある」とは断定できない。

#### `destination != null && distanceToDestination < 0`

正常到着経路では生じないが、0 clampがないため異常な経路・EdgePoint検出失敗では可能である。
Createも大きな負値を`cancelNavigation()`する分岐を持つため、負値を正常な前方残距離として扱ってはならない。

### Phase 5Bへの設計上の結論

BrakingCurveがNavigation由来の値を「前方停止点までの有効な残距離」として使用できる最低条件は
次のとおりである。

1. サーバー側のTrain状態である。
2. `train.navigation != null`。
3. `navigation.destination != null`。
4. `Double.isFinite(navigation.distanceToDestination)`。
5. `navigation.distanceToDestination > 0`。
6. `train.graph != null`かつcarriageが存在する。
7. `destinationBehindTrain`に対応する走行方向先端TravellingPointのedge/nodeが有効である。

`distanceToDestination`の符号から進行方向を推定してはならない。方向と残距離を別の値として扱う。
少なくとも次の状態を区別する必要がある。

| 条件 | BrakingCurveへ渡す前の意味 |
| --- | --- |
| `destination == null` | `NO_ACTIVE_DESTINATION`。Navigation由来の制動制約はない。 |
| `destination != null && distance > 0` | `AHEAD`。有効な前方残距離。 |
| `destination != null && distance == 0` | `AT_DESTINATION_OR_ARRIVAL_PENDING`。単純な制約なしにしてはならない。 |
| `destination != null && distance < 0` | `PAST_DESTINATION_OR_INVALID_STATE`。通常状態ではなく、単純な制約なしにしてはならない。 |
| 非有限値 | `INVALID`または`UNAVAILABLE`。 |

destinationなしはNavigation由来の停止制約なしとして扱える。一方、destinationありで距離0以下を
制約なしへ落とすと、到着位置または異常通過状態で再加速を許可する可能性がある。BrakingCurve自身が
到着完了を確定するのではなく、停止済み・到着待ち・通過または異常を区別した状態を上位へ返す必要がある。

### CATの通過・オーバーラン判定

`destination != null && distanceToDestination < 0`は、現在のNavigation destinationを正常に通過した
状態ではない。CATはこれをオーバーラン異常として扱い、脱線と同じ復旧状態へ入れる。復旧は既決のとおり、
原因が解消されたことを確認した上でのレンチ操作による。

この判定は、**現在の`navigation.destination`だけ**を対象にする。例えばAからCへNavigationしており、
途中にB駅のGlobalStation EdgePointが存在しても、destinationはCである。Bを通過してもCまでの
`distanceToDestination`は通常正のまま減少するため、オーバーラン異常にしてはならない。HUDがNavigation
残距離として表示する対象も、この場合はCである。

### 現在のCAT実装との照合

現在の`CreateTrainQueryUtil.queryNextStopDistance()`は、Navigation経路では
`destination != null`と有限値を確認して`distanceToDestination`をそのまま返す。一方、手動相当探索では
後方向距離へ負符号を付ける。このため現在の`StopTargetDistance`は取得元ごとに符号の意味が異なる。

- `NAVIGATION`：前方向・後方向とも通常は正。
- `MANUAL_CREATE_EQUIVALENT`：前方向は正、後方向は負。

また、`StopTargetDistance.available()`は有限性だけを検証し、0または負値もavailableとして受理する。
HUDの観測値としては保持できるが、Phase 5BのBrakingCurve入力としてそのまま利用するのは安全ではない。
Phase 5Bでは、取得元に依存しない非負の前方残距離と進行方向を別フィールドへ正規化するか、取得元ごとの
符号規約を明示した状態型へ変更する必要がある。

Phase 5Aの`NotchTestTrainState`と`TrainDataSnapshot`はログ観測のため
`Navigation.distanceToDestination`を直接読む。これらは制動判断ではないが、destinationなしの0や
Navigation開始失敗後に残り得る値を、Phase 5Bの有効な停止距離として流用してはならない。

本節は調査結果とPhase 5Bの入力条件を示すものであり、BrakingCurveの数式、0以下での最終制御動作、
到着状態を所有するコンポーネント、および既存CAT Java実装の変更を確定するものではない。
