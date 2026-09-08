# Create: Advanced Trains — Work移行用プロジェクト引継ぎ資料

## 0. この資料の目的

このファイルは、ChatGPT上で進めてきた **Create: Advanced Trains（CAT）** の開発内容を、ChatGPT Workへ移行するための引継ぎ資料です。

**重要:** 制動モデル、BrakingCurve、TASC、非常制動、CAT Controller Block、DrivingModeの詳細仕様、Create 6.0.8の制動物理調査などは、先に作成済みの「制動モデル実装整理」資料にまとめられているため、本資料では原則として重複を避けています。制動関連資料側では、数学仕様確定後にJava実装へ移る方針が明示されています。fileciteturn7file4

本資料では主に以下を扱います。

- 固定開発環境
- Modの基本情報
- Phase 0〜4までの実装状況
- TrainController / ATO / Speed Limit基盤
- Createへの速度介入方式とMixin
- HUD / Network / Debug基盤
- Forge Config基盤
- テストコマンド
- 単位方針
- ビルド方法
- Work / Codex移行時の開発ルール

---

# 1. プロジェクト基本情報

```text
Project: Create: Advanced Trains
Mod ID: create_advanced_trains
Package root: dev.edudio.createadvancedtrains
Group ID: dev.edudio.createadvancedtrains
Author: Edudio
License: All Rights Reserved
```

目的はCreate Modの列車に、高度な自動運転・保安装置・現実寄りの運転挙動を追加することです。

将来対象:

```text
ATO
TASC
Braking Curve
Speed Limit
Signal
ATC / ATS
Notch Control
HUD / GUI / Debug
```

一つの巨大Controllerに全機能を詰め込まず、責務を分離することを前提とします。

---

# 2. 固定開発環境

ユーザーが明示的に変更を要求しない限り、以下を固定します。

```text
Minecraft Java Edition 1.20.1
Forge 47.4.10
Create 6.0.8
Java 17
Gradle 8.8
IDE: VS Code
```

プロジェクトパス:

```text
C:\Products\CreateAdvancedTrains
```

Java 17:

```text
C:\Program Files\Java\jdk-17
```

ユーザーはシステム全体のJavaを変更したくないため、Gradle実行時は必要に応じてPowerShellで:

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
```

を設定します。

`.vscode/settings.json`:

```json
{
  "java.jdt.ls.java.home": "C:\\Program Files\\Java\\jdk-17",
  "java.import.gradle.java.home": "C:\\Program Files\\Java\\jdk-17"
}
```

`gradle.properties`:

```properties
org.gradle.java.home=C:\\Program Files\\Java\\jdk-17
```

---

# 3. Create 6.0.8 開発依存関係

主要バージョン:

```properties
create_version=6.0.8-289
ponder_version=1.0.91
flywheel_version=1.0.5
registrate_version=MC1.20-1.3.3
```

主要repository:

```gradle
repositories {
    maven { url = "https://maven.createmod.net" }
    maven { url = "https://maven.ithundxr.dev/mirror" }
    maven { url = "https://raw.githubusercontent.com/Fuzss/modresources/main/maven/" }
}
```

主要dependency:

```gradle
dependencies {
    implementation(fg.deobf("com.simibubi.create:create-${minecraft_version}:${create_version}:slim") {
        transitive = false
    })

    implementation(fg.deobf("net.createmod.ponder:Ponder-Forge-${minecraft_version}:${ponder_version}"))
    compileOnly(fg.deobf("dev.engine-room.flywheel:flywheel-forge-api-${minecraft_version}:${flywheel_version}"))
    runtimeOnly(fg.deobf("dev.engine-room.flywheel:flywheel-forge-${minecraft_version}:${flywheel_version}"))
    implementation(fg.deobf("com.tterrag.registrate:Registrate:${registrate_version}"))

    compileOnly(annotationProcessor("io.github.llamalad7:mixinextras-common:0.4.1"))
    implementation("io.github.llamalad7:mixinextras-forge:0.4.1")
}
```

Create dev run用:

```gradle
property 'mixin.env.remapRefMap', 'true'
property 'mixin.env.refMapRemappingFile', "${projectDir}/build/createSrgToMcp/output.srg"
```

---

# 4. Modメインクラス

```java
package dev.edudio.createadvancedtrains;

