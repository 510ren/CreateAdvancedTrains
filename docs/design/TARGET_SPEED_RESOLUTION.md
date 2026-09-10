# Target Speed Resolution

## Phase 6の目的

`TargetSpeedResolver`は、複数の独立した速度制約から、Createへ渡す最終CAT目標速度を決める
Server側の統合担当である。BrakingCurve、TASC、信号、ATC/ATS、EB、NotchControllerの責務を
このクラスへ混在させない。

## 入力と出力

| 区分 | 内容 |
| --- | --- |
| 入力 | Create由来の目標速度、現在速度、進行方向、各制約の速度上限・状態、CAT運転状態 |
| 出力 | 最終CAT目標速度、採用した制約、全候補制約と診断情報 |

公開・診断上の単位はblocks/sとし、Create境界でだけblocks/tickへ変換する。正方向・逆方向は
速度の絶対値による上限計算と方向符号の復元を分離する。

## 統合規則

- 各Controllerは「速度上限または状態」を返す。最終`targetSpeed`を書き込まない。
- 通常走行では、有効な正方向上限の最小値を採用する。
- 停止・到着待ち・EB・CAT安全ロックアウトは、単なる数値上限ではなく優先状態として扱う。
- 制約なしは上限値を勝手に0とせず、候補なしとして表す。
- BrakingCurveの`u_ceiling`はResolverまたはその上位設定から明示的に渡す。BrakingCurve自身は
  路線や設定から上限を選ばない。

## 未決定事項

- CAT `maxSpeed`設定の有無、配置、既定値、Create上限との関係。
- 信号・ATC/ATS・TASC・EBを含む最終的な優先順位。
- 制約が解除された際の目標速度の戻し方と、NotchSelectorとの分担。
- P1〜P5の力行Profileと、N・B1〜B7を含む自動ノッチ選択規則。

## Phase 6承認済み実装仕様

### 適用範囲と保護境界

- 本仕様はPhase 6のServer側`TargetSpeedResolver`、`AtoController`、製品版
  `NotchSelector`、`NotchController`だけを対象とする。
- `Train.speed`を直接変更せず、既存の一回の
  `Train.approachTargetSpeed(float accelerationMod)`呼出を使う。CATは追加の
  `approachTargetSpeed()`を呼ばない。
- Phase 5Aの固定ノッチ、停止目標保持、計測ログは測定治具であり、製品版の
  Resolverまたは自動ノッチから呼び出し・変更してはならない。
- 独自マスコン、CAT対象判定の組立時登録、CAT専用ワールド保存データ、HUD、
  Client入力はPhase 9の範囲である。Phase 6はそれらの実装方式を決めず、
  `CAT_ONLY`列車であることを将来の外部適格性境界として扱う。
- CAT適格性判定はPhase 9まで実装しない。Phase 6は既存の`CONTROL_ENABLED`および
  `ATO_ENABLED`による有効化範囲を維持し、独自マスコン未搭載を理由とする新たな
  Mixin接続解除・全列車への回避的な有効化・判定用の暫定Blockを追加しない。

### Resolverの候補と単位

すべての公開値と診断値はblocks/sを使う。Create境界でのみblocks/tickへ変換する。
制御呼出ごとに、次の独立した候補を保持し、最終値だけで根拠を失ってはならない。

| 候補 | 値と意味 |
| --- | --- |
| `u_ceiling` | `abs(train.maxSpeed()) * 20`。燃料状態に応じたCreateの直線上基本最高速度。毎呼出で読み直す。 |
| Create由来候補 | Navigationまたは既存手動経路がその呼出に設定したnative target。カーブ、throttle、Phase 6でまだCATへ移管していない停止制約を含み得る。 |
| BrakingCurve候補 | `AHEAD`の有効な停止対象に対して計算された最大許容速度。結果が利用不能なら候補なし。 |

正方向・逆方向を混在した数値のまま`min`してはならない。進行方向へ正規化した速度の絶対値で候補上限を比較し、採用後に方向符号を復元する。

通常状態では有効候補の最小値を`V`として採用する。BrakingCurve候補がないときは、
その候補を0で代用してはならない。`V`が得られない場合はCATが新しい通常走行指令を生成せず、
既存のCreate挙動を維持する。

### Createのnative targetが0の場合

Createの`targetSpeed = 0`を、CATの常用制動ノッチ要求そのものとして解釈しない。
理由ごとに次のように扱う。

| 原因 | Phase 6の扱い | 後続Phase |
| --- | --- | --- |
| Navigation目的地への停車 | Phase 6ではCreate由来の駅停止制御を維持する。BrakingCurveは診断・安全監視用に計算してよいが、`GlobalStation`への最終停車を完遂するResolver候補には含めない。 | Phase 7のTASCが通常制動から引き継ぎ、最終停止を担当する。 |
| Create信号による停止 | Phase 6ではCreate由来の安全候補として維持する。 | Phase 8の独自5段階信号システム導入後、CAT信号制約へ移管する。 |
| Create標準手動入力 | Phase 6では既存経路が存在する。 | Phase 9でCAT独自マスコンが有効な間、W/S等の標準入力を無効にしCAT入力へ移管する。 |

