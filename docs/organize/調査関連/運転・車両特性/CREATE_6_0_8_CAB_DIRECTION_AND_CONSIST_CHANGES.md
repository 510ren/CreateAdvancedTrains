# Create 6.0.8 の運転台方向、列車進行方向、編成変更に関するソース調査

調査日: 2026-09-21  
用途: CAT新規設計のための技術調査。GPT-6 Astraへの引継ぎ資料。  
対象: ローカルの `vendor/Create-mc1.20.1-6.0.8`（`gradle.properties` の `mod_version = 6.0.8`、`artifact_minecraft_version = 1.20.1`、ローカルGit HEAD `0e39312`）。外部ソースは取得していない。

この文書の「承認済みCAT方針」は今回の依頼文のみを根拠とする。旧CAT仕様・旧実装は承認根拠として使わない。以下の `C:` は `vendor/Create-mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/` の略。行番号は上記ローカルソースのもの。記述は **ソース事実** と **CAT設計への推論** を明示的に分ける。

## 承認済みCAT方針との接点

- 列車単位の「主」「副」は固定し、進行方向で自動交換しない。スケジュールは使用する主・副と、そのマスコン基準の前進・後進を指定する。
- 反対向きの副で「後進」を選んだ場合、主で「前進」を選んだ場合と同じ列車移動方向にする。前後切替レバーは前後方向へ操作する。
- CATマスコンとCreate標準運転台は併設しない。標準運転台の存在をCATの動作条件にしない。
- 主・副の設定方式および保存方式は、この調査で確定しない。

## 1. ソースから確認した事実

### 1.1 標準運転台の向きと組立後の情報

| 位置 | 確認した事実 |
| --- | --- |
| `C:contraptions/actors/trainControls/ControlsBlock.java` `ControlsBlock#getStateForPlacement` 53–62行 | 標準運転台は水平 `FACING` を持つ。通常設置時は設置プレイヤーの水平方向の反対、Shift設置時は同方向を向く。 |
| `C:trains/entity/CarriageContraption.java` `capture` 150–185行 | 各運転台の `FACING` を組立方向 `assemblyDirection` と比較する。軸が異なれば `sidewaysControls`、同じ向きなら `forwardControls`、反対向きなら `backwardControls` を立てる。これは各車両内の存在フラグであり、運転台ごとの個数・位置・IDではない。 |
| 同 `assemble` 93–128行 | 横向きの標準運転台がある車両は組立エラー。標準運転台の直後のConductor/座席も、前後別のConductor判定へ変換する。 |
| 同 `writeNBT` / `readNBT` 188–223行 | `AssemblyDirection`、`FrontControls`、`BackControls`、前後別Conductor情報を保存・再読込する。 |
| `C:contraptions/Contraption.java` `capture` / `addBlock` 627–659行、`writeBlocksCompound` / `readBlocksCompound` 901–998行 | 組立ブロックはアンカー基準のローカル `BlockPos`、`BlockState`、必要に応じたBlock Entity NBTとして保持・直列化される。従って標準運転台の個々のローカル位置と `FACING` は車両のブロック一覧から読める。 |
| `C:trains/station/StationBlockEntity.java` 組立処理 770–849行 | 車両を順に組み立て、全編成に少なくとも一つの `hasForwardControls()` がないとCreate標準組立は失敗する。`doubleEnded` はいずれかの車両に後ろ向き標準運転台があるかで決まる。 |

**境界:** `forwardControls` / `backwardControls` と `doubleEnded` は標準運転台の集約情報であり、CATの主・副、現在操作中の台、編成両端の台を表さない。CAT専用マスコンには `AllBlocks.TRAIN_CONTROLS` の走査結果が自動適用されない。

### 1.2 車両ローカル方向から列車移動方向への対応

| 位置 | 確認した事実 |
| --- | --- |
| `C:trains/station/StationBlockEntity.java` `getAssemblyDirection` 625–638行、組立 775–849行 | 駅の線路方向から組立方向を決め、その方向を各 `CarriageContraption` に渡す。車両のリストは組立走査順に作る。 |
| `C:trains/entity/CarriageContraptionEntity.java` `create` 172–179行、`setCarriage` 729–740行 | 組立方向からEntity初期方位を設定し、所属 `Train` とリスト上の `carriageIndex` を結び付ける。 |
| `C:trains/entity/Train.java` フィールド/コンストラクタ 81–157行、`tick` 290–300、374–398行 | 列車の `speed` は符号付き。正なら先頭から、負なら末尾から車両を移動処理する。車両リストの0番が基準前端、末尾が基準後端である。 |
| 同 `getEndpointEdges` 1060–1065行、`C:trains/entity/Carriage.java` `getLeadingPoint` / `getTrailingPoint` 407–425行 | 先頭車のleading TravellingPointと末尾車のtrailing TravellingPointが編成の両端を表す。 |
| `C:trains/entity/Carriage.java` `travel` 138–193行 | 移動距離の符号で前端/後端の追従・線路選択を切り替える。曲線の走行は各車両のTravellingPointが線路グラフ上で処理する。 |

