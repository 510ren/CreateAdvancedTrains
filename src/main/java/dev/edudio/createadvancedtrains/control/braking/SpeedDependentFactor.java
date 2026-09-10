package dev.edudio.createadvancedtrains.control.braking;

/**
 * Replaceable speed-dependent scalar used by the braking model.
 */
@FunctionalInterface
public interface SpeedDependentFactor {

    double valueAt(double speedBlocksPerSecond);
}
