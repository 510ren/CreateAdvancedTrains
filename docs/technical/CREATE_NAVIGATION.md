# Create Navigation

## CATに必要な観測基準

CATの停止位置・残距離は車体モデルの見た目から推測しない。走行試験ログでは次を基準にする。

- 列車先頭位置：Navigationが現在の進行方向に応じて使用する先頭側TravellingPoint
- 停止位置：Navigation destinationであるGlobalStationの線路上EdgePoint
- 残距離：`Navigation.distanceToDestination`の符号付きblocks値

この定義はTrainDataDebuggerの必須ログ仕様であり、詳細は
`docs/research/EXPERIMENTS.md`を参照する。

## 現在分かっている挙動

Create標準Navigationは、制動距離を基に駅接近時に`targetSpeed = 0`を要求する。
さらに駅の近傍では通常の加速度追従と異なる速度補正を行う。CATの将来の停止制御は、
この補正との競合を実測と実ソースで確認して設計する。

## 2026-09-09のログで確認できた出力

`docs/reference/logs/09-09_2/` の毎tickログでは、destinationがavailableの全sampleに、
leading TravellingPointのgraph/edge/edge内距離/ワールド座標、GlobalStation EdgePointの
graph/edge/位置、符号付き`distanceToDestinationBlocks`が出力されている。

停止直前にはleading TravellingPointとdestination EdgePointが同一edge上にあり、
tick 3487で残距離0.2368 blocksを記録した後、tick 3488でdestinationがなくなった。

## 継続調査事項

`distanceToDestination`の符号規約、destinationなしの場合のCreate内部状態、駅停止位置と
列車先頭基準の厳密な対応は、反復測定とCreate 6.0.8実ソースの確認を続ける。
