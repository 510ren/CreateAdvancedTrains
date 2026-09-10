package dev.edudio.createadvancedtrains.control.speed;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;

public record TargetSpeedResolution(
        OptionalDouble finalMagnitudeBlocksPerSecond,
        OptionalDouble signedFinalSpeedBlocksPerSecond,
        Set<TargetSpeedCandidateSource> adoptedSources,
        List<TargetSpeedCandidate> candidates,
        NativeTargetZeroClassification nativeZeroClassification,
        String diagnostic) {

    public TargetSpeedResolution {
        Objects.requireNonNull(finalMagnitudeBlocksPerSecond, "finalMagnitudeBlocksPerSecond");
        Objects.requireNonNull(signedFinalSpeedBlocksPerSecond, "signedFinalSpeedBlocksPerSecond");
        adoptedSources = Set.copyOf(adoptedSources);
        candidates = List.copyOf(candidates);
        Objects.requireNonNull(nativeZeroClassification, "nativeZeroClassification");
        Objects.requireNonNull(diagnostic, "diagnostic");
    }
}
