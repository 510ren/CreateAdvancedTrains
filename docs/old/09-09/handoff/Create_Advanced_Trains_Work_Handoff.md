# Create: Advanced Trains — Work移行用コンテキスト

**Project:** Create: Advanced Trains  
**Mod ID:** `create_advanced_trains`

この文書は、ChatGPTの通常ChatからWorkへ開発を枝分かれさせるための**最小限の引き継ぎ資料**である。  
ここに書かれている「決定済み事項」は今後の実装・設計の前提として扱う。未決定事項は推測で埋めず、必要に応じてCreate 6.0.8の実ソースを確認する。

---

## 1. 固定開発環境

- Minecraft Java Edition **1.20.1**
- **Forge**
- Create **6.0.8**

ユーザーが明示しない限り、Minecraft / Forge / Create のバージョン変更を提案しない。

Create内部仕様について不明な場合は、別バージョンや一般知識ではなく、**Create 6.0.8 / Minecraft 1.20.1 の実ソースを基準**に確認する。

---

## 2. プロジェクト目的

Create列車を、現実の鉄道に近い制御へ拡張する。

対象機能：

- ATO
- TASC
- 制動曲線
- 制限速度制御
- 信号連携
- ATC / ATS 的速度制御
- CAT独自ノッチ制御
- 現実的な加速・惰行・制動
- 駅への滑らかな進入・停車
- 将来機能を追加しやすい拡張可能な構造

---

## 3. 基本アーキテクチャ

通常制御ではCreate本体の `Train.speed` を直接変更しない。

CAT側で各列車に `TrainController` を持たせ、Createが要求していた目標速度を保存しつつ、CAT側の最終目標速度を `Train.targetSpeed` に反映する。

```text
Create Schedule / Navigation
        ↓
TrainController
 ├─ Speed Limit
 ├─ Signal
 ├─ Station
 ├─ TASC
 ├─ Braking Curve
 ├─ ATC
 └─ ATO
        ↓
TargetSpeedResolver
        ↓
atoTargetSpeed
        ↓
train.targetSpeed
        ↓
Create標準の速度追従
        ↓
train.speed
```

概念：

```java
double createTargetSpeed;
double atoTargetSpeed;

createTargetSpeed = train.targetSpeed;
atoTargetSpeed = calculateTargetSpeed(train);
train.targetSpeed = atoTargetSpeed;
```

将来的には原則として、

```text
scheduleTargetSpeed
signalTargetSpeed
speedLimitTargetSpeed
stationTargetSpeed
brakingCurveTargetSpeed
atcTargetSpeed
        ↓
atoTargetSpeed = min(...)
```

とする。

### Createの `targetSpeed == 0` の扱い

Createの `targetSpeed == 0` を、そのままCATの最終目標速度0として扱わない。

Create側の0は、駅停車・信号停止など複数の停止理由を持ち得るため、CAT側では：

```text
Create targetSpeed = 0
        ↓
停止理由を判定
        ↓
station stop / signal stop / other
        ↓
TASC / Signal / BrakingCurve等で制御
```

とする。

将来の制御コンテキストでは最低限、

```text
createTargetSpeed
createStopRequest
stationDestination
distanceToStation
```

等を分離して保持する。

---

## 4. TrainController / 管理方針

列車ごとに `TrainController` を持たせる。

キーは `Train UUID`。

```text
Train UUID
    ↓
TrainController
```

主な責務：

- Train状態取得
- 現在速度
- Create側targetSpeed
- CAT側targetSpeed
- 駅・停止位置
- 制限速度
- 信号状態
- 制動曲線
- TASC
- ATO
- ノッチ
- 運転モード
- 非常制動状態
- デバッグ状態

1クラスに全ロジックを詰め込まず、機能ごとにクラス分離する。

推奨方向：