import dev.edudio.createadvancedtrains.config.AdvancedTrainsConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(CreateAdvancedTrains.MOD_ID)
public class CreateAdvancedTrains {

    public static final String MOD_ID = "create_advanced_trains";

    public CreateAdvancedTrains() {
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.SERVER,
                AdvancedTrainsConfig.SPEC
        );
    }
}
```

Forgeテンプレートに最初から存在した旧 `Config.java` は削除済みです。旧クラスが `Cannot get config value before config is loaded.` の原因になっていました。

---

# 5. 基本アーキテクチャ

```text
Create Schedule / Navigation
        ↓
TrainController
        ↓
CAT各種制約
        ↓
atoTargetSpeed
        ↓
Train.targetSpeed
        ↓
Create標準速度追従
        ↓
Train.speed
```

通常制御では `Train.speed` を直接変更しません。

Create側が本来要求した値は:

```java
createTargetSpeed
```

として保持し、CAT側の要求値:

```java
atoTargetSpeed
```

と分離します。

---

# 6. Phase進行状況

## Phase 0 — 開発環境

**完了。**

- Forge 1.20.1 起動
- Create 6.0.8依存追加
- 開発ラン起動確認
- Release Jarビルド確認

## Phase 1 — Train取得

**完了。**

取得確認済み:

```text
Train UUID
Train.speed
Train.targetSpeed
```

サーバー側列車一覧:

```java
Create.RAILWAYS.sided(level).trains
```

## Phase 2 — TrainController

**完了。**

```text
Train UUID
    ↓
TrainController
```

で列車単位管理。

## Phase 3 — ATO targetSpeed介入

**完了。**

CAT計算値を `Train.targetSpeed` に反映可能。

## Phase 4 — Speed Limit

**基本実装完了・動作確認済み。**

- `SpeedLimitController`
- `SpeedLimitSource`
- 複数制約の最小値
- blocks/s API
- テストコマンド
- ConfigによるON/OFF

設定基盤の実装内容はアップロード資料にも残っています。fileciteturn7file0

## Phase 5

**未実装。**

制動モデル数学・仕様を別資料で確定してからJava実装へ進みます。

---

# 7. Create 6.0.8で使用している主要要素

```java
Create.RAILWAYS
GlobalRailwayManager
Train
Train.id
Train.speed
Train.targetSpeed
Train.navigation
Train.runtime
Train.carriages
```

Create内部APIは推測で扱わず、必要時にCreate 6.0.8実ソースを確認します。

---

# 8. TrainController

現在の中心クラスです。

責務:

```text
Train UUID保持
Create targetSpeed保持
CAT targetSpeed保持
AtoController保持
TrainState更新
ATO介入
```

代表形:

```java
public class TrainController {

    private final UUID trainId;
    private final AtoController atoController;

    private TrainState state;

    private double createTargetSpeed;
    private double atoTargetSpeed;

    public TrainController(Train train) {
        this.trainId = train.id;
        this.atoController = new AtoController();
        this.createTargetSpeed = train.targetSpeed;
        this.atoTargetSpeed = train.targetSpeed;
        updateState(train);
    }

    public void applyAtoTargetSpeed(Train train) {
        if (!AdvancedTrainsConfig.CONTROL_ENABLED.get())
            return;

        if (!AdvancedTrainsConfig.ATO_ENABLED.get())
            return;

        createTargetSpeed = train.targetSpeed;
        atoTargetSpeed = atoController.calculateTargetSpeed(createTargetSpeed);
        train.targetSpeed = atoTargetSpeed;

        updateState(train);
    }

    public void update(Train train) {
        updateState(train);
    }

