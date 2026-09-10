# ノッチ制御 — Phase 5A試験仕様

## 1. 目的とPhase

本書はPhase 5Aで実測する常用制動ノッチB1〜B7の仕様である。日本の電車の常用制動を
基本思想とし、離散ノッチの性能を速度ごとに測定して、後続のBrakingCurveと自動運転の
根拠を作る。

この段階では、常用制動のProfileと応答モデルを実装可能な形に定義する。EBの性能・発動・
復旧は本書の範囲外であり、`docs/design/EMERGENCY_BRAKE.md`で別途確定する。TASC、
信号、停車位置制御、および最終的なユーザー操作UIも範囲外である。

## 2. 不変条件

- 通常制御では`Train.speed`を直接変更しない。
- Createの加速度値を直接書き換えない。実効加減速度は、Create標準の`targetSpeed`と
  速度追従経路へ渡す加速度修飾の目標値として扱う。
- Profile、ノッチ選択、応答、Create境界の適用を同一責務に混在させない。
- CAT公開値・試験ログはblocks/s、blocks/s²、blocks、secondsを用いる。Create境界でのみ
  blocks/tick、blocks/tick²へ変換する。
- 実際のCreate 6.0.8で加速度修飾を渡す箇所・符号・単位・tick順序を確認できるまで、
  Solは変換式やMixin適用位置を推測してはならない。

## 3. 用語と状態

| 用語 | 意味 |
| --- | --- |
| `COAST` | 力行も常用制動も要求しない中立状態。目標加減速度は0。 |
| `B1`〜`B7` | 弱い順から強い順の常用制動ノッチ。B7が常用制動の最大値。 |
| `commandedNotch` | `NotchSelector`が今回要求した離散ノッチ。 |
| `appliedNotch` | 最後に遷移が完了した離散ノッチ。遷移途中の値を架空のノッチ名にしない。 |
| `effectiveAcceleration` | このtickにCreate境界へ渡す実効加減速度。ノッチ間遷移中は中間値を取り得る。 |
| `aTarget` | 現在速度・要求ノッチ・Profileから求めた目標加減速度。 |
| `aStart` | 今回の遷移開始直前の`effectiveAcceleration`。 |

制動を負値、力行を正値とする符号規約をCATの制御内部で用いる。`COAST`は0
blocks/s²である。Create境界で用いる符号が異なる場合でも、変換は境界だけで行い、
本書の内部規約を変えてはならない。

## 4. Phase 5Aの暫定Profile

### 4.1 基準値

基準加速度 `baseAcceleration` は、Create 6.0.8の `train.acceleration()` が返す値の絶対値を
CAT単位のblocks/s²へ変換したものとする。各tickで読み取り専用に取得する。無効値、非有限値、
または0以下の場合はノッチ制御を適用せず、理由を記録してCreateの通常挙動を維持する。

### 4.2 暫定倍率

Phase 5Aで測定するため、B1〜B7に次の等間隔の暫定倍率を用いる。

| ノッチ | B1 | B2 | B3 | B4 | B5 | B6 | B7 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 倍率 | 1.0 | 1.5 | 2.0 | 2.5 | 3.0 | 3.5 | 4.0 |

現在速度を`v`、ノッチ`Bn`の倍率を`m(n)`とすると、Phase 5Aの常用制動目標は次である。

```text
aTarget(Bn, v) = -baseAcceleration(v) × m(n)
aTarget(COAST, v) = 0
```

これは測定に用いる暫定値であり、最終性能値ではない。試験後、各ノッチの実測表から
速度依存のB7基準曲線とB1〜B7の倍率・個別補正を確定する。B7を基準とする最終Hybrid
Profileへの置換は、`NotchProfile`のデータだけを差し替えて行い、選択・応答・Create境界の
責務を変更しない。

### 4.4 最終Profileの基準位置（Phase 5B決定）

最終B1〜B7 Profileでは、**B4を基準加速度に対応する中央ノッチ**とする。Phase 5Aの
`B1 = 1.0`から`B7 = 4.0`の暫定倍率は、この最終基準位置を表すものではなく、試験用の入力である。

ここで`d_Bn(u)`は正値で表す制動の大きさ、`δ(u)`は無次元の隣接ノッチ間倍率差とする。最終式は次のとおり。

