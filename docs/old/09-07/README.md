# Create 自動運転・制動制御アドオン 設計方針

## 1. 前提条件

* Minecraft Java Edition: **1.20.1**
* Mod Loader: **Forge**
* Create: **6.0.8**
* 対象バージョンは上記に固定する
* 目的：

  * Create標準の列車自動運転を拡張する
  * 特に駅への進入・減速・停車を現実の鉄道に近づける
  * 将来的にATO・TASC・ノッチ制御・ATC/ATSなどへ発展させる
* 基本方針：

  * Createの列車システムをできるだけ利用する
  * `Train.speed` の直接操作は極力避ける
  * Create標準の `targetSpeed` と、自作制御器が算出する目標速度を分離する

---

# 2. なにを実装するか

## 2.1 自作列車制御器（Train Controller）

列車ごとに自作の制御状態を保持します。

主な役割：

* 列車の取得
* 現在速度の取得
* Create標準の `targetSpeed` の取得
* 自作目標速度の計算
* 駅までの距離の取得
* 制動曲線の計算
* 最終的な目標速度の決定
* 将来的なノッチ制御への対応

基本構造：

```text
Create Train
    ↓
Train Controller
    ↓
現在状態を取得
    ↓
速度・距離・制限等を計算
    ↓
atoTargetSpeed
    ↓
Create targetSpeed
    ↓
Createがspeedを追従
```

---

## 2.2 `createTargetSpeed` と `atoTargetSpeed` の分離

Create本来の目標速度と、自作制御器が計算した目標速度を分離します。

```text
createTargetSpeed
        ↓
自作ATO / 制動制御
        ↓
atoTargetSpeed
        ↓
train.targetSpeed
```

例えば、

```java
double createTargetSpeed;
double atoTargetSpeed;
```

を自作Controller側で保持します。

これにより、

* Createが本来要求している速度
* 自作ATOが実際に設定する速度

を区別できます。

---

## 2.3 制動曲線

単純に、

```text
駅に近づいた
↓
targetSpeed = 0
```

とするのではなく、

```text
現在速度
+
駅までの距離
+
想定減速度
+
空走時間
        ↓
制動開始位置
        ↓
目標速度
```

を計算します。

基本的な制動距離は、

$$
d = \frac{v^2}{2a}
$$

を利用します。

将来的には、

* 空走時間
* ブレーキ応答時間
* 勾配
* 車両性能
* 制動ノッチ
* 速度による制動性能変化

なども考慮します。

---

## 2.4 TASC（定位置停止制御）

駅の停止位置を基準として、列車を指定位置に正確に停車させます。

目標：

```text
駅接近
 ↓
制動開始
 ↓
制動曲線に沿って減速
 ↓
低速域で制動を調整
 ↓
停止位置へ進入
 ↓
0 km/h
```

特に、Create標準の停止処理で発生する「停止直前の不自然な減速」を改善することを目的とします。

---

## 2.5 ノッチ式制御

将来的には、現実の電車に近い制御状態を内部的に持たせます。

例：

```text
力行
P0
P1
P2
P3
P4
P5

惰行
N

ブレーキ
B1
B2
B3
B4
B5
B6
B7
B8

非常ブレーキ
EB
```

ATOは単純に速度を指定するのではなく、

```text
現在速度
目標速度
残距離
必要減速度
        ↓
制御量を決定
        ↓
ノッチを選択
        ↓
Createへの目標速度を決定
```

という構造にします。

---

## 2.6 速度制限・信号制御

将来的に以下を実装します。

* 線路の制限速度
* 信号現示
* 停止信号
* 前方列車
* 速度制限の変化
* 制限速度に対する制動曲線
* ATC風の速度照査
* ATS風の強制減速

最終的には、

```text
Schedule
   ↓
線路速度制限
   ↓
信号
   ↓
前方列車
   ↓
駅停止
   ↓
制動曲線
   ↓
ATO
   ↓
atoTargetSpeed
   ↓
Create Train
```

という制御体系を目指します。

---

# 3. それに必要なものは何か

## 3.1 開発環境

必要なもの：

* JDK 17
* Minecraft 1.20.1 Forge MDK
* ForgeGradle
* Create 6.0.8
* IntelliJ IDEA等のJava IDE
* Gradle

Create 6.0.8を開発環境の依存Modとして指定します。

---

## 3.2 Createから取得したい情報

### 基本状態

* `Train`
* Train UUID
* 現在速度 `speed`
* Create標準の `targetSpeed`
* 加速度
* 進行方向

### 列車構成

* Carriage一覧
* 編成長
* 車両数
* 将来的には車両ごとの性能

### 線路・位置

* 現在位置
* 線路上の位置
* 進行方向
* 駅までの距離
* 停止位置

### Schedule / Navigation

* Schedule状態
* 現在の目的地
* Navigation状態
* 目的地までの距離

---

## 3.3 自作側で保持する情報

列車UUIDをキーとしてControllerを管理します。

概念：