**読み替え:** 同一Create標準組立の車両で、標準運転台の `FACING == assemblyDirection` は列車リスト0番側を向く「前」系、反対は末尾側を向く「後」系となる。ワールド座標の北・南などを、曲線上の編成全体に共通する前後方向として使っていない。

### 1.3 標準手動運転で操作台と前進・後進を決める仕組み

| 位置 | 確認した事実 |
| --- | --- |
| `C:contraptions/actors/trainControls/ControlsInteractionBehaviour.java` `handlePlayerInteraction` 20–40行 | 操作開始時に対象のCarriageContraptionEntityと、その運転台のローカル `BlockPos` を渡す。 |
| `C:contraptions/actors/trainControls/ControlsInputPacket.java` 26–76行、`ControlsServerHandler.java` `receivePressed` / `tick` 24–70行 | 入力は対象Entity IDと運転台ローカル位置を伴ってサーバーへ届き、プレイヤーUUIDごとの一時的な入力コンテキストで対象Entity・位置を保持する。 |
| `C:trains/entity/CarriageContraptionEntity.java` `startControlling` 519–534行 | 手動操作開始時にNavigationをキャンセルし、標準スケジュールをpauseする。 |
| 同 `control` 548–590、659–680行 | 対象位置のブロック `FACING` とEntity初期方位から `inverted` を算出し、前進/後進キーの `targetSpeed` 符号を必要なら反転する。`Train.targetSpeed` を符号付きで設定し、`approachTargetSpeed` を呼ぶ。運転台位置は入力コンテキストにあり、`Train` の主・副のような永続フィールドにはならない。 |
| 同 `control` 642–650行 | 手動操作中に近い駅を探す向きも、符号付き操作目標と `doubleEnded` に依存する。 |

**注意:** `inverted` の比較に使うのは `getInitialOrientation().getCounterClockWise()` であり、車両Entityの現在のワールドyawとの単純比較ではない。操作キーからの符号変換をCATへ移す場合は、この座標系を省略して世界方位だけで決めてはならない。

### 1.4 スケジュール走行と経路探索

| 位置 | 確認した事実 |
| --- | --- |
| `C:trains/schedule/ScheduleRuntime.java` `tick` 102–138行 | 指示から経路候補を得て `Navigation.startNavigation` へ渡す。標準ScheduleRuntime自体には「使用する運転台の個体」の選択がない。 |
| `C:trains/schedule/destination/DestinationInstruction.java` `start` 80–112行 | Destination指示はConductor有無を確認し、駅名に合う駅群から `navigation.findPathTo` を使う。 |
| `C:trains/entity/Navigation.java` `findPathTo` 449–516行、`search` 551–611行 | 前後両方向から線路グラフを探索する。前は先頭車leading point、後ろは末尾車trailing pointを起点とする。候補の採用は前後Conductor、`doubleEnded`、schedule pause、距離/コストに依存する。個々の運転台 `BlockPos` は探索に渡らない。 |
| 同 `startNavigation` 386–435行、`tick` 73–102行 | 選ばれた経路の距離符号から `destinationBehindTrain` を決め、非pause時にはその向きのConductorの存在を確認する。条件を満たさなければ開始失敗/Navigation取消となる。 |
| 同 `tick` 116–119、249–289行 | 走行方向別の先端TravellingPointを使い、`destinationBehindTrain` で速度符号を決める。`Train.currentlyBackwards` も設定する。 |
| `C:trains/entity/Train.java` `hasForwardConductor` / `hasBackwardConductor` 579–596行、`C:trains/entity/CarriageContraptionEntity.java` `checkConductors` 495–517行 | Conductorは全車両の座席/Conductor block由来の前後別の現在値。標準運転台の存在フラグそのものでも、選択中の運転台個体でもない。 |

**結論:** 標準スケジュールは「指定されたマスコンの前進/後進」を表現しない。標準Navigationの経路候補選択と発車可否は、CATが標準運転台を置かない方針の下で、そのまま製品要件へ流用できない部分がある。

### 1.5 保存、再読込、組立解除・再組立

