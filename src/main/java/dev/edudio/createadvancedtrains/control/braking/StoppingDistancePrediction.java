package dev.edudio.createadvancedtrains.control.braking;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Result of the response-aware B7 forward integration.
 */
public record StoppingDistancePrediction(
        OptionalDouble stoppingDistanceBlocks,
        int simulatedTicks,
        BrakingCurveFailure failure) {

    public StoppingDistancePrediction {
        Objects.requireNonNull(stoppingDistanceBlocks, "stoppingDistanceBlocks");
        Objects.requireNonNull(failure, "failure");
        if (simulatedTicks < 0) {
            throw new IllegalArgumentException("simulatedTicks must not be negative");
        }
        if (stoppingDistanceBlocks.isPresent() != (failure == BrakingCurveFailure.NONE)) {
            throw new IllegalArgumentException("A successful prediction must have a distance and no failure");
        }
    }

    public static StoppingDistancePrediction success(double distanceBlocks, int simulatedTicks) {
        return new StoppingDistancePrediction(
                OptionalDouble.of(distanceBlocks),
                simulatedTicks,
                BrakingCurveFailure.NONE);
    }

    public static StoppingDistancePrediction failure(BrakingCurveFailure failure, int simulatedTicks) {
        if (failure == BrakingCurveFailure.NONE) {
            throw new IllegalArgumentException("A failed prediction needs a failure reason");
        }
        return new StoppingDistancePrediction(OptionalDouble.empty(), simulatedTicks, failure);
    }

    public boolean isSuccessful() {
        return failure == BrakingCurveFailure.NONE;
    }
}