CATは、理由を問わずnative targetの0を後続tickへ保持・ラッチしてはならない。
その呼出でCATがNを選んだときだけ、最終targetを0へ置き換える。

### Phase 6における駅停車とBrakingCurveの境界

`GlobalStation`への駅停車は、Phase 7のTASC実装前にはCATのBrakingCurveで完遂しない。
Phase 5Bの共通安全余裕`M = 10 blocks`を駅停止位置までの残距離から直接引くと、
駅ブロックの手前で`u_max = 0`となり、Create Navigationが到着処理へ進めなくなるためである。

- Phase 6で駅を目的地とする場合、BrakingCurveはログ・診断・将来のTASC安全監視のために
  計算してよいが、`TargetSpeedResolver`の通常速度候補へ含めない。
- したがって、駅停止に対するCreateのnative target（0を含む）はPhase 6では保持する。
- 駅以外の将来の安全保護対象でBrakingCurveを最終速度制約として採用する規則は、この変更で
  決めない。
- Phase 7では、通常制動からTASCへ引き渡す位置・引渡し速度・TASC区間でのBrakingCurveの
  監視専用利用を仕様化する。TASCだけが駅位置±0.5 blocksの定位置停止を判定する。

### 安全速度と予測

Resolverが返した最終速度上限を`V`とし、通常走行の目標速度を次で定義する。

```text
V_safe = V * 0.95
```

ノッチ選択は現在速度だけで判断しない。現在の`effectiveAcceleration`から候補ノッチの
目標加速度へ既定の10 tickで線形遷移するときの、0.5秒後の進行方向速度を`vPred(notch)`として
予測する。実装は既存の10 tick応答則と同じ時間基準・単位を使う。

初期の通常帯域とヒステリシスは次とする。これらはPhase 10の実走行データで調整対象である。

```text
H = 0.5 blocks/s
L = V_safe - 1 - H
U = V_safe + 1 + H
```

### P / N / Bの選択規則

- `v < L`ではP候補を評価する。現在がP以外なら、`vPred(Pn)`が`V_safe`を超えない
  P候補のうち最も強いPを選ぶ。現在が`Pn`なら、`vPred(Pn) < L`のときだけ
  一段強い`P(n+1)`へ移る。`vPred(Pn) > U`のときだけ一段弱い`P(n-1)`へ移る。
  それ以外は現在のPを維持する。P1/P5を越える段は選ばない。
- `L <= v <= U`ではNを基本とする。ただし、上記のPまたはB保持条件が満たされる間は、
  そのノッチを維持して往復切替を防ぐ。
- `v > U`、またはBrakingCurveの最大許容速度を上回る場合はB候補を評価する。
  Bの予測上限は、`V_safe`と有効なBrakingCurve最大許容速度の最小値とする。
- 通常のB候補はB4、次にB5とする。現在のBnの10 tick応答込み予測がB予測上限へ戻せないときだけ、
  一段強いBへ移る。B4/B5で不足するときだけB6/B7を順に許可する。
  Bを弱めるのも一段ずつとし、一段弱い候補が安全上限へ収まるときだけ行う。
- B7でもB予測上限へ戻せない場合、NotchSelectorはB7と常用制動不足状態を返す。EBを発動せず、
  その判断はPhase 8の責務とする。

「十分低い」の正確な規則は、現在の`Bn`ではなく一段弱い候補の予測で判断する。

```text
強める: vPred(Bn) > C + H             → B(n+1)
弱める: vPred(B(n-1)) <= C + H        → B(n-1)
B4からN: vPred(N) <= C + H            → N
それ以外                              → Bnを維持
```

ここで`C = min(V_safe, 有効なBrakingCurve最大許容速度)`であり、BrakingCurve候補がなければ
`C = V_safe`とする。Phase 6の自動選択はB4〜B7だけを対象とし、通常はB4/B5、
不足時だけB6/B7を選ぶ。B1〜B3は将来のTASC等のためProfileとして保持するが、自動選択しない。

### 応答遷移中のCreate境界

`effectiveAcceleration`は符号を持つ一方、Createの`accelerationMod`は速度目標から離れる向きにも
動作し得るため、通常CAT制御では非負だけを渡す。加速・減速の向きは、同じ呼出に渡す最終targetの
現在速度に対する向きで決める。

- `effectiveAcceleration > 0`の間は、Resolverが方向を復元した`V`をP方向の最終targetとして、
  `effectiveAcceleration / baseAcceleration`を使う。
- `effectiveAcceleration < 0`の間は、停止方向の最終target `0`と
  `abs(effectiveAcceleration) / baseAcceleration`を使う。
- `effectiveAcceleration = 0`では、加速度修飾を0とし、最終targetをその時点の速度と等しくして
  その呼出で速度を変えない。

