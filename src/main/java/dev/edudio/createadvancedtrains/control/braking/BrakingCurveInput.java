package dev.edudio.createadvancedtrains.control.braking;

/**
 * Pure CAT-unit input for one Phase 5B braking-curve calculation.
 *
 * <p>The remaining distance must already have been normalized as an AHEAD
 * Navigation distance. Direction is intentionally not encoded in its sign.</p>
 */
public record BrakingCurveInput(
        double forwardRemainingDistanceBlocks,
        double currentSpeedBlocksPerSecond,
        double transitionStartAccelerationBlocksPerSecondSquared,
        double baseAccelerationBlocksPerSecondSquared,
        double speedCeilingBlocksPerSecond) {
}
