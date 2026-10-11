# CAT 要求工学・AI分業・V&V 運用方針

> 目的: この文書は、Create Advanced Trains (CAT)
> の再構築における要件整理、要求 (REQ)、仕様
> (SPEC)、設計、V&V、実装の全体フローと、各AIの役割を共有するための運用文書である。
>
> この文書自体は CAT
> の製品要求や技術仕様を定義しない。プロジェクトを「どのように要求化・仕様化・レビュー・実装するか」を定義する。

## 1. 現在地

CAT
は既存実装をそのまま改修するのではなく、一から再構築する方針で要件整理を進めている。

現在の上流資料は概ね次の段階にある。

1.  ユーザー自身が作成した「やりたいこと」の箇条書き
2.  `整理2.md`
    -   決まったことの整理
    -   不足事項の抽出
    -   未決事項の抽出
    -   関連項目の整理
3.  `整理3.md`
    -   `整理2.md` を基にさらに詳細化
    -   未決事項、競合、曖昧性を可能な限り解消
    -   正式な要求へ移行する前の探索・合意形成資料

`整理2.md` と `整理3.md` は重要な source artifact であるが、最終的な
Requirements / Specification の正本にはしない。

`整理3.md`
で重大な未決事項・競合・曖昧性を十分に解消した時点を、原則として「探索フェーズ終了」とする。

------------------------------------------------------------------------

## 2. 基本方針

本プロジェクトでは、要求工学の考え方として ISO/IEC/IEEE 29148
を参考にする。ただし、CAT
は安全認証を目的とする実システムではないため、規格への形式的な完全準拠を目的としない。

採用する中心原則は以下である。

-   要求を一意に識別できること
-   1つの要求に複数の独立した要求を混在させないこと
-   曖昧性を可能な限り排除すること
-   要求の根拠・由来を追跡できること
-   要求と仕様を分離すること
-   仕様と実装設計を分離すること
-   要求が検証可能であること
-   上流要求からテストまで追跡可能にすること
-   Verification と Validation を区別すること
-   AIの出力をそのまま正本にせず、レビューとユーザー判断を通すこと

プロジェクトの正本は、最終的に Git 管理された `docs/`
以下の正式文書とする。

------------------------------------------------------------------------

## 3. 情報レイヤー

CATでは以下のレイヤーを明確に分離する。

### 3.1 Source / Exploration

例: - やりたいことリスト - `整理2.md` - `整理3.md` - 調査メモ -
AIとの検討結果

目的: - アイデア探索 - 選択肢比較 - 未決事項の発見 -
ユーザーとの合意形成

これらは「なぜその要求になったか」を追跡する資料として保持する。

### 3.2 REQ - Requirement

REQは「CATが何を満たさなければならないか」を定義する。

REQでは原則としてJavaクラス構造、Mixin実装方法、具体的アルゴリズム等の実装方法を規定しない。

例:

``` text
REQ-TASC-002

通常停止条件下において、TASCによる停止位置誤差は、
目標停止位置から定義された許容範囲内でなければならない。
```

REQに持たせる情報の基本形:

``` text
ID
Title
Requirement
Type
Priority
Rationale
Source
Dependencies
Related Requirements
Verification Method
Status
Notes (必要な場合のみ)
```

要求本文は可能な限り atomic、明確、必要、実現可能、検証可能であること。

### 3.3 SPEC - Specification

SPECは、REQを満たすために「CATが具体的にどのように振る舞うか」を定義する。

SPECで扱う例: - 状態 - 状態遷移 - 入出力 - 制御則 - 数式 - 定数 - 単位 -
タイミング - 境界条件 - 異常時動作 - 優先順位 -
複数機能が同時に作用した場合の競合解決 - client/server間の振る舞い -
永続化対象 - Createとの外部インターフェース上の振る舞い

例:

``` text
REQ-BRK-xxx
    ↓
SPEC-BRK-xxx
    - ブレーキ状態
    - 適用条件
    - 応答遅延
    - 減速度モデル
    - 解放条件
    - 他制御との優先順位
```

### 3.4 ARCH / DESIGN

ここではSPECをソフトウェアとしてどう構成するかを決める。

例: - module boundary - class responsibility - controller構成 - data
ownership - lifecycle - Mixin point - networking architecture -
persistence architecture - dependency direction

製品としての挙動を決める判断はREQ/SPEC側で行い、単なる実装構造はARCH/DESIGN側で扱う。

### 3.5 CODE / TEST

確定したREQ/SPEC/ARCHに基づいてCodexが実装する。

