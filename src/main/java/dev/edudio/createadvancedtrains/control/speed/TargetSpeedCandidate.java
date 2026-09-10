package dev.edudio.createadvancedtrains.control.speed;

import java.util.Objects;
import java.util.OptionalDouble;

/** One immutable blocks/s diagnostic candidate considered by the resolver. */
public record TargetSpeedCandidate(
        TargetSpeedCandidateSource source,
        OptionalDouble magnitudeBlocksPerSecond,
        boolean included,
        String diagnostic) {

    public TargetSpeedCandidate {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(magnitudeBlocksPerSecond, "magnitudeBlocksPerSecond");
        Objects.requireNonNull(diagnostic, "diagnostic");
        if (magnitudeBlocksPerSecond.isPresent()) {
            double value = magnitudeBlocksPerSecond.getAsDouble();
            if (!Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException("Candidate magnitude must be finite and non-negative");
            }
        }
    }
}