```text
train/
  TrainController
  TrainControllerManager
  TrainState

control/
  AtoController
  TascController
  BrakingCurve
  NotchController
  EmergencyBrakeController
  TargetSpeedResolver

speed/
signal/
station/
client/
mixin/
```

---

## 5. CAT制御対象列車

CATはすべてのCreate列車へ無条件に介入しない。

**CAT Controller Block（仮称）を編成内に1個以上搭載している列車だけをCAT制御対象とする。**

検出方針：

```text
Train
 ↓
Carriages
 ↓
各CarriageのContraption
 ↓
搭載Blockを走査
 ↓
CAT Controller Block?
 ├─ NO  → CAT非介入
 └─ YES → TrainController生成・維持
```

列車種別や特定アドオン名をハードコードしない。

他アドオン列車でも、Create互換の `Train → Carriage → Contraption → blocks` 構造を利用できるなら対応可能な設計を目指す。

未確認事項：

- Create 6.0.8で `Train → Carriage → Contraption → BlockState / BlockEntity` を取得する正確なAPI
- 組立後のブロック情報保持
- ローカル座標
- 他アドオン列車との互換性
- 複数CAT Controller Block搭載時の厳密な扱い

---

## 6. CAT独自の運転モード

Create内部フラグをCATの運転モードそのものとして使わない。

CAT側に独自状態を持つ。

```java
enum DrivingMode {
    AUTOMATIC,
    MANUAL
}
```

`TrainController` が列車UUIDごとに保持する。

Create側の以下は**判定材料**として利用するが、CATの真の状態ではない。

- `runtime.paused`
- `runtime.isAutoSchedule`
- Scheduleの有無
- Navigation状態
- 手動操作状態

未決定：

- AUTOMATIC → MANUAL の正確な遷移条件
- MANUAL → AUTOMATIC の復帰条件
- プレイヤーが運転席を離れた場合
- Schedule一時停止・完了
- Scheduleなし
- 複数プレイヤー
- ワールド再読み込み後の復元

この部分は別途「DrivingMode State Machine」として設計する。

---

## 7. 機能設定

現在の設定基盤：

```toml
[control]
enabled = true

[features]
ato = true
tasc = true
braking = true
notch = true
speed_limit = true
signal = true
atc = true
```

意味：

- `control.enabled = false`
  - CAT全体が列車制御へ介入しない
- `features.* = false`
  - その機能はグローバルに利用不可
- `available`
  - グローバル設定上利用可能
- `active`
  - 現在の運転状態で実際に有効

AUTOMATICでは、利用可能な自動制御機能を原則activeにする。

MANUALでは、利用可能な機能を個別ON/OFF可能にする。

---

## 8. 単位

Create `Train.speed` は **blocks/tick**。

```text
blocks/s = blocks/tick × 20
km/h     = blocks/tick × 72
blocks/tick = km/h ÷ 72
```

したがって：

```text
1 block/tick = 20 blocks/s = 72 km/h
```

HUD表示では内部のblocks/tickをそのまま表示せず、原則としてblocks/s等へ変換し単位を明示する。

Phase 5実装開始時には、生の `double` を乱用せず、可能なら：

```text
BlocksPerTick
BlocksPerSecond
KilometersPerHour
```

等の単位値オブジェクトを導入する。

---

## 9. Create 6.0.8で確認済みの速度物理

以下はCreate 6.0.8実ソースで確認済み。

### `Train.acceleration()`

```java
public float acceleration() {
    return (fuelTicks > 0
        ? AllConfigs.server().trains.poweredTrainAcceleration.getF()
        : AllConfigs.server().trains.trainAcceleration.getF()) / 400;
}
```

Createの設定加速度は blocks/s²。

標準 `trainAcceleration = 3` の場合：

```text
3 blocks/s²
÷ 400
= 0.0075 blocks/tick²
```

1 tickごとに `speed` へ0.0075 blocks/tickずつ加減算され、20 tickで3 blocks/sの速度変化となる。

### `approachTargetSpeed()`

