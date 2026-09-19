package dev.edudio.createadvancedtrains.debug.notchtest;

/**
 * 仕様書に独立した型契約がないため、現在の利用箇所から推定した不変データを保持します。
 * @param storedEffectiveAccelerationBlocksPerSecondSquared 仕様書に個別説明がないため、{@code storedEffectiveAccelerationBlocksPerSecondSquared}が示す加速度。単位はblocks/s^2。
 * @param activeEffectiveAccelerationBlocksPerSecondSquared 仕様書に個別説明がないため、{@code activeEffectiveAccelerationBlocksPerSecondSquared}が示す加速度。単位はblocks/s^2。
 * @param transitionElapsedTicks 仕様書に個別説明がないため、{@code transitionElapsedTicks}が示すtick数またはserver tick値。
 * @param transitionTimebase 仕様書に個別説明がないため、現在の処理内容から推定した、{@code transitionTimebase}として使用される入力値。
 */
record NotchTestResponseState(
        double storedEffectiveAccelerationBlocksPerSecondSquared,
        Double activeEffectiveAccelerationBlocksPerSecondSquared,
        int transitionElapsedTicks,
        String transitionTimebase) {
}