したがって、PからN、NからB、BからPへ要求が変わっても、応答遷移がゼロを跨ぐまで
Createへ渡すtargetの向きを早期に反転してはならない。Nの定常到達後は負の
`effectiveAcceleration = -baseAcceleration / 6`となるため、`finalTargetSpeed = 0`および
`accelerationMod = 1/6`をその一回の呼出に適用する。targetを後続tickへラッチしてはならない。

### 同一server tickの複数呼出

10 tick応答の時間基準はserver tickとする。列車UUIDごとに、そのserver tickで最初の
`approachTargetSpeed()`呼出だけがResolver、NotchSelector、NotchResponseModelを評価・進行させる。
このときの`effectiveAcceleration`をキャッシュする。

同じserver tickの後続呼出は、ノッチ選択と応答進捗を再評価せず、キャッシュ済みの
`effectiveAcceleration`を再利用する。ただし、後続呼出のnative targetから得られる安全候補は
Resolverへ取り込んでよい。後続呼出でより低い安全上限が得られた場合、ノッチ応答を二重に進めず、
その呼出用final targetだけを安全側へ下げる。

### 到着待ち・エラー時の出力

到着待ち、計算エラー、Navigationオーバーランは通常P/N/B選択より優先し、共通して次を適用する。

| 項目 | 値 |
| --- | --- |
| `finalTargetSpeed` | 当該呼出のnative targetを変更せず維持する。 |
| `accelerationMod` | Createが当該呼出へ渡した元の値を変更せず維持する。 |
| ノッチ応答 | `SUSPENDED`。進捗を進めず、保存済み`effectiveAcceleration`をCreateへ再利用しない。 |

通常制御へ戻るときは、古いP/B/N状態を再開せず、実効加減速度0から新しい要求ノッチへ
10 tick遷移を開始する。計算エラーとNavigationオーバーランの解除は、既決どおりレンチ復旧の
成功後だけ許可する。

### native target 0の分類

native targetが0の場合、次の優先順で分類する。分類不能な0を推測で無視してはならない。

| 優先 | 条件 | Resolver候補への扱い |
| --- | --- | --- |
| 1 | `navigation.waitingForSignal != null` | 信号停止。Create由来の0を安全候補として含める。 |
| 2 | 信号待ちなし、`navigation.destination != null`、正規化結果が`AHEAD`または到着待ち | 目的地停止。Phase 6ではCreate由来の0を安全候補として含める。BrakingCurveは最終駅停止候補にしない。 |
| 3 | 信号待ちなし、目的地なし、`manualTick == true` | Create標準手動入力。Phase 6ではCreate由来の0を安全候補として含める。 |
| 4 | 上記以外 | 判定不能な0。Create由来の0を安全候補として含め、診断へ分類不能理由を残す。 |

### ATOの統括責務

`AtoController`はCAT通常制御の司令塔である。制約統合、BrakingCurve、ノッチ選択、
10 tick応答の各計算式を自ら再実装せず、専用クラスへ委譲し、列車単位の状態優先に従って
結果を次の担当へ渡す。

```text
制約候補 + BrakingCurve結果
        ↓
TargetSpeedResolver
        ↓
AtoController（状態優先と統括）
        ↓
NotchSelector → NotchResponseModel / NotchController
        ↓
既存のTrain.approachTargetSpeed()一回
```

計算エラー、Navigationオーバーラン、到着待ちは通常P/N/B選択より優先する。
既存の読み取り専用共有フラグとエラーラッチを変更・自動解除してはならない。

### Sol向け受入条件

- `u_ceiling`は毎呼出で`Train.maxSpeed()`から読み、CAT独自maxSpeed設定を追加しない。
- Resolverは候補・採用根拠・最終速度を区別して保持し、速度方向を安全に扱う。
- P1〜P5、N、B1〜B7は10 tick応答と再遷移を共有する。
- P/N/B選択は`V_safe`、`H`、10 tick予測、段階的なノッチ遷移を用い、毎tickの無条件な最適ノッチ再選択をしない。
- Create境界は実効加速度の符号からtarget方向を決め、加速度修飾を非負に保つ。Nの定常到達後は
  `finalTargetSpeed = 0`と`accelerationMod = 1/6`をその一回のCreate呼出にだけ適用し、targetをラッチしない。
- 一server tick内で10 tick応答を最大一回だけ進め、後続呼出はキャッシュ済み実効加減速度を使う。
- 到着待ち、計算エラー、Navigationオーバーランではnative targetと元の`accelerationMod`を維持し、
  ノッチ応答を`SUSPENDED`とする。
- native target 0を信号、目的地、Create標準手動、判定不能に指定順序で分類し、分類不能な0を無視しない。
- `Train.speed`、Create燃料、throttle、manualTick、Navigation destinationをCATが直接変更しない。
- Phase 5A測定治具、BrakingCurveの純粋性、Client HUD/入力の境界を保護する。
- Solの完了条件はJava実装、Create 6.0.8との静的照合、Gradle build、実装報告である。Minecraft起動、走行試験、ログ採取はユーザー担当とする。