```text
d_Bn(u) = baseAcceleration × {1 + (n - 4) × δ(u)}
```

よって`n=4`では補正項が0となり、B4は全速度域で確実に`baseAcceleration`となる。Phase 5Bでは
`δ(u) = δ_0`の定数関数を採用する。Java実装でも将来の速度補正を差し替えられる`delta(u)`の問い合わせを
持つが、初期値は速度によらない`δ_0`だけを返す。`0 < δ_0 < 1/3`はB1の正値とノッチ順序を守る必須条件である。

最終値は`δ_0 = 1/5`とする。B1〜B7の最終倍率は順に`0.4, 0.6, 0.8, 1.0, 1.2, 1.4, 1.6`となる。
走行データに基づく`δ(u)`への補正は、新設するPhase 10調整フェーズで行う。

### 4.3 速度依存テーブルの表現

Profile APIは、速度点と減速度値から線形補間するテーブルでなければならない。Phase 5Aの
暫定Profileは上記倍率の定数値を返すが、実装上は速度0〜160 blocks/sの両端に同じ値を持つ
テーブルとして表現する。範囲外の速度は端点値へclampする。これにより、実測後に速度点を
追加してもAPI・呼出側・ログ形式を変えずに済む。

## 5. 線形応答モデル

ノッチ要求または再要求を受けた時点を`t = 0`とする。`aStart`から、各tickの
`aTarget(commandedNotch, currentSpeed)`へ10 tick（0.5秒）かけて線形遷移する。

```text
effectiveAcceleration(t)
  = aStart + (aTarget(commandedNotch, currentSpeed) - aStart)
    × clamp(t / 10, 0, 1)
```

- 不感時間は置かない。
- 速度変化により`aTarget`を毎tick再評価するが、遷移開始tick・`aStart`・進捗は保持する。
- `t >= 10`では、そのtickの`aTarget`を`effectiveAcceleration`とする。
- 遷移中に新しい`commandedNotch`が来たら、切替直前の`effectiveAcceleration`を新しい
  `aStart`にして、進捗を0へ戻し、新しい`aTarget`へ10 tickで再遷移する。
- 遷移が完了した時にだけ、`appliedNotch = commandedNotch`とする。

## 6. 選択モード

### `FIXED_FOR_TEST`

Phase 5Aで必須とする。試験開始時に指定した`B1`〜`B7`を`commandedNotch`として維持する。
停止判定、残距離、速度差、信号、TASC、ATOの判断でノッチを変更してはならない。これにより、
初速・線路条件・ノッチごとの減速度、応答、停止距離を分離して測定する。

`FIXED_FOR_TEST`は列車を発車・停止させる機能ではない。Createが既存のNavigationまたは
プレイヤー操作により、現在速度より低い同方向の`targetSpeed`を要求している場合だけ、
その減速過程に指定ノッチの性能を適用する。Phase 5A機構は`Train.targetSpeed`、
`createTargetSpeed`、`atoTargetSpeed`を生成・上書き・0へ固定してはならない。減速要求が
ないtickでは、試験ノッチを適用せず、その理由を制御試験記録へ残す。

#### 任意の試験専用停止目標保持

Create標準Navigationは基準`train.acceleration()`だけで制動距離を再計算する。B7のような強い
試験制動を外部から加えると、Navigationが再加速targetを出し、固定ノッチの連続性能を測れない。
そのため、Phase 5Aには次の**任意かつ試験専用**の停止目標保持を設ける。

- `FIXED_FOR_TEST`かつ設定有効時だけ使用する。`AUTO`および将来の製品版ATO/TASCには適用しない。
- Create Navigationが、その列車に有効なdestinationを持つ状態で、初めて停止用のnative
  `targetSpeed = 0`を出した時に開始する。
- 開始後、列車が停止するまで、Createの各速度追従呼出で用いる最終`targetSpeed`を0にする。
  これにより、固定Bノッチの10 tick応答、定常減速度、停止距離を連続した減速要求で測定する。
- `Train.speed`を直接変更してはならない。保持対象は試験呼出における`targetSpeed`だけである。
- 停止完了、試験設定の無効化、列車/ワールドの消滅、試験セッション終了では、列車UUIDごとの
  保持状態を必ず破棄する。無効時・解除後に通常Create走行へ持続的なtargetを書き残してはならない。
