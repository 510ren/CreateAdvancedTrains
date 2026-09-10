package dev.edudio.createadvancedtrains.train;

import java.util.Objects;
import java.util.UUID;

import dev.edudio.createadvancedtrains.train.query.NormalizedNavigationStop;

/**
 * Per-train owner of shared, read-only operational flag derivation.
 */
public final class FlagDeterminer {

    private final UUID trainId;
    private TrainOperationalFlags currentFlags;

    public FlagDeterminer(UUID trainId) {
        this.trainId = Objects.requireNonNull(trainId, "trainId");
        currentFlags = TrainOperationalFlags.fromNavigationState(
                dev.edudio.createadvancedtrains.train.query.NavigationStopState.NO_ACTIVE_DESTINATION);
    }

    public TrainOperationalFlags update(
            UUID observedTrainId,
            NormalizedNavigationStop navigationStop) {
        if (!trainId.equals(Objects.requireNonNull(observedTrainId, "observedTrainId"))) {
            throw new IllegalArgumentException("FlagDeterminer cannot observe a different train UUID");
        }
        Objects.requireNonNull(navigationStop, "navigationStop");
        currentFlags = TrainOperationalFlags.fromNavigationState(navigationStop.state());
        return currentFlags;
    }

    public TrainOperationalFlags currentFlags() {
        return currentFlags;
    }
}
