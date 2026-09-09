# Createの速度物理

## `Train.acceleration()`

Create 6.0.8では、設定上の列車加速度（blocks/s²）を400で割った値が
`Train.acceleration()`として使われる。設定値1.0 blocks/s²の場合は、
`0.0025 blocks/tick²`である。

```text
blocks/tick² × 400 = blocks/s²
```

## `approachTargetSpeed()`

標準処理は概ね、現在速度が目標より低ければ加速度を加算し、高ければ減算して、
目標速度を越えないようにする。標準Createは通常、加速と減速へ同じ
`Train.acceleration()`を用いる。

## 実測ベースライン

`docs/reference/logs/09-09_2/`のCAT無効・毎tickログでは、加速度設定1.0 blocks/s²に
対して、通常の加速・減速は約±1.0 blocks/s²だった。自動運転の停車直前には、
3.20 blocks/sから次tickで0へ到達する通常則と異なる補正が観測された。

この結果は、CATが低速・停止直前を独自に設計する必要性を示すが、停止開始距離や
補正条件の確定には位置・残距離を含む追加測定が必要である。