------------------------------------------------------------------------

## 4. Traceability

可能な範囲で以下の追跡関係を維持する。

``` text
SOURCE
  ↓
REQ
  ↓
SPEC
  ↓
ARCH / DESIGN
  ↓
CODE
  ↓
TEST
```

例:

``` text
整理3: ATO停止制御の要求
        ↓
REQ-TASC-004
        ↓
SPEC-TASC-012
        ↓
DESIGN-TASC-003
        ↓
TascController.java
        ↓
TEST-TASC-021
```

目的: - コードの存在理由を上流要求まで辿れる -
REQがSPECに反映されているか確認できる -
SPECに対応するテストがあるか確認できる -
仕様変更時の影響範囲を特定しやすくする -
不要になったコードや仕様を発見しやすくする

------------------------------------------------------------------------

## 5. 全体フロー

### Phase A - Exploration / 要望整理

担当: - ユーザー - Astra Medium

入力: - やりたいことリスト - 既存の知識 - 必要な技術調査

成果物: - `整理2.md` - `整理3.md`

終了条件: - 重大な未決事項が列挙されている -
既知の競合が解消または明示されている - 用語の意味が概ね統一されている -
「何を作りたいのか」が十分明確になっている

### Phase B - Requirements Formalization

主担当: - Astra High / Pro相当の高推論設定 - ユーザーが最終判断

入力: - `整理3.md` - 必要な source artifacts

処理: - source artifactから正式な要求を抽出 - atomicなREQへ分解 -
ID付与 - rationale/source/dependency/verification method等を設定 -
functional / non-functional / constraint等を分類

成果物: - Requirements draft

この段階では、source artifactの意味を勝手に変更しない。

### Phase C - Requirements Independent Review

REQ draftを独立レビューする。

#### Opus - Requirements Quality Reviewer

重点: - 曖昧性 - 内部矛盾 - REQ間矛盾 - 欠落 - 暗黙の前提 - 未定義用語 -
境界条件 - 異常系 - 検証不能要求 - 定量化不足 - 重複 - 過剰仕様 -
要求と設計の混同

原則: - 正本を直接書き換えない - 問題と理由を報告する -
推奨修正方向は提示可能

#### Fable - Technical / Implementability Reviewer

重点: - 実装者が複数解釈できる要求 - Forge/Create API上の実現性リスク -
状態・ライフサイクル不足 - client/server責務不足 -
persistence/networking上の不足 - 実装時に追加判断が必要になる箇所 -
技術的境界条件 - テスト困難性 - 既存Createとの統合リスク

原則: - 要求の意味を勝手に変更しない -
技術的な問題を「要求上の不足」として報告する

### Phase D - Review Integration

担当: - Astra High / Pro - ユーザー

Opus/Fableの指摘を次のように分類する。

``` text
ACCEPT
PARTIAL
REJECT
DUPLICATE
NEEDS_RESEARCH
NEEDS_USER_DECISION
```

`NEEDS_USER_DECISION` はユーザーに提示し、ユーザーが最終決定する。

レビューAIの意見が互いに異なる場合、単純な多数決は行わない。根拠、要求の意図、技術的事実を比較して判断する。

成果物: - Approved Requirements

### Phase E - Specification

担当: - Astra High / Pro - ユーザー

Approved REQを満たすSPECを作成する。

各SPECは対応するREQを明示する。

SPEC作成時には、少なくとも必要に応じて以下を検討する。

-   normal behavior
-   state
-   transition
-   input/output
-   units
-   timing
-   mathematical model
-   thresholds
-   priority
-   boundary conditions
-   exceptional behavior
-   failure behavior
-   conflicting control resolution
-   external interface behavior
-   client/server behavior
-   persistence
-   configuration interaction

REQで未決の製品判断が発見された場合、SPEC側で勝手に決めずREQ側へ戻す。

### Phase F - Specification V&V

Opus: - REQ → SPECの整合性 - REQ coverage - SPEC内部矛盾 -
要求の意味が変わっていないか - 未定義動作 - 境界・異常系

Fable: - SPECだけを実装者へ渡して実装可能か -
実装時に追加の製品判断が必要にならないか -
Create/Forgeとの技術的成立性 - 状態遷移・interface・lifecycleの不足 -
テスト可能性

Astra: - 両レビューを統合 - 全体システムとしての整合性確認

ユーザー: - 製品仕様に関する最終判断

### Phase G - Architecture / Design

REQ/SPEC確定後に実施する。