    private void updateState(Train train) {
        state = new TrainState(
                train.speed,
                createTargetSpeed,
                atoTargetSpeed
        );
    }
}
```

重要: `applyAtoTargetSpeed()` はLevelTickから無条件に実行するのではなく、Createが実際に速度追従を行う `approachTargetSpeed()` 直前へ介入します。

---

# 9. TrainControllerManager

列車UUIDごとにControllerを保持します。

```java
public class TrainControllerManager {

    public static final TrainControllerManager INSTANCE =
            new TrainControllerManager();

    private final Map<UUID, TrainController> controllers =
            new HashMap<>();
}
```

主要API:

```text
getOrCreate(Train train)
get(UUID trainId)
remove(UUID trainId)
clear()
size()
update(Train train)
updateAll(Iterable<Train> trains)
removeMissing(Iterable<Train> trains)
setSpeedLimitForAll(...)
```

テストコマンドから全Controllerへ速度制限を配布し、動作確認済みです。

---

# 10. TrainState

```java
public record TrainState(
        double speed,
        double createTargetSpeed,
        double atoTargetSpeed
) {}
```

将来Typed Unitを導入すると変更する可能性があります。

---

# 11. AtoController

現在は主にSpeedLimitControllerを内包しています。

```java
public class AtoController {

    private final SpeedLimitController speedLimitController;

    public AtoController() {
        this.speedLimitController = new SpeedLimitController();
    }

    public SpeedLimitController getSpeedLimitController() {
        return speedLimitController;
    }

    public double calculateTargetSpeed(double createTargetSpeed) {
        if (!AdvancedTrainsConfig.SPEED_LIMIT_ENABLED.get())
            return createTargetSpeed;

        return speedLimitController.apply(createTargetSpeed);
    }
}
```

将来、AtoControllerへ全機能を詰め込まず、Controller / Resolverへ分割します。

---

# 12. SpeedLimitSource

```java
public enum SpeedLimitSource {
    LINE,
    TEMPORARY,
    SIGNAL,
    STATION,
    ATC,
    ATS,
    TEST
}
```

文字列ではなくenumで制約源を管理します。

---

# 13. SpeedLimitController

CAT公開側は `blocks/s`、Create境界のみ `blocks/tick` です。

```java
public class SpeedLimitController {

    private static final double TICKS_PER_SECOND = 20.0;

    private final EnumMap<SpeedLimitSource, Double> limits =
            new EnumMap<>(SpeedLimitSource.class);

    public void setLimit(SpeedLimitSource source, double speedLimit) {
        if (source == null)
            throw new IllegalArgumentException("Speed limit source must not be null");

        if (!Double.isFinite(speedLimit) || speedLimit < 0.0)
            throw new IllegalArgumentException("Speed limit must be finite and >= 0");

        limits.put(source, speedLimit);
    }

    public double getEffectiveLimit() {
        double effectiveLimit = Double.POSITIVE_INFINITY;

        for (double limit : limits.values())
            effectiveLimit = Math.min(effectiveLimit, limit);

        return effectiveLimit;
    }

    public double apply(double createTargetSpeed) {
        double effectiveLimitBlocksPerSecond = getEffectiveLimit();
        double createTargetSpeedBlocksPerSecond = createTargetSpeed * TICKS_PER_SECOND;

        double limitedSpeedBlocksPerSecond = Math.min(
                Math.abs(createTargetSpeedBlocksPerSecond),
                effectiveLimitBlocksPerSecond
        );

        double limitedSpeedBlocksPerTick =
                limitedSpeedBlocksPerSecond / TICKS_PER_SECOND;

        return Math.copySign(limitedSpeedBlocksPerTick, createTargetSpeed);
    }
}
```

---

# 14. ATO介入方式とMixin

当初はNavigation側だけへの介入を検討しましたが、それでは不十分でした。

理由:

Createの `Train.approachTargetSpeed()` はNavigationだけでなく、手動運転側の `CarriageContraptionEntity` からも呼ばれるためです。

現在の方式:

```java
@Mixin(Train.class)
public abstract class TrainMixin {

