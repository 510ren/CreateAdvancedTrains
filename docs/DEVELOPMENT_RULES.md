# 開発ルール

`AGENTS.md` がエージェントの役割分担と最優先の作業規約である。本書はCAT固有の
開発ルールを要約する。

## 必須ルール

1. Minecraft 1.20.1、Forge 47.4.10、Create 6.0.8-289、Java 17を固定する。
2. Create API・内部処理・tick順序を推測で実装しない。必要な箇所は実ソースで確認する。
3. 通常のCAT制御で `Train.speed` を直接代入しない。
4. `Train.targetSpeed` への介入は最小限にし、`approachTargetSpeed()`との順序を確認する。
5. CAT公開側はblocks/s系、Create境界だけblocks/tickを使用する。
6. Serverの制御とClientの表示・入力を混在させない。
7. 既存機能を壊す大規模リファクタと、新機能実装を同一タスクに混在させない。
8. Java実装はGPT-5.6 Sol、Markdownの継続的な記録はGPT-5.6 Terraの担当とする。

## Phase 5のゲート

BrakingCurveなどの制御Java実装は、制動性能、応答時間、速度依存テーブル、制動距離、
逆算、安全余裕、低速停止、EBの仕様が十分に定義され、ユーザーが開始を承認するまで
禁止する。受動的な計測仕様は `docs/research/EXPERIMENTS.md` を正とする。

## 実装依頼の最小要件

SolへJava作業を依頼する前に、目的、対象Phase、確定仕様、未決定事項、参照する
Create 6.0.8の挙動、単位、受入条件、保護対象をMarkdownへ記録する。Solは不整合や
仕様不足を見つけた場合、実装で補完せず根拠と選択肢を返す。