| 位置 | 確認した事実 |
| --- | --- |
| `C:trains/entity/Train.java` `write` / `read` 1133–1197行、`C:trains/RailwaySavedData.java` 保存/読込 42–66行 | 列車UUID、順序付き `Carriages`、車両間隔、`DoubleEnded`、進行方向状態等を保存し、同じ列車UUIDで読み込む。 |
| `C:trains/entity/Carriage.java` `write` / `read` 451–524行、`manageEntities` 250–307行 | 車両は組立済みEntityの直列化データを `Entity` として持ち、Entityがチャンク外でも保存・再生成され得る。生きたEntityが常時ある前提の走査は不完全。 |
| `C:trains/entity/CarriageContraptionEntity.java` `writeAdditional` / `readAdditional` 469–479行、`bindCarriage` 319–325行 | Entityには `TrainId` と `CarriageIndex` が保存され、列車への再結合に使われる。これは列車内位置であって不変の車両IDではない。 |
| `C:trains/entity/Carriage.java` コンストラクタ 54–89行、`write` 451–493行 | `Carriage.id` はプロセス内の増分整数として生成される。`Carriage.write` にそのIDは含まれないので、永続マスコン識別の根拠にできない。 |
| `C:contraptions/Contraption.java` `getBlockEntityNBT` 715–724行、`writeBlocksCompound` 901–946行、`addBlocksToWorld` 1109–1193行 | Block Entity NBTは組立ブロック情報へ取り込まれ、保存時に書かれ、解除時に配置先Block Entityへ読み戻す経路がある。ただしNBT加工、ブロック破損、他アドオンの変換を経ても任意のCAT識別子が保たれることまでは未検証。 |
| `C:trains/entity/Train.java` `disassemble` 732–783行、`C:trains/station/StationBlockEntity.java` 組立 848–873行 | 組立解除では列車を削除し、次の標準組立では `UUID.randomUUID()` により新しい列車を作る。列車UUIDだけでは解除/再組立前後の同じマスコンを結べない。 |

## 2. CATへ適用できそうな方法（設計上の推論、未承認）

1. **方向変換:** 選択されたCATマスコンの車両内 `FACING`、その車両の組立方向、列車内の車両順序から、マスコンの「前進」が列車リスト0番側か末尾側かを求める。`主で前進` と `反対向きの副で後進` は、符号が2回反転して同じ列車方向になる。標準運転台の `capture` と `control` はこの変換の参照実装だが、CATマスコン自体の検出、向き定義、入力は別途必要。
2. **曲線・別車両:** 対応判定には現在のワールドyawを使わず、各車両のローカル向きと編成順序を使う。Createの移動は先頭/末尾TravellingPointを使用するため、別車両や曲線上でも論理上の前後を定義できる。ただし別アドオンが車両を反転して取り込む場合は、組立方向と編成順序の整合を再確認する必要がある。
3. **個体識別候補:** 組立中は「列車UUID + 車両インデックス + ローカルBlockPos」で場所を指せる。保存・編成変更・再組立をまたぐ個体識別には、CAT固有の永続識別子をマスコン側の保存情報へ持たせる案がある。これは保存方式の提案であって承認済み仕様ではない。重複ID、複製、破損、解除時のNBT変換をどう扱うか要決定。
4. **標準機能から独立したCAT走行:** CAT専用マスコンのみの列車を成立させるには、標準組立の `hasForwardControls` 要件、標準NavigationのConductor/`doubleEnded` による方向制限、および標準スケジュールの自動経路選択を、どの境界でCAT用の適格性・方向指定・経路指定へ置き換えるか設計する必要がある。単に標準運転台の `FACING` 変換を使うだけでは成立しない。

方向変換の概念表（CATマスコンの「前進」をその設置向きと定義した場合の**推論**）:

| 選択台の向き | その台で前進 | その台で後進 |
| --- | --- | --- |
| 列車リスト0番側 | 列車の正方向 | 列車の負方向 |
| 列車リスト末尾側 | 列車の負方向 | 列車の正方向 |

したがって反対向きの2台では、0番側を向く台の「前進」と末尾側を向く台の「後進」が同じ正方向になる。主/副という名称だけから符号は決まらず、各台の向きが必要。

### 向かい合う2台・別車両・曲線の判定可否