Create標準は概ね：

```java
if (speed < actualTarget)
    speed = Math.min(speed + acceleration * accelerationMod, actualTarget);
else if (speed > actualTarget)
    speed = Math.max(speed - acceleration * accelerationMod, actualTarget);
```

つまり：

```text
targetSpeed
    ↓
approachTargetSpeed()
    ↓
speed ± acceleration
```

標準Createでは、加速と減速に独立した物理モデルはなく、基本的に同じ `acceleration()` を用いる。

### Create標準の制動距離

Navigationでは概ね：

```java
double acceleration = train.acceleration();
double brakingDistance =
    (train.speed * train.speed) / (2 * acceleration);
```

つまり基本式：

```text
d = v² / (2a)
```

Create標準自動運転では、制動距離内に入ると `targetSpeed = 0` として `approachTargetSpeed(1)` で減速する。

### 駅10 blocks以内の特殊補正

Create標準Navigationには、駅付近10 blocks以内で速度を残距離に応じて線形制限し、条件によって `train.speed` を直接補正する処理がある。

CATの独自TASC実装では、このCreate標準駅接近処理との競合・置換を考慮する必要がある。

### `tickPassiveSlowdown()`

Navigation destinationがなく、manualTickでなく、speed≠0の場合、Createは `acceleration()` を用いて受動減速する。

CAT制御時にはこの処理とのtick順序・競合も考慮する。

---

## 10. `approachTargetSpeed()` 介入

既存のNavigation側だけで `targetSpeed` を変更しても不十分なケースが確認されている。

`approachTargetSpeed()` はNavigationだけでなく `CarriageContraptionEntity` 側からも呼ばれる。

そのためCATのATO/ノッチ実装では、必要に応じて **`Train.approachTargetSpeed()` をMixin等で介入する設計**が必要。

ただし優先順位は：

1. Create公開API / フィールド
2. Forgeイベント
3. Mixin
4. Create内部処理置換

Mixinは必要最小限にし、Inject位置・tick順序・他Mod競合を確認する。

---

## 11. Phase 5 — 制動モデル

### 最重要開発ルール

**制動モデルの数学・仕様設計が完了するまで、BrakingCurve等のJava実装へ入らない。**

現在はPhase 5の数学設計中。

### BrakingCurveの責務

BrakingCurveは**最終targetSpeedではない**。

出力は：

> 現在の残距離と制動性能から、安全停止のために許される最大速度

```text
remaining distance
+
braking performance
+
response delay
+
safety margin
        ↓
BrakingCurve
        ↓
maximum permitted speed
```

最終的な速度は ATO / TargetSpeedResolver が各制約の最小値として決定する。

---

## 12. 制動モデル — 決定済み事項

### 基準

常用・非常制動とも、Createの `train.acceleration()` を基準値としてCAT独自特性を構築する。

ただし **B7倍率・EB倍率はまだ未決定**。

### ブレーキ応答時間

```text
10 tick = 0.5 s
```

で確定。

### 安全余裕

両方を使用する。

```text
distance margin
+
deceleration safety factor
```

具体値は未決定。

### 速度依存減速度

固定減速度ではなく：

```text
a = f(v)
```

とし、速度ごとの減速度テーブル方式を基本とする。

常用制動と非常制動は別テーブル。

### クリープ

停止直前に独立した低速制御領域を設ける。

```text
通常走行
 ↓
制動曲線
 ↓
低速制御
 ↓
クリープ
 ↓
完全停止
```

暫定クリープ速度：

```text
0.4 blocks/s
```

停止位置許容誤差：

```text
±0.5 blocks（暫定）
```

低速制御への切替速度は未決定。初期試験範囲：

```text
2〜10 blocks/s
```

### 勾配

V1では実質的に影響させない。

ただし将来対応できるよう `gradient` を独立入力として扱える設計にする。

### 編成重量・長さ

