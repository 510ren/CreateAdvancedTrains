package dev.edudio.createadvancedtrains.train.query;

/**
 * CAT-safe normalization of Create Navigation destination state.
 */
public enum NavigationStopState {
    NO_ACTIVE_DESTINATION,
    AHEAD,
    AT_DESTINATION_OR_ARRIVAL_PENDING,
    PAST_DESTINATION_OR_INVALID_STATE,
    INVALID
}