    @Inject(
        method = "approachTargetSpeed",
        at = @At("HEAD"),
        remap = false
    )
    private void createAdvancedTrains$applyAto(
            float accelerationMod,
            CallbackInfo ci
    ) {
        Train train = (Train) (Object) this;

        TrainController controller =
                TrainControllerManager.INSTANCE.getOrCreate(train);

        controller.applyAtoTargetSpeed(train);
    }
}
```

これにより:

```text
Navigation
    ↓
approachTargetSpeed()

Manual control / CarriageContraptionEntity
    ↓
approachTargetSpeed()
```

の両方を捕捉できます。

注意: `approachTargetSpeed(float)` の引数は target speed ではなく `accelerationMod` です。

---

# 15. LevelTickの役割

LevelTick等では:

```text
Controller生成
Controller state更新
HUD / Debug state更新
不要Controller除去
```

を行います。

**ATO速度介入そのものはLevelTickで行いません。**

authoritativeな介入箇所は:

```text
Train.approachTargetSpeed()
```

です。

---

# 16. Forge Config基盤

設定クラス:

```text
src/main/java/dev/edudio/createadvancedtrains/config/AdvancedTrainsConfig.java
```

Server Configとして登録済み。

現在の設定:

```text
control.enabled
features.ato
features.tasc
features.braking
features.notch
features.speed_limit
features.signal
features.atc
```

概念:

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

意味:

```text
control.enabled = false
→ CAT全体がTrain.targetSpeedへ介入しない

features.ato = false
→ ATO介入しない

features.speed_limit = false
→ ATOは有効でもSpeedLimitControllerを通さない
```

この設定設計は先に作成した環境構築資料にも記録されています。fileciteturn7file15

---

# 17. Speed Limitテストコマンド

基本コマンド:

```text
/create_advanced_trains speed_limit test set <value>
```

実装では:

```java
TrainControllerManager.INSTANCE.setSpeedLimitForAll(
        SpeedLimitSource.TEST,
        value
);
```

を呼びます。

全Controllerへの速度制限反映を確認済みです。

---

# 18. HUD / Network / Debug基盤

Phase 1でServer側Train情報をClient HUDへ同期する基盤を作成済みです。

```text
network/
├─ ModNetwork.java
└─ TrainDebugPacket.java

train/
├─ TrainDebug.java
├─ TrainDebugData.java
├─ TrainDebugEvents.java
└─ client/
   ├─ TrainDebugClientData.java
   └─ TrainDebugOverlay.java
```

Debug Data:

```java
public record TrainDebugData(
        UUID trainId,
        double speed,
        double createTargetSpeed,
        double atoTargetSpeed
) {}
```

Packet送信:

```java
PacketDistributor.PLAYER.with(() -> player)
```

現在のHUDで主に確認する項目:

```text
Train ID
Speed
Create Target
ATO Target
```

---

# 19. 単位方針

CAT公開API / Config / Debugでは原則:

```text
速度: blocks/s
加速度・減速度: blocks/s²
距離: blocks
時間: seconds
```

Create境界:

```text
Train.speed: blocks/tick
Train.targetSpeed: blocks/tick
```

変換:

```text
blocks/s = blocks/tick × 20
blocks/tick = blocks/s ÷ 20
km/h = blocks/tick × 72
blocks/tick = km/h ÷ 72
```

将来は単なる `double` だけで単位を表さず、Value Object導入を予定しています。

候補:

```text
BlocksPerTick
BlocksPerSecond
KilometersPerHour
Acceleration
Distance
Duration
```

制動モデル側資料にもTyped Unit導入方針が記録されています。fileciteturn7file13

---

# 20. Server / Client責務

## Server

```text
Train取得
TrainController
ATO
TASC
Braking
Speed Limit
Signal
ATC / ATS
Notch状態
物理制御
```

## Client

```text
HUD
GUI
速度表示
ノッチ表示
信号表示
Debug
入力UI
```

列車の物理制御ロジックは原則Server側です。

---

# 21. Createとの競合に関する重要ルール

Create自身も以下から速度を変更します。

```text
Train.tick()
Navigation.tick()
CarriageContraptionEntity
Train.approachTargetSpeed()
```

したがってForge Tickで単純に:

```java
train.targetSpeed = ...;
```

とするだけではCreateに上書きされる可能性があります。

現在は:

```text
CreateがapproachTargetSpeed()を呼ぶ
        ↓