将来拡張項目。V1で実際に制動計算へ入れるかは未決定。

---

## 13. CAT独自ノッチ

CATのノッチはCreate標準W/S操作の単なるマッピングではなく、完全独自仕様。

```text
P1 P2 P3 P4 P5
N
B1 B2 B3 B4 B5 B6 B7
EB
```

将来的にはCreate標準の「W長押し加速」「S長押し逆方向加速」の直接操作を置換する。

基本構造：

```text
Notch
 ↓
requested acceleration / deceleration
 ↓
control
```

候補：

### 案A
各ノッチが独自の速度依存テーブル。

```text
B1 → a1(v)
...
B7 → a7(v)
EB → aEB(v)
```

### 案B
B7基準曲線＋倍率。

```text
B7 = aB7(v)
B6 = aB7(v) × k6
...
B1 = aB7(v) × k1
```

まだ決定しない。

---

## 14. 非常制動（EB）

### 手動運転

専用キーで発動可能。

単なる `targetSpeed = 0` ではなく独立したEmergency Brake制御として扱う。

### 自動運転

通常のATOではEBを使わず、TASC / BrakingCurve等により常用制動で安全停止する。

ただし：

> 現在速度・残距離の関係から、常用最大制動では安全停止不能

と判定された場合は自動EBを発動する。

厳密な発動式は未決定。

### EB発動後

自動運転中にEBが発動した場合、重大な異常状態として扱い、自動復帰させない。

候補状態：

```text
NORMAL
EMERGENCY_BRAKING
EMERGENCY_LOCKED
```

復旧にはプレイヤー操作（レンチ等）を要求する方向。

表示：

```text
非常制動発動
```

「脱線」とは表示しない。

---

## 15. TASCとの責務分離

TASC：

> どこに止めるか・停止位置までの残距離を扱う

BrakingCurve：

> 残距離と制動性能から最大許容速度を返す

概念：

```text
Station stop position
      ↓
TASC
      ↓
remaining distance
      ↓
BrakingCurve
      ↓
maximum permitted speed
      ↓
ATO / TargetSpeedResolver
```

Create側の正確なStation停止位置・Train位置基準はまだ要調査。

---

## 16. Phase 5で今後決める事項

以下はまだ仕様固定しない。

1. B7の具体的制動性能
2. EBの具体的制動性能
3. B1〜B6の関係
4. 速度依存テーブルの速度点
5. 各速度点の減速度
6. 区間補間方法
7. 速度依存減速度での制動距離計算アルゴリズム
8. 残距離 → 最大許容速度の逆算方法
9. 0.5秒応答を「完全な無制動時間」とするか、立ち上がり時間とするか
10. distance marginの具体値
11. deceleration factorの具体値
12. 低速制御への遷移条件
13. クリープ開始距離
14. クリープ終了条件
15. 正式な停止判定条件
16. 停止位置±0.5 blocksの妥当性
17. 自動EBの厳密な発動条件
18. EB解除・復旧条件
19. 駅停止位置の正確なCreate 6.0.8仕様

---

## 17. 実装順序

固定Phase：

```text
Phase 0  環境構築
Phase 1  Train取得 / UUID / speed / targetSpeed
Phase 2  TrainController
Phase 3  atoTargetSpeed → train.targetSpeed
Phase 4  Speed Limit
Phase 5  Braking Curve
Phase 6  TASC
Phase 7  Notch
Phase 8  Signal
Phase 9  ATO統合 + GUI/HUD/Debug
```

現在は **Phase 5の数学・仕様設計**。

Phase 5実装開始条件：

```text
Create速度物理確認
 ↓
B7 / EB
 ↓
減速度テーブル
 ↓
制動距離数学
 ↓
逆算アルゴリズム
 ↓
安全余裕
 ↓
低速 / クリープ / 停止
 ↓
EB判定
 ↓
仕様完成
 ↓
Java実装
```

---

## 18. デバッグ情報