- この保持は試験治具であり、製品版の「安全に停止できる動的targetSpeed」やノッチ切替を代替しない。
  本実装ではBrakingCurveとTargetSpeedResolverが動的targetSpeedを決定する。

### `AUTO`

最終的な自動運転で必要なモードであるが、Phase 5Aの性能測定の対象外とする。
`AUTO`の`NotchSelector`は、ATOから渡された要求減速度の大きさに対して、Profile上で
それ以上の制動能力を持つ最小のBノッチを選ぶ。要求減速度が0以下なら`COAST`、B7で足りない
場合は`B7`を返すとともに「常用制動不足」を上位へ返す。EBの選択・発動は行わない。

ATOが要求減速度を作る停止距離数学、ノッチ切替のヒステリシス、TASC・信号との統合は、
BrakingCurveと上位統合仕様を確定してから追加する。Phase 5Aでは、`AUTO`を実走行試験の
制御根拠にしてはならない。

## 7. Phase 5Aの有効化設定

Phase 5Aの制御試験は、通常のノッチ機能設定とは別の明示的なServer Configでのみ有効にする。
既定値は必ず無効とし、無効時には制御・テストログ・状態生成を一切行わない。

```toml
[debug.phase5a_notch_test]
enabled = false
selection_mode = "FIXED_FOR_TEST"
fixed_notch = "B1"
hold_stop_target_until_stop = false
```

| 設定 | 有効値 | 要件 |
| --- | --- | --- |
| `enabled` | `true` / `false` | `false`が既定。`false`では既存走行を変更せず、テストログも作らない。 |
| `selection_mode` | `FIXED_FOR_TEST` / `AUTO` | Phase 5Aの実測は`FIXED_FOR_TEST`だけを使用する。`AUTO`はSelector単体の将来検証用であり、実走行制御の根拠にしない。 |
| `fixed_notch` | `B1`〜`B7` | `FIXED_FOR_TEST`で必須。無効値なら制御を開始せず、理由をサーバーログへ記録する。 |
| `hold_stop_target_until_stop` | `true` / `false` | 既定`false`。`true`は`FIXED_FOR_TEST`限定の試験治具であり、destinationを持つNavigationの最初のnative停止要求後、停止まで最終targetを0に保持する。 |

この設定は試験専用であり、将来のプレイヤー向けノッチ操作、`自動運転時のみ`・`手動運転時のみ`
などの機能有効範囲を置き換えない。製品機能の有効範囲は、booleanではなく列挙型の単一設定で
管理するという設計方針に従い、別仕様で確定する。

## 8. 期待する責務・入出力

| 要素 | 入力 | 出力 | 非責務 |
| --- | --- | --- | --- |
| `NotchProfile` | ノッチ、現在速度、基準加速度 | `aTarget`（blocks/s²） | 停止距離、ノッチ選択、状態変更 |
| `NotchResponseModel` | `commandedNotch`、`aTarget`、前tickの状態 | `effectiveAcceleration`、遷移進捗、完了した`appliedNotch` | Createオブジェクトの変更、停止判定 |
| `NotchSelector` | 選択モード、固定ノッチまたはATO要求減速度、Profile | `commandedNotch`、常用制動不足フラグ | EB発動、制動距離の数学 |
| Create境界適用部 | `effectiveAcceleration`、Createの実際の呼出コンテキスト | Createに渡す加速度修飾 | `Train.speed`の直接変更、Profile再実装 |

クラス名は責務を表す候補であり、Solは現在のpackage構成へ合わせて調整してよい。ただし、
上記の入力・出力・非責務を変えてはならない。

## 9. Phase 5Aのログと測定

Phase 5Aの各サンプルは、既存の受動型TrainDataDebuggerとは別の制御試験記録として、
少なくとも次を同一tickへ対応付ける。

```text
serverTick
testSessionId
selectionMode
fixedTestNotch             # FIXED_FOR_TEST時
commandedNotch
appliedNotch
transitionElapsedTicks
currentSpeedBlocksPerSecond
createTargetSpeedBlocksPerSecond
baseAccelerationBlocksPerSecondSquared
profileTargetAccelerationBlocksPerSecondSquared
effectiveAccelerationBlocksPerSecondSquared
measuredAccelerationBlocksPerSecondSquared
applicationState               # applied / no_braking_demand / disabled / invalid_input 等
navigation.distanceToDestinationBlocks
```