| ケース | Createから得られる根拠 | 現段階の評価 |
| --- | --- | --- |
| 同一車両の向かい合う2台 | 車両のブロック一覧に各ローカル位置と `BlockState` が残る。標準運転台なら `FACING` も残る。 | **方向関係は判定可能。** どちらを主/副にするかはCreateは決めない。 |
| 異なる車両の2台 | 順序付き `Train.carriages` と、各車両の組立情報がある。 | **標準組立の向き整合を前提に判定可能。** 動的連結で整合が崩れる場合は要検証。 |
| 曲線上の編成 | 前後のTravellingPointと車両別の線路追従がある。 | **ワールド方位を使わない論理判定が可能。** ゲーム内挙動は未検証。 |

## 3. 他アドオンによる連結・分離後の最外側マスコン再判定

### Create側で確認できる範囲

- **現在の車両順序:** `Train.carriages` は順序付きで、正方向は0番側、逆方向は末尾側。`Train.carriageSpacing` も車両間順に保存される。根拠は `Train.java` 110–112、140–154、299–398、1133–1195行。
- **各車両内の位置と向き:** 組立済みの `CarriageContraption` ブロック一覧からCATブロックを識別できれば、ローカル `BlockPos` と `BlockState`、存在する場合はBlock Entity NBTを取得できる。根拠は `Contraption.java` 627–659、901–998行、`Carriage.java` 451–524行。アンロード時は生Entity走査だけに依存できない。
- **構成変化を観察する材料:** `GlobalRailwayManager.java` `addTrain` / `removeTrain` 114–125行、`RailwaySavedData.java` 42–66行から、列車集合と列車データは取得できる。車両リスト・ブロック情報の再走査により「現在の構成」を照合する設計は可能。ただしこのソースで確認した `addTrain` / `removeTrain` は組立/解除で使われ、任意の他アドオンの連結/分離通知を保証するものではない。
- **最外側の求め方の候補:** 方向別に、各CATマスコンの所属車両の現在のインデックスを第一キーとし、同一車両内は組立軸に沿うローカル位置を第二キーとして端に近い候補を選ぶ。車両の物理寸法、台の設置向き、同位置の競合、両端の複数台の優先順位は別途仕様化が必要。最外側という地理的選択と、固定主/副という役割は別の情報である。

### 連結アドオン側のソースが必要な範囲

- 連結・分離時に既存 `Train` を変更するか、新 `Train` を作るか。列車UUID、`Carriage` オブジェクト、`carriageIndex`、順序、車両方向、組立済みEntity NBTをどう引き継ぐか。
- 一方の編成を逆順/反転して連結できるか。その際に `CarriageContraption.getAssemblyDirection()`、ブロックのローカル位置/`FACING`、先頭・末尾TravellingPointをどう変換するか。
- 構成変更完了を知らせるAPI・イベント・確定タイミングがあるか。走行中、チャンク未読込、次元またぎ、保存直前/再読込直後に照合してよい状態か。
- 連結前の主/副識別子とスケジュールの扱い、両編成が同じ識別子を持つ場合、分離後どちらの列車へ各台を割り当てるか。

**判定:** Create側の現在値から、向き情報のあるCATマスコンを再列挙して「各方向の最外側」を計算するための基礎データは得られる。ただし他アドオンによる変更後にそのデータが正規化されているか、いつ再判定すべきか、識別子が保たれるかはCreate 6.0.8だけでは確定できない。対象の連結アドオン名・バージョン・ローカルソースが提示されていないため、当該処理は未確認。

## 4. 未確認事項・制約・追加判断が必要な点

1. **CATブロックの物理方向:** CATマスコンの `FACING` がレバーの「前進」方向をどう表すか、組立軸と直交する設置を許すか。標準運転台のルールはCATへ自動適用されない。
2. **主/副の割当:** 初期設定の手順、主/副の変更可否、連結/分離で双方の役割が競合した場合の解決、最外側再判定と固定役割の関係。調査担当として確定しない。
3. **保存と再結合:** CATマスコン識別子の形式・保存場所、組立解除/再組立時の再対応、NBT複製・消失時の扱い、列車UUID変更後のスケジュール参照の移行。調査担当として確定しない。
4. **走行統合:** 標準運転台なしでの組立経路、CAT専用の方向別運転適格性、Create標準Conductorチェックとの関係、スケジュールで指定した方向が経路探索・信号・駅到着処理と矛盾した場合の処理。
5. **他アドオン連結:** 対象アドオンのソースが必要。特に車両反転、インデックス更新、NBT引継ぎ、変更完了通知を静的に確認する必要がある。
6. **実行時:** 曲線・チャンクアンロード・再読込・ポータル・他アドオン連結/分離を含むMinecraft上の確認は行っていない。静的ソース調査のみである。

本作業ではCATコードの実装・変更、Createコードの変更、ビルド、ゲーム内検証を行っていない。