Mixin HEAD
        ↓
CATがtargetSpeed補正
        ↓
Create本来のapproachTargetSpeed()
```

の順序で介入します。

Phase 3〜4では、この方式で実際に速度制限が機能することを確認済みです。

---

# 22. Release Build

```powershell
cd C:\Products\CreateAdvancedTrains
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
.\gradlew clean build
```

生成物:

```text
C:\Products\CreateAdvancedTrains\build\libs\
```

Release Jarの生成は確認済みです。

---

# 23. 軽量化Modについて

開発環境で以下を試したことがあります。

```text
Embeddium
EMI
Entity Culling
FerriteCore
Jade
JEI
ModernFix
```

EmbeddiumはCreate 6.0.8 + Forge 47.4.10環境でMixin crashを起こしました。

そのため開発時は軽量化Modを極力入れず、Create + CAT中心で検証します。

---

# 24. 現在想定しているpackage構成

```text
dev.edudio.createadvancedtrains
├─ CreateAdvancedTrains.java
│
├─ command/
│  └─ ChangeSpeedLimitCommands.java
│
├─ config/
│  └─ AdvancedTrainsConfig.java
│
├─ control/
│  └─ AtoController.java
│
├─ mixin/
│  └─ TrainMixin.java
│
├─ network/
│  ├─ ModNetwork.java
│  └─ TrainDebugPacket.java
│
├─ speed/
│  ├─ SpeedLimitController.java
│  └─ SpeedLimitSource.java
│
└─ train/
   ├─ TrainController.java
   ├─ TrainControllerManager.java
   ├─ TrainState.java
   ├─ TrainDebug.java
   ├─ TrainDebugData.java
   ├─ TrainDebugEvents.java
   └─ client/
      ├─ TrainDebugClientData.java
      └─ TrainDebugOverlay.java
```

将来的な責務分離は、制動モデル側資料を正とします。

---

# 25. TargetSpeedResolver構想

現在:

```text
TrainController
    ↓
AtoController
    ↓
SpeedLimitController
```

将来:

```text
SpeedLimitController
TascController
BrakingCurve
SignalController
AtcController
...
    ↓
TargetSpeedResolver
    ↓