Astraはシステム全体のアーキテクチャ整理に利用可能。

Codexには実リポジトリを調査させ、実装設計案を作らせてもよい。

Codexが判断してよい例: - class decomposition - interface design - helper
abstraction - package layout - internal data structure - test
implementation - リファクタリング方法

Codexが勝手に判断してはいけない例: - ATOの製品上の挙動 -
ブレーキ性能値 - 停止許容誤差 - ATC適用条件 -
手動運転時にどの安全機能を強制するか - ユーザーから見える仕様変更

後者が不明なら実装を止め、仕様上の不明点として上流へ返す。

### Phase H - Implementation

主実装者: - Codex

Codexは正式な以下の文書を正として使用する。

``` text
AGENTS.md
docs/requirements/
docs/specifications/
docs/architecture/
docs/interfaces/
docs/decisions/
docs/development/
```

原則として `整理2.md` / `整理3.md` を実装上の正本として使用しない。

旧コードも現行仕様の正本ではない。必要な場合のみ技術参考として扱う。

### Phase I - Code Review / Verification

推奨: - Codex A: 実装 - Codex B: 独立レビュー - 必要に応じてFable:
重要変更の追加技術レビュー

レビュー対象: - SPEC compliance - REQ coverage - logic bug - edge case -
client/server - concurrency / lifecycle - Forge/Create API misuse -
regression - test coverage - unnecessary complexity - architecture
violation

実装AI自身の自己レビューだけに依存しない。

------------------------------------------------------------------------

## 6. Verification と Validation

本プロジェクトでは両者を区別する。

### 6.1 Verification

問い:

> 「成果物を正しく作っているか？」

例: - REQは明確か - REQ同士に矛盾がないか - REQは検証可能か -
SPECはREQを正しく満たしているか - SPECに未定義動作がないか -
CODEはSPEC通りか - TESTは対象REQ/SPECを検証しているか

Verificationは各工程で継続的に行う。

### 6.2 Validation

問い:

> 「そもそも正しいものを作ろうとしているか？」

例: - このATO挙動はユーザーが本当に欲しいものか -
この制動モデルはCATの目的に適しているか -
この操作体系は意図したプレイ体験になるか -
要求を全部満たしても、当初の目的から外れていないか

Validationの最終判断者はユーザーである。

AIが技術的に美しい案を提示しても、ユーザーの意図と異なるなら採用しない。

------------------------------------------------------------------------

## 7. AIの役割

### User - Product Owner / Final Decision Maker

責務: - CATで実現したい内容の決定 - trade-offの最終判断 -
NEEDS_USER_DECISIONへの回答 - Validation - 要求・仕様の承認

### Astra Medium - Requirements Exploration Partner

主用途: - 対話による要望整理 - `整理2.md` / `整理3.md` - 選択肢比較 -
未決事項の発見 - 要求の意図確認 - 日常的な要求整理

重要: - 未決事項を推測で確定しない - ユーザーの決定とAI提案を区別する

### Astra High / Pro - Requirements / Systems Engineer

主用途: - 正式REQへの形式化 - SPEC作成 - 複雑なシステム間整合性 -
レビュー結果の統合 - 重要な設計判断の分析 - traceability確認

### Opus - Independent Requirements Reviewer

主用途: - 要求品質 - 論理的整合性 - 曖昧性 - 欠落 - V&V -
長文仕様全体の独立監査

Opusは正本の所有者ではない。

### Fable - Independent Technical Reviewer

主用途: - 実装可能性 - 技術的抜け - コード/アーキテクチャ視点 -
API統合リスク - 実装時の曖昧性 - 重要なコード変更の追加レビュー

Fableも正本の所有者ではない。

### Codex - Software Engineer

主用途: - repository investigation - implementation plan -
architecture/designの具体化 - coding - tests - refactoring - debugging -
code review

Codexはコード実装の主担当とする。

ただし製品仕様の決定権は持たない。

------------------------------------------------------------------------

## 8. AI間で意見が食い違った場合

優先順位を「モデルの強さ」だけで決めない。

判断順序:

1.  ユーザーが明示的に決めた要求
2.  Approved REQ
3.  Approved SPEC
4.  Approved Architecture / ADR
5.  検証された技術的事実
6.  AIによる推奨

AIが正式文書と矛盾する案を提示した場合、原則として正式文書を優先する。

ただし正式文書自体の欠陥が疑われる場合は、黙ってコード側で修正せず、上流へ問題を返す。

------------------------------------------------------------------------

## 9. Change Control

