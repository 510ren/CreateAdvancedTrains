# CAT連結・分離：調査依頼と確認状況

作成日：2026-10-30 ／ 更新日：2026-11-02

現在の再開地点は[00-handoff](../../整理3/handoff/00-handoff.md)。以下の10月31日・11月1日の「次は」「説明待ち」は各保存時点の経緯。調査の対象版・実確認日は変更せず、調査全体は未完了のままとする。

状態：調査依頼・進行管理資料。公開一次資料の部分確認と、ローカルソースの経路・衝突・位置・保存・上限・未読込管理の静的確認を実施済み。実車比較・技術調査全体は未完了、実装・ゲーム検証は未実施。調査結果は製品仕様の採用承認ではない。

## 1. 目的と読む資料

通常の電車同士の増解結について、CATの合意事項と実車の手順・提供済みCreateの仕組みを比較し、実現条件、矛盾、意図的な簡略化、最終判断に必要な事項を示す。

- [整理3の入口](../../整理3.md)から作業規則を確認する。
- [列車構成2.4・性能5.5](../../整理3/列車構成とプロファイル.md)、[時刻表8.29](../../整理3/時刻表と保存ダイヤ.md#s-8-29)、[残課題14.3](../../整理3/残課題と再開地点.md#s-14-3)を主対象とする。
- 非常制御、ATO・TASC、安全装置、ATOS、ドア、音響の編成継承は、関係する場面に限り各分野の現行本文を読む。
- [発生可能性と判断根拠](../../整理3/発生可能性と判断根拠.md)は検討の手掛かりとし、不発生の保証とは扱わない。
- [既存の運転台方向・編成変更調査](CREATE_6_0_8_CAB_DIRECTION_AND_CONSIST_CHANGES.md)は調査事実・推論・当時の提案を区別して再利用する。旧CAT実装やSteam 'n' Railsの仕様を現行CATの決定として継承しない。

最新の合意本文を優先する。自動接近は現在地からの経路探索と通常移動を含み、低速の最終接近へ移る。実車比較は通常の増解結が中心で、故障車救援は差と追加要件の洗い出しまで。信号・進路許可の製品判断は別分野に残す。

## 2. 調査依頼A：実車の増解結とCATの比較

鉄道事業者、車両・連結器メーカー、研究機関等の公開一次資料を優先する。各事実にURL、資料名、対象車種・装置・運用、該当箇所を付ける。一般規則、特定車種の例、推論を混同しない。

1. 接近前の準備から低速接近、停止確認、機械的連結、電気・空気系の接続、連結成立確認、運転台切替、出発までの手順。
2. 分離前の保持、切離し、各編成の保持確認、運転開始までの手順。乗務員の役割と自動処理の違い。
3. 接近速度上限とその適用条件。上限と推奨速度を区別し、一例を全車共通の値にしない。公開資料で値を確認できなければ未確認とする。
4. 曲線・勾配、連結器形状・位置ずれ、異性能編成について、成立条件と制約を調べる。規定・製品値とゲーム用提案を分ける。
5. CATの相互準備、両側停止後の成立、ホームポジション、通常／要復旧EB、切替保持、独自の待機・接近指示と対照する。現実と異なることだけを理由に既決仕様を否定しない。
6. 救援は通常の増解結との差と追加で必要な能力だけ整理する。救援の全面仕様化はしない。

出力：実車の場面／根拠／CATの該当節・現行動作／一致・相違／維持できる簡略化／利用者判断が必要な点。速度・確認時間等の推奨値は、根拠と適用範囲を伴う未承認案として示す。

## 3. 調査依頼B：提供済みソースによる成立性確認

対象は `vendor/Create-mc1.20.1-6.0.8/`。他MODは必要な場合に限り提供済みvendorを参照する。外部MODのソースをWebから取得・閲覧しない。不足時は対象・版・用途を示して提供を依頼する。Java変更、ビルド、Minecraft起動・動作試験は含めない。

| 優先順 | 確認対象 | 調査で答えること |
| --- | --- | --- |
| 1 | 経路探索と最終接近 | 駅ではなく指定列車の指定端を目標にするための取得情報と拡張境界。通常移動から接近への切替、目標の鮮度、停止相手・接続端の再確認。占有区間への進入に必要な接続点を示すが信号仕様を独断で決めない |
| 2 | 衝突と正常連結 | 衝突判定の呼出条件・順序・双方への影響、低速接近との境界、正常停止後に接触済みの両列車を連結できるか。誤相手・誤端・準備解除後の衝突を通常連結に変えない条件 |
| 3 | 曲線・勾配と位置 | 端の座標・方向・距離の取得、将来の接続位置の予測、許容値の根拠。現在離れている端の不一致と、成立予定位置での不適合を区別 |
| 4 | 編成変更と保存 | 列車・車両・端の識別、登録・占有・経路・Entity・保存の更新境界、読み込み範囲外の状態。どの途中状態が発生し得るかを呼出経路に基づき判定し、複製・消失・復元の検討対象を絞る |
| 5 | 設定・一覧 | maxBogeyCountの参照箇所・設定反映時点・既存超過編成への挙動。候補一覧に使えるサーバー側情報と、未読込・未知・喪失の区別、更新負荷 |
| 6 | 異性能編成 | 力行・Bノッチ・速度・抵抗・保持を扱う標準処理とCATが独自計算する境界。未確定の数理モデルを勝手に確定せず、追加の物理調査と製品判断を切り分ける |

出力：ファイル・識別子・行番号／確認事実／CAT要件への影響／成立性の評価／未確認・必要な実機試験。ソースで確認できたことと実機での保証を区別する。「不可能」は根拠と前提、「起こらない」は抑止条件と見直し条件を示す。

## 4. 予備確認（2026-10-30・限定的な静的確認）

以下は今回実際に読んだ範囲であり、全呼出元・他MOD介入・実機動作を確認した結果ではない。

- [Navigation.java](../../../../vendor/Create-mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/entity/Navigation.java) の `findPathTo(ArrayList<GlobalStation>, double)` は駅群を目的地として経路を探索する。これを確認しただけでは任意の列車端を目的地にできるとは言えない。下位探索の再利用範囲は未調査。
- [Train.java](../../../../vendor/Create-mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/entity/Train.java) の `collideWithOtherTrains` は衝突相手が得られると双方の `crash()` を呼ぶ。速度条件はこの箇所では爆発演出の分岐にあり、`crash()` 呼出しを低速で除外する条件ではない。`crash()` は `derailed = true` とする。衝突を検出する全条件・接近中の実際の判定位置・他MOD介入は継続調査。
- `vendor/` にCreate 6.0.8、Steam 'n' Rails、Create Train Navigatorのソースディレクトリがあることを確認した。他アドオンの正確なコミット・対象版・関連処理の内容は今回確認していない。連結互換を採用し直す根拠にはしない。

[再調査:CAT] 合意内容が変更されたら対応する比較を見直す。
[再調査:依存版] 提供版・導入MODが変わったら処理の根拠を再確認する。
[再調査:実機] 正常接近、誤端衝突、停止からの再準備、曲線・勾配、読み込み境界、保存復元は未検証。

## 4A. 追加のソース確認（2026-10-30〜31の会話分、2026-10-31保存）

対象：提供済みCreate mc1.20.1-6.0.8。以下の行番号は今回確認時点。全呼出元・他MOD介入・Minecraft本体の実行順・実機挙動まで検証したものではない。

ソース参照：

- [Navigation.java](../../../../vendor/Create-mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/entity/Navigation.java)
- [Train.java](../../../../vendor/Create-mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/entity/Train.java)
- [Carriage.java](../../../../vendor/Create-mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/entity/Carriage.java)
- [CarriageBogey.java](../../../../vendor/Create-mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/entity/CarriageBogey.java)
- [CarriageCouplingRenderer.java](../../../../vendor/Create-mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/entity/CarriageCouplingRenderer.java)
- [CarriageContraptionEntityRenderer.java](../../../../vendor/Create-mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/entity/CarriageContraptionEntityRenderer.java)
- [CarriageContraptionEntity.java](../../../../vendor/Create-mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/entity/CarriageContraptionEntity.java)
- [StationBlockEntity.java](../../../../vendor/Create-mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/station/StationBlockEntity.java)
- [GlobalRailwayManager.java](../../../../vendor/Create-mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/GlobalRailwayManager.java)
- [RailwaySavedData.java](../../../../vendor/Create-mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/RailwaySavedData.java)
- [SavedDataUtil.java](../../../../vendor/Create-mc1.20.1-6.0.8/src/main/java/com/simibubi/create/foundation/utility/SavedDataUtil.java)
- [CTrains.java](../../../../vendor/Create-mc1.20.1-6.0.8/src/main/java/com/simibubi/create/infrastructure/config/CTrains.java)
- [StandardBogeyBlock.java](../../../../vendor/Create-mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/bogey/StandardBogeyBlock.java)

| 対象・根拠箇所 | 確認事実 | CATへの影響・未確認事項 |
| --- | --- | --- |
| Navigation.findPathTo（449行付近）、search（615〜750行付近） | 駅群を目的地とし、GlobalStationに対してStationTestを呼ぶ。他列車がいる駅には追加コストを与える処理がある | 経路発見と進入許可を区別する。任意の列車端を目標とする拡張範囲は未確定 |
| Train.tick（374〜415行付近）・collideWithOtherTrains（598行付近） | 速度符号で処理順を選び、最初に処理する車両のtravel後に列車衝突判定を呼ぶ。衝突相手が得られると双方にcrashを呼ぶ | 正常接近・誤端接触・ホーム位置待ちを識別する接続方法が必要。標準の判定位置と見た目の接触は同一としない |
| Carriage.getLeadingPoint/getTrailingPoint（407行付近） | 台車の走行点を返す | 車体端・連結器位置そのものではない |
| CarriageBogey.getAnchorPosition（142行付近）・Carriage.updateContraptionAnchors（312行付近） | 台車の走行点から基準位置を算出し、車両の位置・回転用基準を更新する。ディメンション跨ぎ等の分岐がある | サーバー側の位置算出の材料はあるが、CATの明示指定位置への変換・全条件での有効性は未確認 |
| CarriageBogey.updateCouplingAnchor（157行付近）・CarriageContraptionEntityRenderer（73行付近） | 表示用couplingAnchorsは台車の接続オフセットと回転等から計算し、描画処理から更新する | 表示値をそのままサーバー判定の根拠にできるとは確認していない |
| StandardBogeyBlock（34・44行付近）・CarriageCouplingRenderer（39〜85行付近） | 標準台車は走行点間隔2、接続オフセット(0, 7/32, 1)。連結描画は同一Trainの隣接車両の台車側アンカーとcarriageSpacingを用いる | 自由建築した車体先端の自動検出ではない。CATの端指定や実物風外観を標準描画から自動決定しない |
| StationBlockEntity（778行付近）・Train（112、302〜346、1141行付近） | 車両間隔は台車位置の差から設定される整数値で保存される。設定値と実際の基準位置間距離との差を移動補正へ用いる | 新境界の間隔が不整合なら位置補正が生じる可能性がある（推論）。この調査だけから1ブロック刻みの製品制限を独断追加しない。後続の11月1日合意では規定設置による整数基準間隔を採用した（2.4節・4D）。全実座標の整数化を意味しない |
| Trainコンストラクタ（140行付近） | 車両の所属を設定し、Navigation・ScheduleRuntime・占有等の管理状態を初期化する | 新規生成だけではCATの継承要件を満たさない |
| GlobalRailwayManager.addTrain/removeTrain（114行付近）・tickTrains（219行付近） | 列車IDの登録と走行／待機一覧を別に管理し、一覧を反復して列車を処理する | 反復中の構造変更を避ける確定境界が必要（設計上の考察）。専用の利用者向け異常手順を増やす根拠とはしない |
| Carriage.setTrain（96行付近）・CarriageContraptionEntity（319、471、731行付近） | setTrainは所属参照のみ変更。Entityは列車ID・車両番号を保持し、結び付け・同期の処理を持つ | 列車登録、車両所属、読み込み済み／保存中Entityの識別を整合させる必要がある |
| GlobalRailwayManager.tick（189行付近）・Train.earlyTick | 信号区間側の列車・予約情報を消去し、列車側から登録する更新順がある | 編成変更をこの順序へ接続する方法は未確定 |
| RailwaySavedData.save（33行付近）・Train.write（1133行付近） | 線路・信号区間・列車を同じNBTへ保存。列車には車両、間隔、占有、予約区間、時刻表実行、経路等が含まれる | CAT追加状態やEntityまで一括で整合する保証ではない |
| SavedDataUtil.saveWithDatOld（16行付近） | dirty時にNBT化し、一時ファイルへ圧縮保存して旧ファイルを伴う置換を呼ぶ | Minecraft本体の保存呼出し順は未確認。vendor検索ではMinecraftServer/ServerLevel/DimensionDataStorageの本体Javaを発見していない。必要時に1.20.1の該当ソースを利用者へ依頼する |
| CTrains（10行付近）・StationBlockEntity.refreshAssemblyInfo（544行付近） | maxBogeyCount既定20。提供Create内の文字列検索では設定定義と組立情報取得の直接参照を確認。台車情報配列はnull時に設定値長で作る | 走行列車への超過時停止・分離処理は今回検索では見つからないが、他MODや間接経路まで否定しない。設定のライブ反映・既存駅の配列再作成は未確認 |
| Carriage.manageEntities（250行付近）・write/read（451行付近） | 活動範囲外のEntityを保存・除去し、活動範囲内で再生成する経路がある。台車、位置、保存済みEntity情報等を保持する | Entity不在だけで列車喪失としない。ただし連結判定に必要な現在情報が全て揃うとは限らない |
| Carriage.alignEntity（867行付近）・Train.tick（296行付近） | プレイヤー乗車と前方チャンク状態によりcarriageWaitingForChunksを設定し、移動距離を0にする経路がある | 読み込み範囲外でも常に同一条件で走れるとは一般化しない |

## 4B. 実車側の公開資料確認（部分調査）

- [JR EAST Technical Review No.31（2010年）、高速化に向けた車両開発（その他）](https://www.jreast.co.jp/development/tech/pdf_31/Tech-31-29-30.pdf)：29〜30ページの2節。FASTECH360の分割併合装置には前頭カバー、電気連結器、制御装置等があり、連結器配置は既存車との互換条件に関係する。試験車・特定構成の事実であり、全電車の共通構造・CAT初期必須機能とはしない。
- [特許第5841697号（2016-01-13）、鉄道車両の連結器システム](https://patentimages.storage.googleapis.com/1c/86/dc/e119a0d6a95c15/JP5841697B2.pdf)：段落0002、0022、0026、0030。曲線上の位置・向きずれへの対策を記述し、連結速度に「例えば3km/h以下」の例示がある。実施形態であり、動作説明には救援場面を含む。通常の営業増解結の共通上限や、実用実績を確定する資料とは扱わず、CAT速度は未決のまま残す。
- JR東日本のE4系紹介、ユタカ製作所の自動解結システム紹介等も探索したが、今回の範囲では通常増解結の全操作手順・共通速度上限を確定できていない。検索結果に現れた別用途の速度閾値・試験速度を採用しない。

通常の在来線電車の接近、成立確認、未使用台の指令、制動確認、解放手順は追加調査。数値が見つからないことをCAT要件の不成立とは扱わない。

<a id="cab-control-patents-2026-11-02"></a>

### 運転台制御の利用者提供調査と追加照合（2026-11-02）

利用者から、併結時の運転台選択、レバーサーとノッチの区別、常用／非常制動、引通線、機械的連結と制御切替に関する簡易調査を受領。「抜けや誤りの可能性があり、100%信用しない」との指定に従い、以下はGoogle Patents掲載の特許本文の該当箇所を読んだ限定確認とする。採用車種・営業運転取扱い・回路図全体の検証ではない。

| 資料・確認箇所 | 本文で確認したこと | 限界 |
| --- | --- | --- |
| [JP2001233202A](https://patents.google.com/patent/JP2001233202A/ja)、段落0006～0013・0019～0024 | 電気指令式ブレーキの引通線と運転台位置に応じた回路切替を説明。併結終了後、常用から非常位置へ移しキーを抜く取扱い例と、キー・ブレーキ設定器・自動解結スイッチによる前位／中位／後位認識を記載 | 非使用台のレバーが非常位置にあることと、その台が列車全体へ有効な非常指令を出し続けることは同一視しない。接触瞬間の全指令の競合防止・保持力は未確認 |
| [JP3939434B2](https://patents.google.com/patent/JP3939434B2/ja)、段落0001・0009～0013 | 電車・気動車の併結を対象とし、N/F/Bの運転切換スイッチ、相手車種による編成切換スイッチ、接触器・継電器・渡り線を説明。Nは運転切換スイッチの中性位置 | マスコンのニュートラルノッチと混同しない。全電車共通の併結手順や通常停車の保持方式は確定しない |
| [JP6185131B1](https://patents.google.com/patent/JP6185131B1/ja)、ステップS10～S40・請求項6 | 元台でレバーサー前→切、ハンドル非常→抜取、移動先で抜取→非常、切→前の交代手順例を記載 | 主題は交直流電気車の運転台交代時の真空遮断器制御。併結時の全ブレーキ指令回路や留置ブレーキの必要性を示す根拠ではない |

- 提供調査の「複数台が前でも必ずしも競合しない」「常用で停車保持する方式」「機械連結後の引通し確認」等は、一般化や各車種の手順まで今回確認できたとは扱わない。非使用レバーの物理追従についても、全対象の動作検証ではない。
- 各特許の回路記載はあるが、電気接点が接続された瞬間から全指令の競合を防ぐ仕組みを今回解析し終えたわけではない。E231系・E233系の具体回路・順序・実運用は未確認。
- 今回確認した記載だけでは、レバーサー切で全ブレーキが無効になる一般則、交代・併結時の留置ブレーキ必須、留置ブレーキの性能・対象用途は裏付けられない。留置ブレーキ案と明示操作の製品条件は[HUD11.22](../../整理3/マスコン操作とHUD.md#s-11-22)で管理し、参考調査から自動採用しない。

## 4C. 既決事項との照合と判断保留

- 今回確認した範囲では、既決方針の撤回が必要と判明したものはない。ただし全体整合確認・成立性検証の完了ではない。
- 標準衝突時の双方脱線、未読込と喪失の区別、CAT側での台車数上限確認には対応するソース根拠がある。
- 両側停止後のホーム位置待ち・その場の再準備・曲線勾配対応は、接続位置と衝突・間隔補正の技術確認を残す。
- **2026-10-31の製品合意：** 両端の物理的接続位置を利用者が明示指定する。正本は[2.4節](../../整理3/列車構成とプロファイル.md#s-2-4)。今回の調査結果だけで台車基準・外形自動推定を採用しない。
- 速度・許容差・確認時間、対応可能な曲線勾配、実車との意図的な簡略化、成立困難な場合の代替動作は、根拠を揃えて利用者へ判断を求める。
- 通常保存の割込み、複数tickにまたがる更新、反復中の一覧変更、ディスク障害を同一の「途中保存」問題として扱わない。評価は[発生可能性と判断根拠](../../整理3/発生可能性と判断根拠.md)へ接続する。

<a id="coupling-geometry-followup"></a>

## 4D. 位置変換・整数間隔・仮移動の追加確認（2026-11-01保存）

提供済みCreate mc1.20.1-6.0.8の静的確認。実装・実機試験は未実施。製品合意の正本は[2.4節](../../整理3/列車構成とプロファイル.md#s-2-4)。以下の事実から許容値・対応範囲を確定しない。

- AbstractContraptionEntity.toGlobalVectorは車体内位置を回転・基準位置で変換し、OrientedContraptionEntity.applyRotationは初期方向・上下・左右回転を適用する。CarriageContraptionEntity.controlにはサーバー側で運転台との距離を調べる使用例がある。未読込Entityなしでの算出・更新順は未確認。
- Carriage.travelは車両内の台車間・台車内の補正も加える。Train.tickの車両間補正と合わせ、通常移動量0だけでは補正移動まで禁止されないコード構造。停止保持との両立・実際の移動程度は未検証。
- carriageSpacingは整数の保持・保存・TrainPacket同期に加え、間隔補正、連結描画、解体配置、forEachTravellingPointBackwards経由のレンチ再配置・占有再確認へ使われる。端数対応なら保存だけでは足りない。getTotalLengthは整数計算だが提供Create内検索では呼出箇所を確認できず、現在の運行への必須影響とは断定しない。
- StationBlockEntityは走行点をloc + 0.5 ± wheelPointSpacing / 2として配置し、台車位置の差を整数間隔にする。CarriageBogeyの基準位置は走行点位置の平均。直線・水平の対称配置は規定設置による整数化と整合させやすい（考察）。寸法・取付条件未決のため全構成での保証ではない。
- CarriageCouplingRendererは両アンカー間へ向き・伸縮を合わせて描画する。実物の可動限界や自由建築車体の接触判定を保証しない。
- Train.findCollidingTrainは走行点から作る線分等を使い、CAT指定先端や車体外形そのものを判定しない。Train.tickのmaxStress > 4による脱線も間隔ずれの処理であり、CATの角度制限・採用閾値とは別。
- TrackGraphは接続点・区間を保存し、TrackEdgeは曲線データを保持・保存する。位置・長さ・向きを取得でき、GlobalRailwayManagerは鉄道保存情報を読み戻す。形状取得が毎回ブロック読込を要する構造ではないが、全更新経路・鮮度・未読込時の有効性は未確認。
- TrackEdgeの位置計算には隣接区間を参照する補正があり、単純な方向取得と実走行点計算を無条件に同一視しない。線路一点の方向だけで車体・連結器の向きは決まらない。
- TravellingPointは独立インスタンスの仮移動と、信号・駅等の通過処理を何もしないコールバックを用意する。TrainRelocatorはprobeで仮位置を記録し、simulateなら実列車の位置反映前に戻る。CATの予測用基盤の候補であり、その処理全体を無条件に副作用なしとはしない。
- TravellingPoint.travelには中長距離での曲線移動精度に関する注意とTrackEdge側の回避処理への記述がある。長距離一括移動の精度保証はできず、移動刻み・実走行時補正との一致・分岐選択・ポータル・負荷を検証する。CAT用コールバックが予約や信号を変更しないことも確認が必要。

主な追加根拠（上の4Aと併読）：
- [AbstractContraptionEntity.java](../../../../vendor/Create-mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/contraptions/AbstractContraptionEntity.java)
- [OrientedContraptionEntity.java](../../../../vendor/Create-mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/contraptions/OrientedContraptionEntity.java)
- [TrainPacket.java](../../../../vendor/Create-mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/entity/TrainPacket.java)
- [TravellingPoint.java](../../../../vendor/Create-mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/entity/TravellingPoint.java)
- [TrainRelocator.java](../../../../vendor/Create-mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/entity/TrainRelocator.java)
- [TrackGraph.java](../../../../vendor/Create-mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/graph/TrackGraph.java)
- [TrackEdge.java](../../../../vendor/Create-mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/graph/TrackEdge.java)
- [BezierConnection.java](../../../../vendor/Create-mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/track/BezierConnection.java)

次の技術確認候補：仮移動の刻みと実走行補正の差、先読み精度・負荷・有効範囲。現在は利用者の仕様決定方針変更の説明を待ち、調査を自動継続しない。

## 5. 結果を受けた最終精査

A・Bの結果は別々に報告できる。両方を揃えてから、既決事項の維持／変更提案／未確認を一覧化する。製品判断を必要とする項目だけを関連ごとにまとめて利用者に確認する。未確認の全数値収集を議論再開の条件にしない。

2026-10-31保存時の進捗：4A〜4Cの追加調査と接続位置の明示指定合意を記録済み。次は指定位置から台車間隔への対応と、曲線・勾配でも接近停止・ホーム位置待ち・連結確定を成立させる条件を確認する。指定手段・時機は未決。実車の通常増解結手順・速度上限、Minecraft本体の保存呼出し順、異性能計算等は引き続き未確認。実装・実機試験・全体調査完了とは扱わない。

## 変更履歴

| 日付 | 内容 |
| --- | --- |
| 2026-10-31 | 未保存の静的調査・公開資料確認・既決事項照合を保存。明示指定の合意を2.4節へ参照し、次の確認対象を更新 |

| 2026-11-01 | 追加静的調査を4Dへ記録。規定連結器方式の合意と調査保留を正本へ接続。次は利用者の方針変更説明を待つ |
| 2026-11-01 | 移行準備に伴い更新日・再開先・整数基準間隔の後続合意との時点差を明記。新しいソース調査・実機検証は未実施 |
