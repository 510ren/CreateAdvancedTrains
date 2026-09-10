package dev.edudio.createadvancedtrains.train.query;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Immutable, read-only Create Navigation observation in CAT public units.
 * Forward distance is present only for the AHEAD state.
 */
public record NormalizedNavigationStop(
        NavigationStopState state,
        NavigationTravelDirection direction,
        OptionalDouble forwardDistanceBlocks,
        OptionalDouble observedCreateDistanceBlocks,
        NavigationStopInvalidReason invalidReason) {

    public NormalizedNavigationStop {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(forwardDistanceBlocks, "forwardDistanceBlocks");
        Objects.requireNonNull(observedCreateDistanceBlocks, "observedCreateDistanceBlocks");
        Objects.requireNonNull(invalidReason, "invalidReason");

        if (forwardDistanceBlocks.isPresent()) {
            double distance = forwardDistanceBlocks.getAsDouble();
            if (state != NavigationStopState.AHEAD || !Double.isFinite(distance) || distance <= 0.0) {
                throw new IllegalArgumentException("Only AHEAD may expose a finite positive forward distance");
            }
        } else if (state == NavigationStopState.AHEAD) {
            throw new IllegalArgumentException("AHEAD requires a forward distance");
        }

        if (observedCreateDistanceBlocks.isPresent()
                && !Double.isFinite(observedCreateDistanceBlocks.getAsDouble())) {
            throw new IllegalArgumentException("Observed Create distance must be finite when present");
        }
        if ((state == NavigationStopState.INVALID) != (invalidReason != NavigationStopInvalidReason.NONE)) {
            throw new IllegalArgumentException("Only INVALID may have an invalid reason");
        }
    }

    public static NormalizedNavigationStop noActiveDestination() {
        return new NormalizedNavigationStop(
                NavigationStopState.NO_ACTIVE_DESTINATION,
                NavigationTravelDirection.UNKNOWN,
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                NavigationStopInvalidReason.NONE);
    }

    public static NormalizedNavigationStop ahead(
            double distanceBlocks,
            NavigationTravelDirection direction) {
        return new NormalizedNavigationStop(
                NavigationStopState.AHEAD,
                direction,
                OptionalDouble.of(distanceBlocks),
                OptionalDouble.of(distanceBlocks),
                NavigationStopInvalidReason.NONE);
    }

    public static NormalizedNavigationStop arrivalPending(NavigationTravelDirection direction) {
        return new NormalizedNavigationStop(
                NavigationStopState.AT_DESTINATION_OR_ARRIVAL_PENDING,
                direction,
                OptionalDouble.empty(),
                OptionalDouble.of(0.0),
                NavigationStopInvalidReason.NONE);
    }

    public static NormalizedNavigationStop pastDestination(
            double observedDistanceBlocks,
            NavigationTravelDirection direction) {
        return new NormalizedNavigationStop(
                NavigationStopState.PAST_DESTINATION_OR_INVALID_STATE,
                direction,
                OptionalDouble.empty(),
                OptionalDouble.of(observedDistanceBlocks),
                NavigationStopInvalidReason.NONE);
    }

    public static NormalizedNavigationStop invalid(NavigationStopInvalidReason reason) {
        if (reason == NavigationStopInvalidReason.NONE) {
            throw new IllegalArgumentException("INVALID requires an invalid reason");
        }
        return new NormalizedNavigationStop(
                NavigationStopState.INVALID,
                NavigationTravelDirection.UNKNOWN,
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                reason);
    }
}
