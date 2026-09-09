# 未解決事項

## 最優先：Phase 5の制動仕様

| 項目 | 必要な決定・根拠 |
| --- | --- |
| B1〜B7 | `docs/design/NOTCH.md`のPhase 5A暫定倍率・試験手順で実測し、その結果からB7曲線、最終倍率、個別補正の有無を決める。 |
| EB | 性能、速度依存表、常用制動からの切替条件、復旧条件 |
| 制動距離 | 応答時間・速度依存減速度・安全余裕を含む計算方法 |
| 逆算 | 残距離から最大許容速度を求める数値探索方法 |
| 安全余裕 | 距離余裕・減速度安全係数の速度別試験値 |

## Create Navigation調査

- 進行方向に対応するleading TravellingPointの取得経路
- GlobalStation destinationと線路上EdgePointの取得経路
- `Navigation.distanceToDestination`の符号・単位・destinationなし時の値
- Create標準の駅近傍速度補正の正確な条件とtick順序

## 計測

- 各駅間のCreate標準走行を、位置・残距離を含めて毎tick記録する。
- 同一条件で最低3回測定し、速度、加速度、目標速度変更点、停止誤差を表へ集計する。
- 80/160 blocks/sへ実際に到達する区間と、カーブ制限区間を分けて測定する。

## 現行実装との照合

- `SpeedLimitSource.TEST = 20.0 blocks/s`の初期登録が意図した挙動か。
- LevelTick Debug処理がController更新・ATO適用を行う現在の設計が、Mixin基準の介入方針と
  両立するか。
- CAT Controller Block未搭載列車をController対象から除外する正確な方法。
