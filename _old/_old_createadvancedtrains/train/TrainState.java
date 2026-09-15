package dev.edudio.createadvancedtrains.train;

import dev.edudio.createadvancedtrains.control.AtoOperatingState;
import dev.edudio.createadvancedtrains.train.error.CatCalculationErrorSnapshot;

/**
 * 仕様書に独立した型契約がないため、現在の利用箇所から推定した不変データを保持します。
 * @param speed 仕様書に個別説明がないため、{@code speed}が示す速度値。単位は呼出元の境界定義に従います。
 * @param createTargetSpeed Createが設定した目標速度。
 * @param atoTargetSpeed 仕様書に個別説明がないため、{@code atoTargetSpeed}が示す速度値。単位は呼出元の境界定義に従います。
 * @param operationalFlags 仕様書に個別説明がないため、現在の処理内容から推定した、{@code operationalFlags}として使用される入力値。
 * @param atoOperatingState 仕様書に個別説明がないため、{@code atoOperatingState}が示す現在または判定後の状態。
 * @param calculationError 仕様書に個別説明がないため、現在の処理内容から推定した、{@code calculationError}として使用される入力値。
 */
public record TrainState(
        double speed,
        double createTargetSpeed,
        double atoTargetSpeed,
        TrainOperationalFlags operationalFlags,
        AtoOperatingState atoOperatingState,
        CatCalculationErrorSnapshot calculationError) {
}
