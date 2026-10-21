# Create Mod：列車性能・運行制御・線路敷設アドオン調査

**調査日：2026年10月19日**  
**対象：Minecraft Java Edition 1.20.1 / Minecraft Forge**  
**想定するCreate本体：Create 6.0.8（ただし、全MODの6.0.8互換性を保証するものではありません）**

## 目次

1. [調査条件と確認基準](#1-調査条件と確認基準)
2. [列車の走行性能・運転方式](#2-列車の走行性能運転方式8件)
3. [駆動・動力・耐久性](#3-駆動動力耐久性3件)
4. [自動運転・信号・運行管理](#4-自動運転信号運行管理8件)
5. [列車・線路網の処理性能](#5-列車線路網の処理性能4件)
6. [列車の周辺機能](#6-列車の周辺機能5件)
7. [線路敷設・線形設計・自動建設](#7-線路敷設線形設計自動建設新規7件)
8. [既出MODの線路敷設関連機能](#8-既出modの線路敷設関連機能重複計上なし)
9. [線路網の可視化・建設補助](#9-線路網の可視化建設補助追加2件)
10. [Forge 1.20.1条件から除外した候補](#10-forge-1201条件から除外した候補)
11. [用途別の選び方・互換性上の注意](#11-用途別の選び方互換性上の注意)
12. [集計と限界](#12-集計と限界)

---

## 1. 調査条件と確認基準

- **対象**：Createの列車について、最高速度・加減速・制動・ノッチ・駆動力・信号・自動運転・ダイヤ・列車の処理性能を拡張するMOD、およびCreateの線路の配置・曲線・勾配・分岐・種類・建設自動化・路線可視化に関連するMOD。
- **採否**：配布ページに **Minecraft 1.20.1 / Forge** と明記されたファイルがあることを基本条件としました。開発中・Beta版でも対象としています。
- **重要な区別**：「Forge 1.20.1用のファイルが存在する」ことは、「Create 6.0.8・Steam 'n' Rails 1.7.x・他のアドオンと同時導入して正常動作する」ことを意味しません。
- **確認方法**：原則としてCurseForge・Modrinthの説明とバージョン別ファイル一覧を参照しました。GitHubの未公開ブランチ・Discord限定配布・ソースのみ存在する未配布物を網羅できたとは限りません。
- **件数**：前回の列車関連28件に対し、線路敷設の新規7件と鉄道網可視化の新規2件を追加しました。**重複を除く37件**です。
- MOD名のクリックで配布ページを開けます。ファイルは必ず **Game Version = 1.20.1、Loader = Forge** で絞ってください。トップページに出る最新版がNeoForge 1.21.1等であることがあります。

## 2. 列車の走行性能・運転方式（8件）

| No. | アドオン | 主な機能 | 状態・注意点 |
|---:|---|---|---|
| 1 | [Create: Individual Train Speeds](https://www.curseforge.com/minecraft/mc-mods/create-individual-train-speeds) | **列車ごと**に加速度・最高速度・カーブの上限速度を変更。駅UIから設定 | 公開済み。Create 6.0.8対応ファイルの案内あり |
| 2 | [Create: Let's become a real train controller](https://www.curseforge.com/minecraft/mc-mods/create-lets-become-a-real-train-controller) | 力行P・中立N・制動Bなどの**ノッチ式運転**を追加 | 公開済み。各ノッチの挙動やCreate設定への依存を確認 |
| 3 | [Create Realism](https://www.curseforge.com/minecraft/mc-mods/create-realism) | 編成長・運転手付き車両数に応じた加速度調整。前方情報や制動曲線を表示 | 公開済み。ETCS風表示の各項目が実際に速度を制御するとは限らない |
| 4 | [Create: Accelerate](https://modrinth.com/mod/create-accelerate) | **高速鉄道・Maglev**と高加速度・高速度の列車／線路を追加 | 開発段階。専用線路は第8節にも再掲 |
| 5 | [Create Train Tweaks](https://www.curseforge.com/minecraft/mc-mods/create-train-tweaks) | ターボ・スーパーチャージャーで最高速度・加速を強化 | 公開済み。Steam 'n' Railsの一部線路と非互換の注意書きあり |
| 6 | [Create: Railway Evolution](https://modrinth.com/mod/railway-evolution) | 障害物検知・段階的制動・仮想閉塞・進路予約・列車優先順位 | 開発中。リリースによって利用可能機能が異なる |
| 7 | [Ultimate Railway](https://www.curseforge.com/minecraft/mc-mods/ultimative-railway) | 速度制限・赤信号と非常制動・運転台・架線に関連する制御 | 初期版。機能完成度と必要な依存MODを要確認 |
| 8 | [Railways Additions](https://www.curseforge.com/minecraft/mc-mods/railways-additions) | クルーズコントロール（定速維持）、遠隔操作など | 公開済み |

### 性能変更の観点で重要な点

- **Individual Train Speeds**：Create全体の速度設定ではなく、個別の列車特性を作り分ける目的に適しています。
- **Let's become a real train controller**：速度目標を決めるだけの制御と、P/Bの段によって加減速する制御の違いを比較できます。
- **Create Realism**：長編成による加速性能の変化と制動情報の表示が特徴です。物理量を用いた完全な車両力学モデルだと断定しないでください。
- **Railway Evolution**：制動・閉塞・優先順位に関する技術的な比較候補ですが、掲載機能がForge用の同一リリースに全て揃うかは個別に確認が必要です。

## 3. 駆動・動力・耐久性（3件）

| No. | アドオン | 主な機能 | 状態・注意点 |
|---:|---|---|---|
| 9 | [Create: Armored Rails](https://www.curseforge.com/minecraft/mc-mods/create-armored-rails) | 列車のHP・装甲・修理・損傷による速度低下・エンジン/車体の速度強化 | 公開済み。Create 6.0.8以上を要求する版あり |
| 10 | [Create: Train Transmission [VS]](https://www.curseforge.com/minecraft/mc-mods/create-train-transmission) | SU/RPMに基づく駆動系。変速装置の種類と動力で出力可能な最高速度を変更 | Beta。Create Interactive / Valkyrien Skiesを想定 |
| 11 | [Create: Electrified Trains](https://www.curseforge.com/minecraft/mc-mods/create-electrified-trains) | 架線からのFE電力供給を列車運行の条件に追加 | Beta。Create: Pantographs & Wiresを必要とする構成 |

**Train Transmissionの例**：説明にあるAndesite / Brass / Sturdyの変速装置でCreate側の最高速度に対する到達割合が変化します。設定、バージョン、SU供給量によって実際の値が異なる可能性があります。

## 4. 自動運転・信号・運行管理（8件）

| No. | アドオン | 主な機能 | 状態・注意点 |
|---:|---|---|---|
| 12 | [Create: Tramways](https://www.curseforge.com/minecraft/mc-mods/tramways) | 自動列車向けの速度制限標識、信号表示、リクエスト停車 | 公開済み |
| 13 | [Create Train Autonomy](https://www.curseforge.com/minecraft/mc-mods/create-train-autonomy) | 駅を目的地としない直接運転、速度指定、前進・後退、採掘・帰還・線路延長など | Beta。線路建設機能は第8節参照 |
| 14 | [Create Dispatcher](https://www.curseforge.com/minecraft/mc-mods/create-dispatcher) | 運行計画・ダイヤ作成、間隔・ホーム競合・デッドロック等の検出 | 公開済み。通常版とWeb UIサーバー版のファイル選択に注意 |
| 15 | [Create: Routes](https://www.curseforge.com/minecraft/mc-mods/create-routes) | 複数の列車で再利用する共通ルートやスケジュール管理 | 公開済み |
| 16 | [Create Railways Navigator](https://www.curseforge.com/minecraft/mc-mods/create-railways-navigator) | ルート検索・運行情報・車内案内・スケジュール関連機能 | 公開済み。Create 6用ファイルを選択 |
| 17 | [Create: Maintenance Control](https://www.curseforge.com/minecraft/mc-mods/create-maintenance-control) | 駅の一時閉鎖・列車の自動通過 | 公開済み |
| 18 | [Create: Steam 'n' Rails](https://www.curseforge.com/minecraft/mc-mods/create-steam-n-rails) | 車両連結・切離し、信号・線路種・踏切・分岐関係の拡張 | 公開済み。Create 6用Forge 1.20.1版「1.7.3 C6」がある。線路関連は第8節に再掲 |
| 19 | [Create: Transit](https://www.curseforge.com/minecraft/mc-mods/create-transit) | 貨物輸送や鉄道物流の自動割り当て | 公開済み |

> **運転保安の扱い**：ゲーム内の「ATC・ATS・ETCS風表示」「自動停止」と、実車の保安装置の安全性・制動保証は別物です。名前や画面デザインだけで実装範囲を判定しないでください。

## 5. 列車・線路網の処理性能（4件）

| No. | アドオン | 改善対象 | 状態・注意点 |
|---:|---|---|---|
| 20 | [Create: Threaded Trains](https://www.curseforge.com/minecraft/mc-mods/create-threaded-trains) | 列車・鉄道網計算の別スレッド化 | 公開済み。列車計算Mixin同士の競合、同期不良に注意 |
| 21 | [Create Optimized Trains](https://www.curseforge.com/minecraft/mc-mods/create-optimized-trains) | 遠距離列車のLOD・処理簡略化・先読み | 公開済み。Create 6.0.8以降向けの説明あり |
| 22 | [Train Network Optimisations](https://www.curseforge.com/minecraft/mc-mods/trainnetworkoptimisations) | 未ロードチャンクに対する線路ネットワーク接続処理などを最適化 | 公開済み。線路を作る際の負荷にも関係 |
| 23 | [Create FastTrack](https://www.curseforge.com/minecraft/mc-mods/create-fasttrack) | 線路の設置・破壊時に発生する鉄道グラフ探索を高速化 | Forge 1.20.1専用。Create 6.0.8以前の`TrackGraph#findDisconnectedGraphs`の探索経路をMixinで改善 |

**FastTrackの具体例**：説明によれば、Create 6.0.8以前のBFS実装で`ArrayList#remove(0)`を多用していた箇所を`ArrayDeque`に置換し、ネットワーク分割探索に伴う大きなラグを抑制します。これは**列車の加速度を上げるMODではなく、線路を追加・削除する際のCPU負荷を減らすMOD**です。参照：[作者による技術説明](https://www.curseforge.com/minecraft/mc-mods/create-fasttrack)。

## 6. 列車の周辺機能（5件）

| No. | アドオン | 関連機能 | 状態・注意点 |
|---:|---|---|---|
| 24 | [Create: Pantographs & Wires](https://www.curseforge.com/minecraft/mc-mods/create-pantographs-and-wires) | 架線・パンタグラフ・電化設備 | 公開済み。Create 6対応ファイルと必要なDragonLibの版を確認 |
| 25 | [Control Flex x Create](https://www.curseforge.com/minecraft/mc-mods/cfx-x-create) | コントローラーで列車の力行・制動などを操作 | 公開済み |
| 26 | [Create: Trains On Trains](https://www.curseforge.com/minecraft/mc-mods/create-trains-on-trains) | 列車上の台車・列車構造物など | 公開済み |
| 27 | [Create: Modern Train Parts](https://www.curseforge.com/minecraft/mc-mods/create-modern-train-parts) | 可動パンタグラフ・近代的な車両設備 | 公開済み。Create 6向けForge 1.20.1ファイルあり |
| 28 | [Create Train Utilities](https://www.curseforge.com/minecraft/mc-mods/create-trainutilities) | 複数ドアの一括制御、自動閉扉、停車設備 | 公開済み |

---

## 7. 線路敷設・線形設計・自動建設（新規7件）

ここからが**今回新たに調査した線路敷設関連アドオン**です。いずれもMinecraft 1.20.1 / Forgeの公開ファイルを確認しています。ただし、Create 6.0.8との実機互換性は個別検証が必要です。

| No. | アドオン | 分類 | 具体的な機能 | 確認できたForge 1.20.1用ファイル／注意 |
|---:|---|---|---|---|
| 29 | [create:track mechanism modification](https://www.curseforge.com/minecraft/mc-mods/create-track-mechanism-modification) | **線形制限の緩和** | Createの線路接続における幾何条件を緩和。曲線・勾配・交差の自由度を拡大。最長500ブロックの接続。`Ctrl+X`で直角曲線モード、配置ガイドも追加 | `create_track_mechanism_modification-1.20.1-forge-1.8.9.jar`等。急曲線が実際の列車通行や他MODとの互換性を保証するわけではない |
| 30 | [Create: Parallel Tracks](https://www.curseforge.com/minecraft/mc-mods/create-parallel-tracks) | **複線・並行線** | 元の線路を基準に並行線路を作る。直線・カーブ・勾配を追従し、間隔指定とプレビューに対応 | `create_parallel_tracks-1.20.1-1.0.jar`。説明の最新版NeoForge前提の記述とForge用ファイルを取り違えないこと |
| 31 | [Create: Auto Track](https://www.curseforge.com/minecraft/mc-mods/create-auto-track) | **自動敷設** | Builder Blockで目的地を設定し、レールや路盤を自動設置。線路・道床の種類を調整可能 | `create_auto_track-1.0.1_hf.jar`。トップページの新しい2.x版は1.21.1 NeoForge |
| 32 | [Create: Railway Automation](https://www.curseforge.com/minecraft/mc-mods/create-railway-automation) | **目的地指定式の敷設** | Track Placer BlockとMap Locator Itemで行先を指定し、離れた位置まで線路接続 | `Railway-Automation-1.20.1-1.0.jar` |
| 33 | [Create: Railroad Wand](https://www.curseforge.com/minecraft/mc-mods/create-railroad-wand) | **テンプレート建設** | 建設用ワンドを使い、単線・複線のトンネルや開放区間等を配置 | `create_railroad_wand-1.2.2-hotfix.jar`。クリエイティブ向け、取り消し機能の制限に注意 |
| 34 | [Railways Untold](https://www.curseforge.com/minecraft/mc-mods/railways-untold) | **路線の自動生成** | ワールド内でCreateの鉄道路線を手続き的に生成。地形・トンネル・村の駅・線路種を考慮 | `railwaysuntold-1.20.1-forge-1.2.2.jar`。既存ワールドの生成範囲や設定の扱いを事前確認 |
| 35 | [Create: Dual Gauge Tracks](https://www.curseforge.com/minecraft/mc-mods/create-dual-gauge-tracks) | **線路規格** | 1本の線路に異なる軌間に対応する台車を走らせるための複軌間線路 | **`create_dual_guage_track-2.0.0-forge-1.20.1.jar`**（2026-10-09）。古い1.0版の「1.20.1 NeoForge」と間違えないこと |

### 7.1 線路の曲線・勾配・交差を自由に作りたい場合

**第一候補：create:track mechanism modification**

- Create本体では置けない曲線・勾配・接続を許可する機能が説明されています。
- 単一の接続距離を最大500ブロックへ延長するという説明があります。
- `Ctrl+X`で直角曲線モード、`Ctrl`を押した敷設で四分円形のカーブを作る機能があります（説明上の半径はおおむね2～32ブロック）。
- 接続時の座標差・直角曲線の判定などをHUDに表示します。
- **注意**：これは「線路を置く際の制限緩和」であり、車両の速度制限・カーブ加速度・脱線等の力学を厳密に再計算するMODではありません。極端な線形が安全・正常に走行できるかは別途テストが必要です。

出典：[機能説明とForge用ファイル](https://www.curseforge.com/minecraft/mc-mods/create-track-mechanism-modification/files/all)。

### 7.2 複線・複々線・車庫線を効率よく建設したい場合

**第一候補：Create: Parallel Tracks**

- 既存の基準線に合わせて並行線を生成し、線間距離を設定できます。
- 曲線区間・勾配の敷設を補助する点が特徴です。
- 配布ページでForge 1.20.1の`create_parallel_tracks-1.20.1-1.0.jar`を確認できます。
- 現行のページ説明に書かれたNeoForge/1.21.1要件を、そのまま**過去のForge 1.20.1ファイルの要件**として扱わないでください。ただし、Forge版と現行版で機能差がある可能性もあります。

出典：[1.20.1 Forge用ファイル](https://www.curseforge.com/minecraft/mc-mods/create-parallel-tracks/files/all)。

### 7.3 大量の線路を建設する場合

| 目的 | 候補 | 比較のポイント |
|---|---|---|
| 線路と路盤を機械で延長 | [Auto Track](https://www.curseforge.com/minecraft/mc-mods/create-auto-track) | Builder Blockの操作、路盤、対応レール種類、設置時の消費資材 |
| 離れた目的地へ線路を結ぶ | [Railway Automation](https://www.curseforge.com/minecraft/mc-mods/create-railway-automation) | Track Placer・Map Locatorの指定方法、経路の決め方 |
| トンネル・複線区間を繰り返し作る | [Railroad Wand](https://www.curseforge.com/minecraft/mc-mods/create-railroad-wand) | テンプレート種類、建設モード、配置ミス時の復元 |
| ワールド全体に路線を自動生成 | [Railways Untold](https://www.curseforge.com/minecraft/mc-mods/railways-untold) | 地形・村・既存建築への影響、ワールド生成範囲 |

**自動建設系MODを既存ワールドに入れる際はバックアップが必須です。** 特に大量のチャンクを編集する機能では、Undoがない場合があります。

### 7.4 軌間や線路規格を追加したい場合

**Create: Dual Gauge Tracks**は、異なる軌間の車両が共用する線路を追加する系統です。配布ページで、2026年8月の旧1.0ファイルは**1.20.1 NeoForge**と表記されている一方、2026年10月9日の **2.0.0は1.20.1 Forge**用であることが確認できます。**旧版ではなく2.0.0のForgeファイル**を選択してください。

確認用：[Forgeファイルの詳細ページ](https://www.curseforge.com/minecraft/mc-mods/create-dual-gauge-tracks/files/9108299)。

## 8. 既出MODの線路敷設関連機能（重複計上なし）

前半28件に含まれるため、新規件数には加えていません。

| MOD（上の番号） | 線路関連の使い道 |
|---|---|
| [Steam 'n' Rails](https://www.curseforge.com/minecraft/mc-mods/create-steam-n-rails)（No.18） | 線路素材・種類の追加、特殊な鉄道設備、交差・踏切など。Create 6向けForge 1.20.1の1.7.3 C6が公開済み |
| [Create: Accelerate](https://modrinth.com/mod/create-accelerate)（No.4） | 高速鉄道専用線路、Maglev用線路など新しい線路規格 |
| [Create Train Autonomy](https://www.curseforge.com/minecraft/mc-mods/create-train-autonomy)（No.13） | 自律走行する列車が採掘・線路延長・資材搬送に関与する建設用途 |
| [Create FastTrack](https://www.curseforge.com/minecraft/mc-mods/create-fasttrack)（No.23） | 長大な鉄道網でレールを設置・削除した際のグラフ再計算ラグを軽減 |
| [Train Network Optimisations](https://www.curseforge.com/minecraft/mc-mods/trainnetworkoptimisations)（No.22） | 線路ネットワークの構築・接続時の処理負荷の軽減 |
| [Create: Pantographs & Wires](https://www.curseforge.com/minecraft/mc-mods/create-pantographs-and-wires)（No.24） | 架線・パンタグラフなどの線路沿いインフラの敷設 |

## 9. 線路網の可視化・建設補助（追加2件）

これらは**線路を直接配置するMODではありません**。大規模な路線網の設計・点検・運行状態確認に関係するため、関連候補として収録します。

| No. | MOD | 機能 | Forge 1.20.1と注意点 |
|---:|---|---|---|
| 36 | [Ultimate Track Map](https://www.curseforge.com/minecraft/mc-mods/ultimate-track-map) | 線路・駅・信号・踏切・列車を配線略図として表示。列車運行上の問題も表示 | `utm-0.1.0.jar`、Forge 47.x、Create 6.0.8+、**Ultimate Railway（URM）0.1.0+が必須**。作者のテストは当初シングルプレイのみ |
| 37 | [Create Track Map: Fork²](https://www.curseforge.com/minecraft/mc-mods/create-track-map-fork2) | 線路網・信号・駅・列車をWeb地図でリアルタイム表示。大規模ネットワークでの描画／通信負荷を削減 | `create-track-map-2.1+mc1.20.1-forge.jar`（Beta）。1.20.1 Forge 47.1.x、Create 6.0.6+と説明 |

> **別候補**：元の[Create Track Map](https://modrinth.com/mod/create-track-map)や[Create Track Map: Forge](https://www.curseforge.com/minecraft/mc-mods/create-track-map-forge)も知られています。ただし、CurseForgeの一部1.20.1ファイルは`neoforge`名で登録され、表示ローダーにも差異があります。この資料では、ファイル名まで`forge`を確認できる**Fork²**を採用し、重複するマップ派生MODを独立件数に加えていません。

---

## 10. Forge 1.20.1条件から除外した候補

| MOD | 関連する機能 | 除外理由 |
|---|---|---|
| [Create: Train Physics](https://www.curseforge.com/minecraft/mc-mods/create-train-physics) | 質量・牽引力・燃料・走行物理 | 確認できた配布版がMinecraft 1.21.1 / NeoForge向け |
| [Create Train Physics Reloaded](https://www.curseforge.com/minecraft/mc-mods/create-train-physics-reloaded) | 現実的な列車物理 | Forge 1.20.1の配布版を確認できず |
| [Create: Speed Signals](https://www.curseforge.com/minecraft/mc-mods/create-speed-signals) | 信号・閉塞の速度制限 | 確認できた配布版はMinecraft 1.21.1 / NeoForge向け |
| [Create: Train Cruise Control](https://www.curseforge.com/minecraft/mc-mods/create-train-cruise-control) | 定速運転 | 確認できた配布版はMinecraft 1.21.1 / NeoForge向け（1.20.1ならRailways Additionsを参照） |
| [Metro: Utilities](https://www.curseforge.com/minecraft/mc-mods/metro-utilities) | 都市鉄道の運行補助 | Forge 1.20.1版を確認できず |
| [Create Walkable Tracks](https://www.curseforge.com/minecraft/mc-mods/create-walkable-tracks) | 線路の歩行・敷設補助 | 確認できた版が1.18.2 Forge向け |
| [Trackwork](https://www.curseforge.com/minecraft/mc-mods/trackwork) | 履帯／車輪の物理 | 戦車などの履帯であり、Create列車用の鉄道レール敷設MODではない |
| [Create: Extra Gauges](https://www.curseforge.com/minecraft/mc-mods/create-extra-gauges) | 計器類 | Gaugeは**計器**を意味し、列車の軌間追加ではない |
| [BetterRails](https://www.curseforge.com/minecraft/mc-mods/betterrails) | Minecraftのレール機能改善 | 主にバニラのトロッコ・レール系統。Create列車のスプライン線路を拡張する根拠が不十分 |

**補足**：今後新しいForge 1.20.1ビルドが公開される可能性はあります。除外は2026年10月19日時点の確認結果であり、将来の非対応を断定するものではありません。

## 11. 用途別の選び方・互換性上の注意

### 11.1 やりたいこと別の比較

| 目的 | 最初に確認するMOD |
|---|---|
| 列車ごとに速度・加速度を変える | Individual Train Speeds |
| P1～P5、B1～B7など段階運転の構想と比較する | Let's become a real train controller |
| 編成長や駆動装置によって走行性能を変更する | Create Realism、Train Transmission |
| 速度制限・障害物・制動曲線・列車優先度を扱う | Tramways、Railway Evolution、Ultimate Railway |
| 列車を直接操作して自律運行・線路延長する | Train Autonomy |
| 曲線半径・勾配の敷設制約を緩和する | **track mechanism modification** |
| 複線・複々線・留置線を敷設する | **Parallel Tracks**、Steam 'n' Rails |
| 線路の敷設を機械で自動化する | Auto Track、Railway Automation |
| トンネル・橋梁等の定型施工を短縮する | Railroad Wand |
| 自然生成する路線や大規模鉄道網を作る | Railways Untold |
| 異なる軌間の車両を共用線路に通す | Dual Gauge Tracks |
| 巨大路線のレール設置・削除時のラグを減らす | **Create FastTrack**、Train Network Optimisations |
| 線路・信号・列車を地図で監視する | Ultimate Track Map、Track Map: Fork² |

### 11.2 Create 6.0.8環境での注意

1. **Create本体の互換性**：Minecraft 1.20.1 Forge版であっても、Create 0.5.x向けMODとCreate 6.0.8向けMODは互換とは限りません。ファイル名、更新履歴、依存MODのCreate必要バージョンを確認してください。
2. **Steam 'n' Rails**：1.20.1 ForgeにもCreate 6用と旧Create用があります。例えば**1.7.3 Forge 1.20.1 C6**が確認できます。旧C0.5用との取り違えに注意してください。
3. **Mixinの競合**：Threaded Trains、Optimized Trains、FastTrack、線路配置制約変更MOD、速度・加減速・列車制御MODは、Create内部の近い処理に介入する可能性があります。全組合せの互換性は未検証です。
4. **曲線の走行安定性**：線路が設置できることと、列車が正常に曲線を通過できること、他MODの最高速度・制動制御がその線形を適切に評価することは別問題です。
5. **ワールド破壊リスク**：Railroad Wand、Auto Track、Railway Automation、Railways Untoldなどの大量建設機能は、必ず**ワールドのコピー**で試し、バックアップを確保してください。
6. **依存関係**：Pantographs & WiresのDragonLib、Electrified Trainsの架線MOD、Ultimate Track MapのUltimate Railwayなどは配布ページで別途確認してください。
7. **サーバー運用**：クライアント側だけで機能するMODと、クライアント・サーバーの両方に必要なMODがあります。配布ページのEnvironment表示だけでなく説明・依存関係を確認してください。
8. **開発用のコード参照**：Create内部を変更するMODを比較する場合は、ライセンス、ソースコード公開の有無、実際に介入するメソッドとTick順序を分けて確認してください。機能説明が似ていても実装方式が同じとは限りません。

### 11.3 実験時の推奨順序

- **第1段階：敷設単体試験**：Create 6.0.8 + 敷設MODを1種類のみ導入し、直線・曲線・勾配・分岐・長距離接続を試します。
- **第2段階：走行試験**：同じ線路に単独列車・長編成・高速列車を走らせ、衝突判定・信号認識・曲線通過を確認します。
- **第3段階：併用試験**：Steam 'n' Railsや速度制御MODを1つずつ追加し、クラッシュログと列車速度の変化を確認します。
- **第4段階：大規模負荷試験**：複線区間・多数の分岐・長距離路線でTPSと線路設置時の停止時間を計測します。FastTrackなどの効果は同じワールド・同じ手順で比較します。

## 12. 集計と限界

| 区分 | 重複を除いた件数 |
|---|---:|
| 走行性能・運転方式 | 8 |
| 駆動・動力・耐久性 | 3 |
| 自動運転・信号・運行管理 | 8 |
| 列車・線路網の処理性能 | 4 |
| 列車の周辺機能 | 5 |
| **今回追加：線路敷設・線形・自動建設** | **7** |
| **今回追加：路線網可視化** | **2** |
| **総計（同一MODの再掲を除外）** | **37** |

**これは2026年10月19日時点に公開情報から確認できた候補一覧であり、「世の中に存在する全MOD」を証明するものではありません。** ダウンロード数・人気・スター数は選定条件にはしていません。未公開プロジェクトや説明にForge対応が書かれていないものは、厳密な対応確認ができないため含めていません。

なお、**37件のすべてをMinecraft 1.20.1 Forge + Create 6.0.8上で実際に起動・共存・走行テストしたわけではありません**。導入可否を確定したい場合は各ファイルの依存関係と実機検証が必要です。

---

### 主要な確認用リンク（線路敷設）

- [track mechanism modification / ファイル一覧](https://www.curseforge.com/minecraft/mc-mods/create-track-mechanism-modification/files/all)
- [Parallel Tracks / ファイル一覧](https://www.curseforge.com/minecraft/mc-mods/create-parallel-tracks/files/all)
- [Auto Track / ファイル一覧](https://www.curseforge.com/minecraft/mc-mods/create-auto-track/files/all)
- [Railway Automation / Forge 1.20.1ファイル](https://www.curseforge.com/minecraft/mc-mods/create-railway-automation/files/8676039)
- [Railroad Wand / ファイル一覧](https://www.curseforge.com/minecraft/mc-mods/create-railroad-wand/files/all)
- [Railways Untold / ファイル一覧](https://www.curseforge.com/minecraft/mc-mods/railways-untold/files/all)
- [Dual Gauge Tracks / Forge 1.20.1 2.0.0ファイル](https://www.curseforge.com/minecraft/mc-mods/create-dual-gauge-tracks/files/9108299)
- [Steam 'n' Rails / ファイル一覧](https://www.curseforge.com/minecraft/mc-mods/create-steam-n-rails/files/all)
- [FastTrack / 処理性能の技術説明](https://www.curseforge.com/minecraft/mc-mods/create-fasttrack)
- [Ultimate Track Map / 配布ページ](https://www.curseforge.com/minecraft/mc-mods/ultimate-track-map)
- [Create Track Map: Fork² / Forge 1.20.1](https://www.curseforge.com/minecraft/mc-mods/create-track-map-fork2/files/all)