出力先は `logs/create_advanced_trains/notch_test/` とし、列車UUIDと試験セッション開始UTCを
含む、既存ファイルを上書きしないJSON Lines `.log` ファイルとする。`enabled = false`のときは
ディレクトリ・ファイル・Writerを作成しない。既存のTrainDataDebuggerのログ形式・依存関係を
変更してはならない。

### 9.1 性能測定前の呼出単位観測

Phase 5AのProfile性能を評価する前に、同一server tick内の
`Train.approachTargetSpeed()`呼出を区別して記録できなければならない。sampleごとに最後の
targetSpeedだけを残す方式では、Profile値と実測加速度の関係を判定できないためである。

次の計測信頼性修正では、既存のB1〜B7倍率、`NotchResponseModel`の応答則、
`Train.targetSpeed`への非介入、加速度修飾の返却条件を変更してはならない。目的は観測の追加と、
Create 6.0.8実ソースに基づく呼出順の確認だけである。

新しいschemaVersionの各sampleには、少なくとも次を追加する。

```text
approachCallCount
brakingDemandCallCount
notchModifierAppliedCallCount
approachCalls[]
  sequenceInServerTick
  preCatTargetSpeedBlocksPerSecond
  finalTargetSpeedBlocksPerSecond
  brakingDemand
  applicationReason             # 減速要求なしの場合
  originalAccelerationMod
  returnedAccelerationMod
  notchModifierApplied
responseState
  storedEffectiveAccelerationBlocksPerSecondSquared
  activeEffectiveAccelerationBlocksPerSecondSquared  # このsampleで未適用ならnull
  transitionElapsedTicks
  transitionTimebase            # 現行実装が何を1 tickとして数えたか
```

`measuredAccelerationBlocksPerSecondSquared`はserver tick全体の速度差であり、一回の
`approachTargetSpeed()`呼出が発生させた減速度ではないことを、schema内の説明または
session_startで明記する。複数呼出があるtickでProfile目標値とserver tick実測値を直接比較しては
ならない。

停止目標保持を実装したschemaVersion 4以降は、試験条件と実際にCreateへ渡した値を区別できるよう、
session_startと各`approachCalls[]`へ少なくとも次を追加する。

```text
session_start
  holdStopTargetUntilStop

approachCalls[]
  nativeTargetSpeedBlocksPerSecond       # 試験保持前のCreate入力
  finalTargetSpeedBlocksPerSecond        # Createの速度追従が実際に用いる値
  stopTargetHoldState                    # INACTIVE / LATCHED / RELEASED
  stopTargetHoldOverrideApplied
  stopTargetHoldTriggerReason            # 初回ラッチ時だけ。例: navigation_native_stop_target
```

停止目標保持の有無を混在させたログから、Bノッチの性能値を同じ表へ集計してはならない。

停止目標保持を有効にする固定ノッチ測定では、駅Aから駅Bへ自動運転を開始しても、B駅到着までを
一つの性能サンプルとしては扱わない。有効な一測定区間は、最初の`stopTargetHoldState = LATCHED`から
対応する最初の`stopTargetHoldState = RELEASED`までである。解除後の再加速・再ラッチは別サイクルであり、
停止距離・定常減速度・応答の同一サンプルへ混在させない。駅停車精度はPhase 5Aの対象外である。

SolはCreate 6.0.8実ソースを確認し、応答時間10 tickの時間基準をserver tick、呼出回数、
または連続適用tickのどれとして実装すべきかを、根拠と選択肢付きで報告する。この計測信頼性
修正では、その時間基準や制御挙動を独断で変更してはならない。

### 9.2 schemaVersion 3で確認された試験条件の分離

V3計測により、同一server tickでの複数呼出・二重修飾は当該走行の原因ではないと確認できた。
一方、B7が中断した160→100 blocks/sの加速tickは、ATOを有効にしたまま、速度制限を100 blocks/sへ
手動変更していた試験条件による。これはtargetSpeedの所有権・Mixin介入点の不具合を示すものではない。