atoTargetSpeed
```

AtoControllerを巨大化させないことが重要です。

---

# 26. createTargetSpeedを保持する理由

`createTargetSpeed` は今後もDebug上重要です。

用途:

```text
Create本来の要求速度
CATによる制限後速度
ATO Targetとの差分
制約の原因調査
バグ解析
```

将来Debug HUDでは:

```text
Create Target
ATO Target
Active Constraint
```

を同時に表示できる構造が望ましいです。

---

# 27. Debug HUDの将来候補

```text
Train ID
Current Speed
Create Target Speed
ATO Target Speed
Speed Limit
Active Constraint
Station
Distance to Station
Signal State
Braking Distance
Required Deceleration
Current Notch
Driving Mode
Controller State
```

制動モデル・TASC調整時はDebug機能を積極的に利用します。

---

# 28. 開発上の固定ルール

Work / Codexで実装する際は以下を守ります。

1. Minecraft 1.20.1固定
2. Forge固定
3. Create 6.0.8固定
4. Java 17
5. Create API / 内部仕様を推測で実装しない
6. Create 6.0.8実ソースを必要に応じて確認
7. `Train.speed` を通常制御で直接変更しない
8. `TrainController` を列車UUID単位で管理
9. 機能を独立クラスへ分離
10. Server / Client責務を分離
11. 単位を明示
12. CAT公開側ではblocks/s系
13. Create境界のみblocks/tick
14. Mixinは必要最小限
15. Inject箇所・tick順序を必ず確認
16. 実装後はGradle build
17. 既存Phaseの動作を壊さない
18. 不要な大規模リファクタを同時に行わない

---

# 29. Phase 5へ進む条件

**Phase 5のJava実装は、ユーザーが制動モデルの数学・仕様が完成したと明示するまで開始しません。**

ユーザーが:

```text
制動モデルの数学上の構築が完了した
```

と伝えた段階で実装へ進みます。

その際、可能であればTyped Unit Value Objectの導入も同時に行います。

---

# 30. Work / Codex移行後に最初に確認するファイル

```text
build.gradle
gradle.properties
src/main/resources/META-INF/mods.toml
src/main/resources/*mixins*.json

src/main/java/dev/edudio/createadvancedtrains/
├─ CreateAdvancedTrains.java
├─ config/AdvancedTrainsConfig.java
├─ train/TrainController.java
├─ train/TrainControllerManager.java
├─ train/TrainState.java
├─ control/AtoController.java
├─ speed/SpeedLimitController.java
├─ speed/SpeedLimitSource.java
├─ mixin/TrainMixin.java
├─ command/ChangeSpeedLimitCommands.java
└─ network / debug関連
```

**会話中の過去コードより、実際のリポジトリ内コードを最終的な正とします。**

---

# 31. 推奨ドキュメント構成

Codexとの運用では、リポジトリ内に以下を置く方針が推奨されています。fileciteturn7file3

```text
AGENTS.md

docs/
├─ PROJECT.md
├─ ARCHITECTURE.md
├─ DEVELOPMENT_STATUS.md
├─ DEVELOPMENT_RULES.md
│
├─ design/
│  ├─ ATO.md
│  ├─ BRAKING_MODEL.md
│  ├─ TASC.md
│  ├─ NOTCH.md
│  ├─ SIGNAL.md
│  └─ ATC.md
│
└─ technical/
   ├─ CREATE_6_0_8.md
   ├─ UNITS.md
   └─ SERVER_CLIENT.md
```

特に `AGENTS.md` はCodex向けの固定開発指示書として使います。

---

# 32. 現在の到達点

```text
Minecraft 1.20.1
+ Forge 47.4.10
+ Create 6.0.8
        ↓
CAT Mod起動
        ↓
Create Train取得
        ↓
TrainController生成
        ↓
現在速度取得
        ↓
Create targetSpeed取得
        ↓
SpeedLimitController
        ↓
atoTargetSpeed計算
        ↓
Train.approachTargetSpeed()へMixin介入
        ↓
train.targetSpeedへ反映
        ↓
Create標準速度追従
        ↓
train.speed
```

この基盤は実際に動作確認済みです。

---

# 33. 本資料に含めていない内容

以下は先に作成済みの制動関連資料を参照してください。

```text
BrakingCurve数学
常用制動 / 非常制動
B1〜B7 / EB
ブレーキ応答遅れ
安全余裕
速度依存減速度
減速度テーブル
TASC詳細
クリープ
停止位置
EB異常状態
CAT Controller Block
CAT対応列車判定
DrivingMode詳細
AUTOMATIC / MANUAL状態遷移
Create 6.0.8制動物理詳細
駅停車理由判定
targetSpeed=0の解釈
```

これらについては本資料で再定義しません。

---

# 34. Work移行後の推奨初動

```text
1. 実リポジトリを確認
2. 現在のソースを読む
3. この引継ぎ資料と実コードを比較
4. docs/DEVELOPMENT_STATUS.mdを作成
5. AGENTS.mdを作成
6. Phase 0〜4の既存動作を固定
7. 制動モデル資料をdocs/design/BRAKING_MODEL.mdへ整理
8. ユーザーからPhase 5実装開始許可を待つ
9. 許可後、Typed Unit導入＋Phase 5実装へ進む
```

最重要なのは、**会話中の古いスニペットへ戻らず、現在のリポジトリ状態を基準に継続すること**です。