将来的にサーバー側状態をクライアントへ表示する。

候補：

```text
Train ID
Current Speed
Create Target Speed
ATO Target Speed
Driving Mode
Station
Distance to Station
Speed Limit
Signal State
Braking Distance
Required Deceleration
Current Notch
Emergency Brake State
Controller State
```

HUDでは速度値をblocks/tickのまま表示せず、blocks/s等へ変換し単位明記する。

---

## 19. サーバー / クライアント

物理制御・ATO・TASC・制動曲線・運転モード等は原則サーバー側。

クライアントは主に：

- HUD
- GUI
- 速度表示
- ノッチ表示
- 信号表示
- デバッグ表示

を担当。

---

## 20. Work / Codexで守るルール

1. Minecraft 1.20.1 + Forge + Create 6.0.8固定。
2. 不明なCreate APIを推測で書かない。
3. Create 6.0.8実ソースを確認する。
4. 通常制御で `Train.speed` を直接変更しない。
5. `Train.targetSpeed` とCreate標準速度追従を基本とする。
6. ただし `approachTargetSpeed()` の多重呼び出し・Create標準駅接近処理との競合には注意する。
7. CAT運転モードはCAT独自状態。
8. CAT制御対象はCAT Controller Block搭載列車。
9. BrakingCurveは最大許容速度を返す。
10. ATO / TargetSpeedResolverが各制約を統合する。
11. CATノッチは完全独自仕様。
12. Mixinは必要最小限。
13. Phase 5数学が完成するまでBrakingCurveのJava実装を始めない。
14. 実装時はファイルパス、package、import、配置場所を明示する。
15. Javaの型安全性・責務分離・単位明示を優先する。
16. 実装後はビルド可能性を確認する。

---

# 情報源

## Create 6.0.8 公式ソース

確認対象ブランチ：`mc1.20.1-6.0.8`

- Train.java  
  https://raw.githubusercontent.com/Creators-of-Create/Create/mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/entity/Train.java

- Navigation.java  
  https://raw.githubusercontent.com/Creators-of-Create/Create/mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/entity/Navigation.java

- CarriageContraptionEntity.java  
  https://raw.githubusercontent.com/Creators-of-Create/Create/mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/entity/CarriageContraptionEntity.java

- ScheduleRuntime.java  
  https://raw.githubusercontent.com/Creators-of-Create/Create/mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/schedule/ScheduleRuntime.java

- TrainStatus.java  
  https://raw.githubusercontent.com/Creators-of-Create/Create/mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/TrainStatus.java

- CTrains.java  
  https://raw.githubusercontent.com/Creators-of-Create/Create/mc1.20.1-6.0.8/src/main/java/com/simibubi/create/infrastructure/config/CTrains.java

## このChatプロジェクト内で確定した主要設計

- CAT独自DrivingMode
- `createTargetSpeed` と `atoTargetSpeed` の分離
- Create `targetSpeed == 0` を停止理由として分類して処理
- CAT Controller Block搭載列車のみ制御
- Phase 5数学完成前にJava実装しない
- ブレーキ応答時間 10 tick = 0.5秒
- distance margin + deceleration safety factor
- 速度依存減速度テーブル
- クリープ 0.4 blocks/s 暫定
- 停止許容誤差 ±0.5 blocks 暫定
- CAT独自 P1〜P5 / N / B1〜B7 / EB
- 自動運転で常用制動不能時のみEB
- 自動EB後は異常ロック、表示「非常制動発動」

---

## Work開始時の推奨指示

このファイルをプロジェクト前提として読み、**記載済みの決定事項を勝手に変更しないこと**。  
未決定事項は推測で確定せず、必要ならCreate 6.0.8公式ソースを調査する。  
現在の主作業は **Phase 5 制動モデルの数学・仕様設計の続行** であり、ユーザーが仕様完成を明示するまでBrakingCurveのJava実装へ入らない。