```text
UUID
 └─ TrainController
      ├─ createTargetSpeed
      ├─ atoTargetSpeed
      ├─ 現在の制御状態
      ├─ ブレーキ状態
      ├─ 制動曲線
      └─ TASC状態
```

将来的には、

```text
TrainController
 ├─ ATOController
 ├─ BrakeController
 ├─ SpeedLimitController
 ├─ SignalController
 └─ TASCController
```

のように分割します。

---

# 4. どのような問題がありそうか

## 4.1 Createの速度処理との競合

Create自身が、

```text
targetSpeed
    ↓
speed
```

を毎tick処理します。

そのため、自作Modが `speed` を直接変更すると、Create側の処理と競合する可能性があります。

### 対策

最初は、

```text
自作Mod
 ↓
atoTargetSpeed
 ↓
train.targetSpeed
 ↓
Create標準のspeed制御
```

とし、`speed` の直接操作を避けます。

---

## 4.2 `targetSpeed` がCreate側から変更される問題

CreateのScheduleやNavigationなどが `targetSpeed` を変更する可能性があります。

そのため、

```text
Create targetSpeed
        ↓
自作Modが取得
        ↓
自作制御
        ↓
atoTargetSpeed
        ↓
Create targetSpeed
```

という処理順序を慎重に設計する必要があります。

---

## 4.3 制動距離モデルが単純すぎる問題

基本式、

$$
d = \frac{v^2}{2a}
$$

だけでは現実の列車を十分に再現できません。

実際には、

* 列車重量
* ブレーキ性能
* 勾配
* 空走時間
* ブレーキ応答
* 速度による制動性能変化
* 制動ノッチ
* 車輪・レール間の粘着

などが影響します。

そのため、最初は単純な一定減速度モデルで実装し、後から高度化します。

---

## 4.4 Minecraftのtick処理

Minecraftは通常20 TPSで動作します。

通常は十分ですが、TPS低下時には制御周期が変化します。

そのため、

> 「必ず20回/秒で制御できる」

ことを前提にしすぎない設計が必要です。

---

## 4.5 Create 6.0.8の内部実装への依存

Createの内部クラスや処理順序に依存すると、別バージョンでは動作しなくなる可能性があります。

今回は、

**Minecraft 1.20.1 Forge + Create 6.0.8に固定**

するため、この問題は許容します。

開発時にはCreate 6.0.8のソースコードを基準にします。

---

## 4.6 Scheduleとの干渉

Create標準のScheduleはできるだけそのまま利用します。

自作ModがScheduleそのものを置き換えるのではなく、

```text
Schedule
 ↓
Createが目的地を決定
 ↓
自作ATOが速度制御
```

という関係にします。

これにより、Createの既存の駅・Scheduleシステムを利用できます。

---

## 4.7 停止位置の取得

「駅までの距離」と「列車を実際に停止させたい位置」は区別する必要があります。

TASCでは、

* Stationの位置
* 列車先頭位置
* 列車最後尾位置
* 列車中心位置
* 停止位置
* 編成長

を明確に区別する必要があります。

---

# 5. 実際にそれを実装する方法

## Phase 0：Create 6.0.8の解析

最初にCreate 6.0.8のソースコードを調査します。

主な対象：

```text
Train
GlobalRailwayManager
Navigation
ScheduleRuntime
Carriage
TravellingPoint
Station
Signal
```

特に以下を確認します。

* Trainのtick処理
* `speed` の更新
* `targetSpeed` の更新
* `approachTargetSpeed()`
* Navigationの更新
* Scheduleの更新
* Stationとの関係

---

# Phase 1：Train Inspector

いきなりATOを実装せず、まず列車の状態を確認するデバッグ機能を作ります。

例：

```text
Train Inspector

Train: 快速101
UUID: xxxx-xxxx

Speed: 60 km/h
Target Speed: 80 km/h

Carriages: 8
Length: 96 m

Destination: ○○駅
Distance: 1250 m

Direction: Forward
```

これにより、Create 6.0.8から実際に何を取得できるか確認できます。

---

# Phase 2：TrainController

列車UUIDごとにControllerを作ります。

```text
Train UUID
    ↓
TrainController
```

毎tick、対象列車の状態を取得します。

```text
Train
 ↓
TrainController.tick()
```

---

# Phase 3：`atoTargetSpeed` の導入

最初は、

```text
atoTargetSpeed = createTargetSpeed
```

とします。

これにより、自作ControllerがCreateの列車へ正常に介入できることを確認します。

その後、

```text
駅が近い
 ↓
atoTargetSpeedを徐々に下げる
```

という単純な制御を追加します。

---

# Phase 4：制動距離制御

現在速度を \(v\)、想定減速度を \(a\)、停止位置までの距離を \(d\) とします。

基本制動距離：

$$
d_b = \frac{v^2}{2a}
$$

を計算します。

概念：

```text
残距離 > 制動距離
    ↓
通常運転 / 惰行

残距離 ≈ 制動距離
    ↓
制動開始

残距離 < 制動距離
    ↓
制動を強める
```

---

# Phase 5：制動曲線

いきなり、

```text
80 → 0
```

