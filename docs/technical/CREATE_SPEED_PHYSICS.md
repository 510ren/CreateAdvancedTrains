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

Create 6.0.8-289の実ソースで確認した正確な処理は次のとおりである。`actualTarget`は
呼出開始時点の`Train.targetSpeed`であり、`speed`と等しければ何もしない。速度が目標より
低ければ`speed + acceleration() * accelerationMod`、高ければ
`speed - acceleration() * accelerationMod`へ更新し、いずれも`actualTarget`を越えないよう
丸める。したがって、CATがこの引数を修飾する場合、修飾値は一回の
`approachTargetSpeed()`呼出に対する加減速量を直接決める。

`Navigation.tick()`は、通常の自動運転でそのtickの残距離・カーブ・Create標準制動距離から
`Train.targetSpeed`を決め、その直後に`approachTargetSpeed(1)`を呼ぶ。停止距離内では目標を
0、それ以外では最高速度またはカーブ速度にする。手動運転側の
`CarriageContraptionEntity`も、プレイヤー入力から`Train.targetSpeed`を設定して
`approachTargetSpeed()`を呼ぶ。よって`targetSpeed`はCreateの各制御文脈がその都度生成する
入力であり、CATが過去の値を保持して別tickへ再代入する所有物ではない。

Navigationの通常停止判定は、`brakingDistance = speed² / (2 * acceleration())`である。この
`acceleration()`はCreate標準の基準加速度であり、CATのB7による実効-4.0 blocks/s²を知らない。
したがって基準1.0 blocks/s²の列車へB7だけを適用すると、NavigationはB7で速く減速した直後に
「残距離に対して遅すぎる」と再判定し、再び最高速度をtargetSpeedとして出す。このときCATが
通常の非介入規則を守れば、列車は+1.0 blocks/s²で加速する。次tickには速度が増して標準制動距離が
残距離を上回り、Navigationが0を出してB7が再び適用される。これはNavigationの標準制動モデルと
強い外部制動を組み合わせた際のフィードバック往復である。

Navigationには、停止位置の直前で`Train.speed`を直接補正する標準処理もある。これは
`approachTargetSpeed()`による通常の加減速とは別経路であり、CATの通常制御がこれを置換・
包括的にフックしてはならない。

## 実測ベースライン

`docs/reference/logs/09-09_3/`のCAT無効・毎tickログでは、加速度設定1.0 blocks/s²に
対して、通常の加速・減速は約±1.0 blocks/s²だった。自動運転の停車直前には、
3.20 blocks/sから次tickで0へ到達する通常則と異なる補正が観測された。

この結果は、CATが低速・停止直前を独自に設計する必要性を示す。反復ベースラインは
`docs/research/EXPERIMENTS.md`に集計済みだが、160 blocks/sを対象に最終Profileを決めるには
実際に高速度域へ到達するPhase 5A追加試験が必要である。