Phase 5AのProfile性能試験では、ATO、速度制限、TASC、EBおよび製品版の自動ノッチ選択を無効にし、
`FIXED_FOR_TEST`だけを有効にする。Create標準のNavigationまたはプレイヤー操作が実際に現在速度より
低い同方向targetSpeedを出した場合だけ、固定ノッチを適用する。この分離済み条件をsession_startに
記録し、連続した減速要求の区間だけを応答・減速度・停止距離の性能値に使う。

測定は、B1〜B7について、少なくとも複数の初速・加速度設定・進行方向で行う。160 blocks/sを
目標とするProfileを最終化する前に、実際に高速度域へ到達する線路または試験条件を用意する。
各条件の反復回数、初速、制動開始位置、速度点ごとの実測減速度、停止距離、応答完了時刻、
停止誤差を表へ集計する。

## 10. Solへ依頼する前の確認と完了条件

Java実装を依頼する前に、Phase 5 Java実装開始の明示承認を別途得る。Solは実装前に、
Create 6.0.8実ソースで次を確認する。

1. `train.acceleration()`の型・符号・単位と、安全な読み取り時点。
2. `effectiveAcceleration`をCreate標準の速度追従へ渡す正確な経路、修飾値の意味、単位、
   既存Mixinとの順序。
3. `targetSpeed`を通すことで、CreateのNavigation・手動運転と競合しないこと。
4. Phase 5Aの試験設定を有効にしていないとき、既存走行へ一切の影響がないこと。

Solの完了条件は、次のソースレベル確認とGradle buildに限る。Minecraftの起動・操作、
固定ノッチ走行、制御試験ログの実出力、実測加減速度の確認はユーザーが手動で行うため、
Solの完了条件には含めない。

- ソース上で、`FIXED_FOR_TEST`の指定ノッチを維持する状態遷移が実装されている。
- 停止目標保持が無効のとき、Phase 5A機構が`Train.targetSpeed`、`createTargetSpeed`、
  `atoTargetSpeed`を変更せず、既存の減速要求に対してだけ適用される構造になっている。
- 停止目標保持が有効なときは、`FIXED_FOR_TEST`・Navigation destinationあり・最初のnative停止要求後に
  限り、Createの速度追従へ渡す最終`targetSpeed`を0に保持する。`Train.speed`の直接変更、`AUTO`への
  適用、試験終了後のtarget保持をしてはならない。
- ProfileがB1=1.0からB7=4.0まで0.5刻みの暫定倍率を持ち、応答・再遷移・毎tick再評価の
  規則を実装している。
- 無効設定時に制御・Writer・出力ディレクトリを作らないガードがソース上にある。
- `Train.speed`を直接変更せず、既存の受動型TrainDataDebuggerを制御依存へ変更していない。
- Gradle buildが成功する。

ユーザー手動試験では、B1〜B7の固定走行、10 tick遷移、無効設定、ログ出力、実測加減速度を
確認し、その結果を`docs/research/EXPERIMENTS.md`へ記録する。

## 11. Phase 6の製品版力行ノッチ

Phase 5では、NとB1〜B7の常用制動に必要な基盤だけを扱う。Phase 6の製品版NotchControllerでは、
力行ノッチとしてP1〜P5を追加する。したがって製品版の離散ノッチ集合は、少なくとも
`P1, P2, P3, P4, P5, N, B1, B2, B3, B4, B5, B6, B7`となる。

Pノッチも、N・Bノッチと同じ10 tick線形応答と再遷移規則の対象とする。ただし、P1〜P5の加速度倍率、
速度依存Profile、Pノッチ間の間隔、最高速度付近での力行抑制、PからN/Bへの自動切替条件は未確定である。
これらはB4基準のBノッチProfileから推測せず、Phase 6の力行仕様として別途決定する。

## 12. 変更可能な暫定事項

本書で変更を予定しているのは、Phase 5Aの実測後に差し替えるProfileデータである。
具体的にはB1〜B7倍率、速度点、B7基準曲線、個別補正、低速域特性である。応答時間10 tick、
線形遷移、再遷移規則、毎tickProfile再評価、責務分離、通常制御で`Train.speed`を直接
変更しない不変条件は、実測上の問題が確認されてユーザーが変更を承認するまで維持する。
