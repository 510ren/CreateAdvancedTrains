package dev.edudio.createadvancedtrains.train.query;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Immutable result of a read-only stop-target distance query, in blocks.
 */
public record StopTargetDistance(
        OptionalDouble distanceBlocks,
        Source source,
        UnavailableReason unavailableReason) {

    public StopTargetDistance {
        Objects.requireNonNull(distanceBlocks, "distanceBlocks");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(unavailableReason, "unavailableReason");

        if (distanceBlocks.isPresent()) {
            if (!Double.isFinite(distanceBlocks.getAsDouble())) {
                throw new IllegalArgumentException("Available distance must be finite");
            }
            if (source == Source.UNAVAILABLE || unavailableReason != UnavailableReason.NONE) {
                throw new IllegalArgumentException("Available distance cannot have an unavailable source or reason");
            }
        } else if (source != Source.UNAVAILABLE || unavailableReason == UnavailableReason.NONE) {
            throw new IllegalArgumentException("Unavailable distance must have an unavailable reason");
        }
    }

    public static StopTargetDistance available(double distanceBlocks, Source source) {
        return new StopTargetDistance(OptionalDouble.of(distanceBlocks), source, UnavailableReason.NONE);
    }

    public static StopTargetDistance unavailable(UnavailableReason reason) {
        return new StopTargetDistance(OptionalDouble.empty(), Source.UNAVAILABLE, reason);
    }

    public boolean isAvailable() {
        return distanceBlocks.isPresent();
    }

    public enum Source {
        NAVIGATION,
        MANUAL_CREATE_EQUIVALENT,
        UNAVAILABLE
    }

    public enum UnavailableReason {
        NONE,
        NAVIGATION_UNAVAILABLE,
        NAVIGATION_DISTANCE_NOT_FINITE,
        GRAPH_UNAVAILABLE,
        CARRIAGES_UNAVAILABLE,
        MANUAL_DIRECTION_UNAVAILABLE,
        NO_APPROACHABLE_STATION,
        STATION_DISTANCE_NOT_FOUND
    }
}