確定済みREQ/SPECを変更する場合:

``` text
変更要求
  ↓
影響するREQ特定
  ↓
関連SPEC特定
  ↓
ARCH/CODE/TESTへの影響分析
  ↓
必要なV&V
  ↓
ユーザー承認
  ↓
文書更新
  ↓
実装修正
```

コード都合だけでREQ/SPECを書き換えない。

技術的制約により要求が成立しない場合は、要求変更として扱う。

------------------------------------------------------------------------

## 10. 推奨ディレクトリ概念

最終的な構成は別途プロジェクト全体の文書構成と整合させるが、概念的には以下を想定する。

``` text
docs/
├─ _working/
│  ├─ 整理2.md
│  ├─ 整理3.md
│  └─ research/
│
├─ requirements/
│  ├─ system-requirements.md
│  ├─ functional/
│  ├─ non-functional.md
│  ├─ constraints.md
│  └─ traceability.md
│
├─ specifications/
│  ├─ ato.md
│  ├─ tasc.md
│  ├─ atc.md
│  ├─ braking.md
│  ├─ notch.md
│  ├─ speed-limit.md
│  ├─ signals.md
│  └─ ...
│
├─ architecture/
├─ interfaces/
├─ decisions/
└─ development/
```

巨大な単一文書に全仕様を集約することを避ける。

ただし、分割によって相互関係が見えなくならないよう、IDとtraceabilityを使用する。

------------------------------------------------------------------------

## 11. Definition of Ready - 実装へ渡せる条件

ある機能をCodexへ実装依頼する前に、原則として以下を満たす。

-   関連REQがApproved
-   関連SPECがApproved
-   未解決の重大な競合がない
-   必要な境界条件が定義済み
-   必要な異常系が定義済み
-   単位・定数・タイミングが必要な場合は定義済み
-   他機能との優先順位が必要な場合は定義済み
-   Create/Forgeとの技術的不確実性が許容範囲まで調査済み
-   Verification methodが定義されている
-   実装時に新しい製品判断を要求しない状態になっている

満たさない場合、Codexは推測して実装せず上流へ返す。

------------------------------------------------------------------------

## 12. Definition of Done - 機能完了条件

最低限:

-   対応REQが満たされている
-   対応SPECが実装されている
-   必要なテストが存在し成功する
-   traceabilityが更新されている
-   既存REQ/SPECへの回帰がない
-   コードレビューを通過している
-   必要に応じて実環境でValidationされている
-   実装によって新しい暗黙仕様が発生していない

------------------------------------------------------------------------

## 13. Astraへの運用指示

この文書を読んだAstraは、以後の要件整理で以下を守る。

1.  現在の作業が SOURCE / REQ / SPEC / ARCH / CODE
    のどの層かを混同しない。
2.  `整理3.md` の完成前は、必要以上に実装設計へ進まない。
3.  正式REQ作成時は、ユーザーの意図を勝手に補完しない。
4.  REQとSPECを分離する。
5.  SPEC作成中に製品上の未決事項を発見したらREQへ戻す。
6.  AIレビュー結果を無条件で採用しない。
7.  Opus/Fableのレビュー結果を分類し、必要なものだけ統合する。
8.  `NEEDS_USER_DECISION` はユーザーへ確認する。
9.  V&Vとtraceabilityを後付けにせず、要求作成時から考慮する。
10. 実装可能性のために製品要求を勝手に変更しない。
11. 正式文書と旧コードが矛盾する場合、旧コードを仕様の根拠としない。
12. Codexへ渡す前にDefinition of Readyを確認する。
13. ユーザーが承認していない内容を「確定」と表現しない。

------------------------------------------------------------------------

## 14. この文書の位置づけ

この文書はプロジェクトの「要求・仕様策定プロセスおよびAI分業方針」を定義する。

優先関係:

``` text
ユーザーの明示的な決定
        ↓
正式に承認されたプロジェクト方針
        ↓
Approved REQ
        ↓
Approved SPEC
        ↓
Approved ARCH / ADR
        ↓
AIの提案・レビュー
        ↓
旧実装
```

旧実装は技術調査の資料にはできるが、再構築後CATの要求・仕様を自動的に規定するものではない。

この運用の目的は、AIを多数使うこと自体ではない。

目的は、 - 要求の意図を保持する - 矛盾・欠落を早期発見する -
正本を一つに保つ - 実装AIによる推測を減らす -
要求からコード・テストまで追跡可能にする -
CATを長期的に変更可能な状態にする

ことである。