とするのではなく、制動曲線から現在位置における目標速度を計算します。

例えば、

```text
駅まで500m → 80 km/h
駅まで400m → 75 km/h
駅まで300m → 67 km/h
駅まで200m → 55 km/h
駅まで100m → 39 km/h
駅まで 50m → 27 km/h
駅まで 10m → 12 km/h
駅まで  0m →  0 km/h
```

のような曲線を作ります。

この値を `atoTargetSpeed` とします。

---

# Phase 6：TASC

停止位置を明確に定義し、

```text
停止位置 - 現在位置
```

を利用して制動曲線を生成します。

停止直前では制動を弱め、停止位置で速度0になるように調整します。

---

# Phase 7：ノッチモデル

内部的に、

```text
P0～P5
N
B1～B8
EB
```

を導入します。

各ノッチに、

```text
加速度 / 減速度
```

を割り当てます。

例：

```text
B1 = -0.3 m/s²
B2 = -0.5 m/s²
B3 = -0.7 m/s²
...
B8 = -1.5 m/s²
EB = -2.5 m/s²
```

※実車を再現する場合は、対象車両の性能に合わせて設定します。

ATOは必要な減速度から適切なノッチを選択します。

---

# Phase 8：勾配補正

線路の勾配を取得し、

```text
上り
 ↓
自然減速が強い

下り
 ↓
自然減速が弱い / 加速する
```

ことを考慮します。

制動曲線を、

```text
列車性能
+
線路勾配
```

から計算します。

---

# Phase 9：速度制限・信号

最終的な目標速度を複数の制約から決定します。

概念：

```text
Scheduleによる速度
        ↓
線路速度制限
        ↓
信号による速度制限
        ↓
前方列車による制限
        ↓
駅停止による制限
        ↓
最も厳しい制限を採用
```

概念的には、

$$
v_{target}
=
\min(
v_{schedule},
v_{limit},
v_{signal},
v_{train},
v_{station}
)
$$

とします。

---

# 6. 最終的なシステム構成

```text
                 Create Schedule
                       │
                       ↓
                Create Navigation
                       │
                       ↓
                 TrainController
                       │
        ┌──────────────┼──────────────┐
        ↓              ↓              ↓
   Speed Limit      Signal          Station
        │              │              │
        └──────────────┼──────────────┘
                       ↓
                 Braking Curve
                       │
                       ↓
                      ATO
                       │
                       ↓
                 Brake Controller
                       │
                       ↓
              P0～P5 / N / B1～B8 / EB
                       │
                       ↓
                atoTargetSpeed
                       │
                       ↓
              train.targetSpeed
                       │
                       ↓
             Create speed control
                       │
                       ↓
                  Train movement
```

---

# 7. 開発上の基本方針

## 原則1：Createの列車システムを極力再利用する

Schedule、Navigation、Station、TrainなどはCreateのものを利用します。

---

## 原則2：`speed` の直接操作は極力避ける

最初は、

```text
自作制御
 ↓
atoTargetSpeed
 ↓
Create targetSpeed
 ↓
Create speed
```

とします。

---

## 原則3：Createの`targetSpeed`と自作目標速度を分離する

少なくとも、

```java
double createTargetSpeed;
double atoTargetSpeed;
```

を区別して管理します。

---

## 原則4：段階的に実装する

```text
Train取得
 ↓
速度表示
 ↓
targetSpeed介入
 ↓
制動距離
 ↓
制動曲線
 ↓
TASC
 ↓
ノッチ
 ↓
信号
 ↓
ATC / ATS
```

の順番で実装します。

---

## 原則5：Create 6.0.8に固定する

今回は互換性よりも、対象バージョンでの実現性・安定性を優先します。

---

# 8. 最初の実装目標

最初に完成させるべき最小機能は以下です。

```text
1. Createの列車を取得
2. Train UUIDを取得
3. 現在速度を取得
4. Create targetSpeedを取得
5. 駅までの距離を取得
6. 自作atoTargetSpeedを計算
7. train.targetSpeedへ反映
8. 列車が自然に減速・停車することを確認
```

この段階では、

* GUI
* ノッチ
* 信号
* 勾配
* 複雑な物理モデル
* ATC / ATS

は実装しません。

まずは、

> **Create標準の速度追従機構を維持したまま、目標速度の決定だけを自作制御器へ置き換える**

ことを最初の到達点とします。

---

# 9. 現時点での設計上の重要事項

今回のプロジェクトでは、`targetSpeed`を単純に上書きするだけではなく、

```text
Createが決定した目標速度
        ↓
createTargetSpeed
        ↓
自作制御器
        ↓
制動曲線・速度制限・駅停止等を考慮
        ↓
atoTargetSpeed
        ↓
train.targetSpeed
```

という**二段階の目標速度システム**を採用するのが望ましいです。

これにより、CreateのSchedule等を利用しながら、自作Mod側で「その目標速度へどのように到達するか」を制御できます。

将来的にノッチ制御やATC/ATOを追加する場合も、この構造を維持したまま拡張できます。
