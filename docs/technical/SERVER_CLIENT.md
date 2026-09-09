# Server / Client責務

## Server

- Create列車の取得
- TrainControllerと速度制約の計算
- `Train.targetSpeed`への介入
- 設定の読み取り
- 受動型走行ログの収集・ファイル出力

## Client

- HUD、GUI、速度・ノッチ・信号・Debug表示
- プレイヤー入力のUI
- Serverから受信した表示用データの保持

Clientは列車の物理制御、目標速度決定、走行ログの正とならない。走行試験の`.log`は
サーバー側だけで生成する。
