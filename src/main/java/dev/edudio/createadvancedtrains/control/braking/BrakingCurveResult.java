package dev.edudio.createadvancedtrains.control.braking;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Pure calculation output. It contains no target-speed, notch, EB, or world
 * mutation command.
 */
public record BrakingCurveResult(
        BrakingCurveStatus status,
        OptionalDouble maximumPermittedSpeedBlocksPerSecond,
        OptionalDouble predictedStoppingDistanceBlocks,
        OptionalDouble usableDistanceBlocks,
        OptionalDouble predictedOvershootBlocks,
        BrakingCurveFailure failure) {

    public BrakingCurveResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(maximumPermittedSpeedBlocksPerSecond, "maximumPermittedSpeedBlocksPerSecond");
        Objects.requireNonNull(predictedStoppingDistanceBlocks, "predictedStoppingDistanceBlocks");
        Objects.requireNonNull(usableDistanceBlocks, "usableDistanceBlocks");
        Objects.requireNonNull(predictedOvershootBlocks, "predictedOvershootBlocks");
        Objects.requireNonNull(failure, "failure");
    }

    public static BrakingCurveResult noForwardStopTarget() {
        return new BrakingCurveResult(
                BrakingCurveStatus.NO_FORWARD_STOP_TARGET,
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                BrakingCurveFailure.NONE);
    }

    public static BrakingCurveResult invalid(BrakingCurveFailure failure) {
        if (failure == BrakingCurveFailure.NONE) {
            throw new IllegalArgumentException("An invalid result needs a failure reason");
        }
        BrakingCurveStatus status = failure == BrakingCurveFailure.ITERATION_LIMIT_REACHED
                ? BrakingCurveStatus.ITERATION_LIMIT_REACHED
                : BrakingCurveStatus.INVALID_INPUT;
        return new BrakingCurveResult(
                status,
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                failure);
    }

    public boolean isCalculationError() {
        return status.isCalculationError();
    }
}
