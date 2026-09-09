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
```

| 設定 | 有効値 | 要件 |
| --- | --- | --- |
| `enabled` | `true` / `false` | `false`が既定。`false`では既存走行を変更せず、テストログも作らない。 |
| `selection_mode` | `FIXED_FOR_TEST` / `AUTO` | Phase 5Aの実測は`FIXED_FOR_TEST`だけを使用する。`AUTO`はSelector単体の将来検証用であり、実走行制御の根拠にしない。 |
| `fixed_notch` | `B1`〜`B7` | `FIXED_FOR_TEST`で必須。無効値なら制御を開始せず、理由をサーバーログへ記録する。 |

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
baseAccelerationBlocksPerSecondSquared
profileTargetAccelerationBlocksPerSecondSquared
effectiveAccelerationBlocksPerSecondSquared
measuredAccelerationBlocksPerSecondSquared
navigation.distanceToDestinationBlocks
```

出力先は `logs/create_advanced_trains/notch_test/` とし、列車UUIDと試験セッション開始UTCを
含む、既存ファイルを上書きしないJSON Lines `.log` ファイルとする。`enabled = false`のときは
ディレクトリ・ファイル・Writerを作成しない。既存のTrainDataDebuggerのログ形式・依存関係を
変更してはならない。

測定は、B1〜B7について、少なくとも複数の初速・加速度設定・進行方向で行う。160 blocks/sを
目標とするProfileを最終化する前に、実際に高速度域へ到達する線路または試験条件を用意する。
各条件の反復回数、初速、制動開始位置、速度点ごとの実測減速度、停止距離、応答完了時刻、
停止誤差を表へ集計する。

## 10. Solへ依頼する前の確認と受入条件

Java実装を依頼する前に、Phase 5 Java実装開始の明示承認を別途得る。Solは実装前に、
Create 6.0.8実ソースで次を確認する。

1. `train.acceleration()`の型・符号・単位と、安全な読み取り時点。
2. `effectiveAcceleration`をCreate標準の速度追従へ渡す正確な経路、修飾値の意味、単位、
   既存Mixinとの順序。
3. `targetSpeed`を通すことで、CreateのNavigation・手動運転と競合しないこと。
4. Phase 5Aの試験設定を有効にしていないとき、既存走行へ一切の影響がないこと。

実装した場合の受入条件は次である。

- `FIXED_FOR_TEST`でB1〜B7のいずれかを指定すると、要求ノッチは試験中に変わらない。
- 暫定倍率がB1=1.0からB7=4.0まで0.5刻みでProfileへ反映される。
- ノッチ要求・再要求時の実効加減速度は10 tickで連続的に遷移し、遷移中に不連続がない。
- 速度変化だけでは遷移進捗が再開始されず、新ノッチ要求時だけ再遷移する。
- 各必須フィールドを含む列車別JSON Lines制御試験記録を出力し、無効時は出力・制御・
  ディレクトリ作成を行わない。
- `Train.speed`を直接変更せず、既存の受動型TrainDataDebuggerを制御依存へ変更しない。
- Gradle buildが成功し、最低1本のBノッチ試験でログと実測値を提示する。

## 11. 変更可能な暫定事項

本書で変更を予定しているのは、Phase 5Aの実測後に差し替えるProfileデータである。
具体的にはB1〜B7倍率、速度点、B7基準曲線、個別補正、低速域特性である。応答時間10 tick、
線形遷移、再遷移規則、毎tickProfile再評価、責務分離、通常制御で`Train.speed`を直接
変更しない不変条件は、実測上の問題が確認されてユーザーが変更を承認するまで維持する。
