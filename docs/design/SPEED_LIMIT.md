# Speed Limit

## 実装済みの機能

`SpeedLimitController`は、`SpeedLimitSource`ごとの制限速度を`EnumMap`で保持する。
有効制限の最小値を採用し、Createから渡されたtargetSpeedの符号を保ったまま制限する。

公開APIの制限速度はblocks/s、Create境界の入力・出力はblocks/tickである。

```text
LINE / TEMPORARY / SIGNAL / STATION / ATC / ATS / TEST
                       ↓
               minimum active limit
                       ↓
              Create targetSpeed
```

## 検証用コマンド

```text
/create_advanced_trains speed_limit test set <value>
```

`<value>`はblocks/sであり、全登録済みControllerへ `SpeedLimitSource.TEST` として配布する。

## 制約

- 制限値は有限かつ0以上でなければならない。
- 制限値の削除・未設定は正の無限大として扱う。
- `features.speed_limit = false` の場合、ATOはSpeedLimitControllerを通さない。

## 要照合

現行の `TrainController` は生成時に`TEST = 20.0 blocks/s`を登録している。これは通常の
試験上限なのか一時コードなのかを、次の速度制限作業前に確認する必要がある。
