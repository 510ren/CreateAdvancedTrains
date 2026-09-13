package dev.edudio.createadvancedtrains.control.braking;

/**
 * Replaceable speed-dependent scalar used by the braking model.
 */
@FunctionalInterface
public interface SpeedDependentFactor {

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code valueAt}としてまとめられている処理を実行します。
     * @param speedBlocksPerSecond 仕様書に個別説明がないため、{@code speedBlocksPerSecond}が示す速度。単位はblocks/s。
     * @return 処理または計算によって得られた数値。
     */
    double valueAt(double speedBlocksPerSecond);
}
