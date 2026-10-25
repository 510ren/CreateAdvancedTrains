# 再構築前資料 — 2026-10-25整理

旧開発の記録を内容不変で保管している。現在の決定・実装指示として使わない。再利用候補と問題箇所は[文書台帳](../../文書台帳.md)を参照する。

## 配置

移動元の `docs/` 以下の相対配置を、このフォルダ内にそのまま保持した。例えば旧 `docs/technical/CREATE_NAVIGATION.md` は、現在の `docs/old/再構築前資料/technical/CREATE_NAVIGATION.md` に対応する。

| 区分 | ファイル |
| --- | --- |
| 旧決定ログ | [DECISION_LOG.md](decisions/DECISION_LOG.md) |
| 旧技術資料 | [CREATE_6_0_8.md](technical/CREATE_6_0_8.md)、[CREATE_NAVIGATION.md](technical/CREATE_NAVIGATION.md)、[CREATE_SPEED_PHYSICS.md](technical/CREATE_SPEED_PHYSICS.md)、[CREATE_TRAIN_LIFECYCLE.md](technical/CREATE_TRAIN_LIFECYCLE.md)、[MIXIN.md](technical/MIXIN.md)、[SERVER_CLIENT.md](technical/SERVER_CLIENT.md)、[UNITS.md](technical/UNITS.md) |
| 本文がない旧文書 | [ARCHITECTURE.md](ARCHITECTURE.md)、[DEVELOPMENT_RULES.md](DEVELOPMENT_RULES.md)、[DEVELOPMENT_STATUS.md](DEVELOPMENT_STATUS.md)、[PHASE_ROADMAP.md](PHASE_ROADMAP.md)、[PROJECT.md](PROJECT.md)、[OPEN_QUESTIONS.md](research/OPEN_QUESTIONS.md)、[EXPERIMENTS.md](research/EXPERIMENTS.md)、[PERSISTENCE.md](technical/PERSISTENCE.md) |
| 旧走行ログ43件 | [09-09_1](reference/logs/09-09_1/)、[09-09_2](reference/logs/09-09_2/)、[09-09_3](reference/logs/09-09_3/)、[09-09_4](reference/logs/09-09_4/)、[09-10_1](reference/logs/09-10_1/) |

## 保存の確認

[移動記録](移動記録_2026-10-25.json)は、整理前のdocs全161ファイルの位置・SHA-256・移動先を記録している。移動した59件は元ファイルと内容が同一である。移動していないファイルも記録に含むため、`moved` フィールドで区別する。

旧本文内のパス・過去の命令・日付は改変していない。今回の索引によって現行資料との区別と現在位置を示す。過去の文書の指示に従って実装やビルドを開始しない。
