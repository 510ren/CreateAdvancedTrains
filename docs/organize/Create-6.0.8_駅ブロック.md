# Create 6.0.8（Forge / MC1.20.1）駅ブロックへの停車判定 解析

> 解析対象：`Creators-of-Create/Create` GitHubリポジトリ、タグ `mc1.20.1-6.0.8` のソースコードを直接取得して読解。
> 事実はソースからの直接確認、断定を避けた箇所は「推測」と明記。未確認・未調査の項目は末尾にまとめる。

---

## 目次

1. [駅ブロックの停車判定はどう実装されているか](#1-駅ブロックの停車判定はどう実装されているか)
2. [停止位置に許容誤差を持たせることは可能か](#2-停止位置に許容誤差を持たせることは可能か)
3. [未確認・要再調査の項目](#3-未確認要再調査の項目)

---

## 1. 駅ブロックの停車判定はどう実装されているか

### 1.1 結論

**駅の停車判定は信号（`SignalBoundary`）と全く同じ「エッジポイント通過検出」の仕組みに乗っている。** `StationBlockEntity` 自体は停車判定そのものを行っておらず、UI演出・自動スケジュール適用・ComputerCraft連携のためのフラグ管理のみを担当する。

### 1.2 `GlobalStation` はグラフ上の「エッジポイント」の一種

```java
public class GlobalStation extends SingleBlockEntityEdgePoint { ... }
// SingleBlockEntityEdgePoint extends TrackEdgePoint
```

`SignalBoundary`（信号）と同じ抽象基底 `TrackEdgePoint` を継承し、トラックグラフの「エッジ（区間）上の特定位置」として登録される。`SignalBoundary` が前後2面（`Couple`）を持つのに対し、`GlobalStation` は `SingleBlockEntityEdgePoint`（`canMerge() = false`）で**1方向・1ブロックエンティティのみに対応**する点が異なる。

### 1.3 実際に停止させているコード（`Train#frontSignalListener`）

`content/trains/entity/Train.java` 437〜450行：

```java
public IEdgePointListener frontSignalListener() {
    return (distance, couple) -> {
        if (couple.getFirst() instanceof GlobalStation station) {
            if (!station.canApproachFrom(couple.getSecond().getSecond())
                || navigation.destination != station)
                return false;
            speed = 0;
            navigation.distanceToDestination = 0;
            navigation.currentPath.clear();
            arriveAt(navigation.destination);
            navigation.destination = null;
            return true;
        }
        ...
    };
}
```

発火条件は2つ：

1. `station.canApproachFrom(side)` が `true`（§1.6参照）
2. `navigation.destination == station`（**この駅が現在のナビゲーション目的地であること**。通過するだけの駅では発火しない）

満たされれば `speed` を即座に `0` へハード代入し、`arriveAt()` を呼ぶ。**じわじわ0に収束して止まるのではなく、この瞬間に離散的にゼロクランプされる。**

### 1.4 `frontSignalListener` が呼ばれるタイミング（`TravellingPoint#edgeTraversedFrom`）

`frontSignalListener`／`backSignalListener` は信号・駅・観測ブロック共通の汎用コールバック（`IEdgePointListener`）で、実際に呼び出すのは `TravellingPoint#travel` 内部の `edgeTraversedFrom`。

```java
protected Double edgeTraversedFrom(TrackGraph graph, boolean forward, IEdgePointListener edgePointListener,
    ITurnListener turnListener, double prevPos, double totalDistance) {
    ...
    for (int i = 0; i < edgePoints.size(); i++) {
        TrackEdgePoint nextBoundary = edgePoints.get(index);
        double locationOn = nextBoundary.getLocationOn(edge);
        double distance = forward ? locationOn : length - locationOn;
        if (/* このtickの移動区間 [from, to] に含まれないなら */) continue;
        if (edgePointListener.test(totalDistance + distance, Pair.of(nextBoundary, ...)))
            return locationOn;   // ← ここが重要
    }
    return null;
}
```

呼び出し元の `travel()` では、そのtickの移動距離ぶん先の `position` を**先に仮計算**してから、移動区間内に存在するエッジポイント（信号・駅・観測ブロック）を順番にチェックする。リスナーが `true` を返すと：

```java
Double blockedLocation = edgeTraversedFrom(...);
if (blockedLocation != null) {
    position = blockedLocation;         // = locationOn に強制スナップ
    traveled = position - prevPos;      // そのtickの残り移動距離は破棄
    return traveled;
}
```

**「列車がホームでピタリと止まる」のは、物理演算が自然に速度0へ収束した結果ではなく、先頭car端点がその瞬間の移動でちょうど駅の登録位置をまたいだ時に、位置を強制的にスナップして速度を0にする離散イベント**である。

`TrackEdgePoint#getLocationOn` は駅設置時に確定する固定値：

```java
public double getLocationOn(TrackEdge edge) {
    return isPrimary(edge.node1) ? edge.getLength() - position : position;
}
```

ここには「近ければOK」という幅の概念は一切ない。これが**誤差0ブロック**の正体。

### 1.5 誰が「先頭」判定に関わるか（`Carriage#travel` / `Train#tick`）

```java
int carriageType = first ? (last ? Carriage.BOTH : Carriage.FIRST) : (last ? Carriage.LAST : Carriage.MIDDLE);
```

`frontSignalListener`/`backSignalListener` が実際にアタッチされるのは、**列車全体で最初の車両の最前輪（`FIRST`/`BOTH`）と、最後の車両の最後輪（`LAST`/`BOTH`）のみ**。中間車両・内側の輪は `point.ignoreEdgePoints()`（passiveListener）が使われ、駅や信号には一切反応しない。したがって**駅の停止判定に関与するのは列車全体でただ1点（先頭車の先端）のみ**で、後続車両の位置は「前の点に追従する」物理計算（`point.follow(prevPoint)`）だけで決まる。

### 1.6 進入方向の判定（`GlobalStation#canApproachFrom`）

```java
public boolean canApproachFrom(TrackNode side) {
    return isPrimary(side) && !assembling;
}
```

- `isPrimary(side)`：駅設置時に決まる「正面方向」と一致するかの判定（基底 `TrackEdgePoint` 実装）。**逆向きから駅を通過してもこの判定は `false` になり、停止イベントは発火しない**（素通り）。
- `assembling`：プレイヤーが組立モード中は進入不可。

### 1.7 到着前の「先読み」減速（`Navigation#tick` の `signalScout`）

実際の物理停止（§1.3〜1.4）とは別に、**仮想の先読みポイント `signalScout`** が制動距離（`brakingDistance = speed²/(2·acceleration)`）＋α先まで毎tick走査し、目的地駅を事前に検出する：

```java
if (boundary == destination && ((GlobalStation) boundary).canApproachFrom(nodes.getSecond()))
    return true;  // スキャン終了＝ここより先は見なくてよい
```

この先読み結果をもとに `targetSpeed` が「残距離に応じた減速カーブ」を作り（駅手前10ブロックでは `maxApproachSpeed = topSpeed * (targetDistance/10)` で漸減）、実際に列車がその速度で走った結果、§1.3〜1.4の物理エンジンがちょうど駅の位置でスナップして止まる。**「先読みで滑らかに減速」＋「実移動で正確にスナップ」の二層構造**になっている。

### 1.8 複数列車が同じ駅を目指す場合の調停（`GlobalStation#reserveFor`/`getImminentTrain`）

```java
public void reserveFor(Train train) {
    Train nearestTrain = getNearestTrain();
    if (nearestTrain == null
        || nearestTrain.navigation.distanceToDestination > train.navigation.distanceToDestination)
        this.nearestTrain = new WeakReference<>(train);
}

public Train getImminentTrain() {
    Train nearestTrain = getNearestTrain();
    if (nearestTrain == null) return nearestTrain;
    if (nearestTrain.getCurrentStation() == this) return nearestTrain;
    if (!nearestTrain.navigation.isActive()) return null;
    if (nearestTrain.navigation.distanceToDestination > 30) return null;
    return nearestTrain;
}
```

- 駅は `WeakReference<Train> nearestTrain` を1つだけ持ち、`reserveFor` で「残距離が短い方」を優先的に記録する（複数列車が同じ駅を目的地にしても、停止判定自体（§1.3）は距離とは無関係に個々の列車が独立して行う）。
- 「30ブロック以内」というしきい値は `StationBlockEntity#tick` のUI演出（旗の上下アニメーション `flag.chase`）や自動スケジュール適用（`applyAutoSchedule`）、ComputerCraftイベント送出のトリガーとして使われるだけで、**物理的な停止判定には一切関与しない**。

---

## 2. 停止位置に許容誤差を持たせることは可能か

### 2.1 結論

実装上は可能だが、**「`arriveAt()` を独自に呼ぶ」だけでは不十分で、位置クランプ処理（§1.4）そのものを書き換える必要がある**。結果として、多くの場合 `arriveAt()` の呼び出しも自分で管理することになる。

### 2.2 なぜ「`arriveAt()`を呼ぶだけ」では不十分か

`frontSignalListener` のGlobalStation分岐は、以下の3つの処理が**同一ラムダの中で不可分に結合**している：

| 処理 | 内容 |
|---|---|
| ① 物理的な停止 | `speed = 0` |
| ② 論理的な到着処理 | `arriveAt(navigation.destination)` |
| ③ 位置の強制スナップ | 戻り値 `true` により `edgeTraversedFrom` が `position` を `locationOn` にクランプ |

- 「①③はそのままにして②だけ自分で追加で呼ぶ」→ 既に元のコードが `arriveAt()` を呼んでいるため**二重呼び出し**になり無意味。
- 「③（誤差ゼロのスナップ）を止めて誤差許容にしたい」→ このリスナー自体を差し替えるしかなく、差し替えた以上①②も**自分で書き直す**ことになる。

このメソッドは `public IEdgePointListener frontSignalListener()` として**ラムダを返すだけの関数**であり、Mixinで中の条件式だけを差し替えるのは技術的に困難（匿名ラムダの合成メソッドを狙う必要があり、難読化・バージョン依存で壊れやすい）。現実的な介入ポイントは以下の2案。

### 2.3 案A：`Carriage#travel` の `frontSignalListener()` 取得箇所を `@Redirect` する

```java
IEdgePointListener frontListener = train.frontSignalListener();  // ← ここをRedirect
```

自作の `IEdgePointListener` を返し、内部で `GlobalStation` 分岐だけ「残距離が±0.5ブロック以内になった時点で `speed=0; arriveAt(...)`」というロジックに差し替え、それ以外（信号・観測ブロック）は元のリスナーにそのまま委譲する。

- **①②③すべてを自分で管理することになる**ため、`arriveAt()` を独自に呼ぶ実装が必要。
- 制御の自由度は高い（誤差判定のタイミングや条件を細かく作り込める）。
- 元のラムダのロジック（信号・観測ブロック分岐）を自前で再現または委譲する必要があり、実装量はやや多い。

### 2.4 案B：`edgeTraversedFrom` 側で `locationOn` にオフセットを加える

`nextBoundary instanceof GlobalStation` の場合のみ `locationOn` に ±0.5 の範囲でオフセットを加えてから判定・返却する。

- ①②③のロジック自体（`frontSignalListener`）はそのまま動くので、**`arriveAt()` を自分で呼ぶ必要はない**。
- 実装量は少ない一方、以下の懸念点がある（未検証）：
  - 同じエッジポイントに対してtickごとに異なるオフセット値を計算すると、ある tick では「まだ手前」判定、次の tick では「もう通過済み」判定のようにブレる可能性がある。1回の到着イベントにつき一度だけ決定したオフセット値をキャッシュする必要がある。
  - オフセットが原因で `locationOn` がエッジ長 `edge.getLength()` を超える／0を下回ると、隣接エッジへの越境が絡む可能性があり、境界チェック（`Mth.clamp`等）が別途必要と考えられる。

### 2.5 補足：接近速度との整合性

駅手前では `Navigation#tick` が `maxApproachSpeed = topSpeed * (targetDistance / 10)` で減速するため、残り0.5ブロック付近では既に速度がかなり低い状態にある。そのため「±0.5ブロックの判定窓を1tickの移動量が飛び越えてしまう」リスクは通常低いが、手動運転や加速度を大きくいじった編成では飛び越える可能性があるため、**厳密な等値判定ではなく「区間の通過」で判定する**設計（案Bはこれに近い）の方が安全と考えられる（推測）。

### 2.6 案A・案Bの比較

| 観点 | 案A（リスナー差し替え） | 案B（位置オフセット） |
|---|---|---|
| `arriveAt()`の独自呼び出し | 必要 | 不要 |
| 実装量 | 多い（①②③すべて自前実装） | 少ない（オフセット計算のみ） |
| 制御の自由度 | 高い | 低い（既存ロジックに依存） |
| 判定のブレるリスク | 自分で制御できる | オフセットのtick間一貫性に注意が必要 |
| 境界越えの考慮 | 自前実装内で対応 | 別途 `Mth.clamp` 等が必要と考えられる |

---

## 3. 未確認・要再調査の項目

- ホームの長さ（複数車両編成）が実際にどこまで厳密に検証されているか（プレイヤーへの警告や自動判定があるか）は未確認。
- `assembling`状態（組立モード）と停車判定の詳細な相互作用（組立中に他の列車がその駅を目的地にした場合の挙動）は未確認。
- `StationBlock.java`（ブロック本体・`ASSEMBLING`プロパティ・レッドストーン等）は未読。
- `arriveAt()`後、`ScheduleRuntime`側の`destinationReached()`〜`POST_TRANSIT`遷移との具体的な接続は簡単に触れたのみで深掘りしていない。
- パッケージポート（`GlobalPackagePort`）や郵便連携（`runMailTransfer`）は停車判定と直接関係しないため割愛。
- 案A・案Bとも実装コードは提示しておらず、方針レベルの整理に留まる。

---

*本ドキュメントは Create Mod 6.0.8（Forge, MC1.20.1）のソースコード直読に基づく。*
