package dev.edudio.createadvancedtrains.control.braking;

/**
 * Pure CAT-unit input for one Phase 5B braking-curve calculation.
 *
 * <p>The remaining distance must already have been normalized as an AHEAD
 * Navigation distance. Direction is intentionally not encoded in its sign.</p>
 * @param forwardRemainingDistanceBlocks 仕様書に個別説明がないため、{@code forwardRemainingDistanceBlocks}が示す距離または位置量。単位はblocks。
 * @param currentSpeedBlocksPerSecond 現在速度の大きさ。単位はblocks/s。
 * @param transitionStartAccelerationBlocksPerSecondSquared 仕様書に個別説明がないため、{@code transitionStartAccelerationBlocksPerSecondSquared}が示す加速度。単位はblocks/s^2。
 * @param baseAccelerationBlocksPerSecondSquared Create基本加速度の大きさ。単位はblocks/s^2。
 * @param speedCeilingBlocksPerSecond 仕様書に個別説明がないため、{@code speedCeilingBlocksPerSecond}が示す速度。単位はblocks/s。
 */
public record BrakingCurveInput(
        double forwardRemainingDistanceBlocks,
        double currentSpeedBlocksPerSecond,
        double transitionStartAccelerationBlocksPerSecondSquared,
        double baseAccelerationBlocksPerSecondSquared,
        double speedCeilingBlocksPerSecond) {
}
