package dev.edudio.createadvancedtrains.train;

import java.util.Objects;

import dev.edudio.createadvancedtrains.train.query.NavigationStopState;

/**
 * Immutable shared flags derived centrally from Create observations.
 */
public record TrainOperationalFlags(
        boolean atDestinationOrArrivalPending,
        NavigationStopState navigationStopState) {

    public TrainOperationalFlags {
        Objects.requireNonNull(navigationStopState, "navigationStopState");
        boolean expectedArrivalFlag = navigationStopState
                == NavigationStopState.AT_DESTINATION_OR_ARRIVAL_PENDING;
        if (atDestinationOrArrivalPending != expectedArrivalFlag) {
            throw new IllegalArgumentException("Arrival flag must be derived from Navigation state");
        }
    }

    public static TrainOperationalFlags fromNavigationState(NavigationStopState state) {
        return new TrainOperationalFlags(
                state == NavigationStopState.AT_DESTINATION_OR_ARRIVAL_PENDING,
                state);
    }
}
