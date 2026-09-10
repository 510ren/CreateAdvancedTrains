package dev.edudio.createadvancedtrains.control;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Distinguishes a calculated CAT target from intentionally emitting no new
 * automatic acceleration or service-brake intent.
 */
public record AtoTargetSpeedDecision(
        AtoOperatingState state,
        OptionalDouble targetSpeed) {

    public AtoTargetSpeedDecision {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(targetSpeed, "targetSpeed");
    }

    public static AtoTargetSpeedDecision target(double targetSpeed) {
        if (!Double.isFinite(targetSpeed)) {
            throw new IllegalArgumentException("targetSpeed must be finite");
        }
        return new AtoTargetSpeedDecision(
                AtoOperatingState.ACTIVE,
                OptionalDouble.of(targetSpeed));
    }

    public static AtoTargetSpeedDecision suppressed(AtoOperatingState state) {
        if (state == AtoOperatingState.ACTIVE) {
            throw new IllegalArgumentException("ACTIVE must provide a target speed");
        }
        return new AtoTargetSpeedDecision(state, OptionalDouble.empty());
    }

    public boolean emitsTargetSpeedIntent() {
        return targetSpeed.isPresent();
    }
}
