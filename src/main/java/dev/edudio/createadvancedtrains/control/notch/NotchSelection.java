package dev.edudio.createadvancedtrains.control.notch;

import java.util.Objects;

public record NotchSelection(
        Notch requestedNotch,
        boolean serviceBrakeInsufficient,
        double predictedSpeedBlocksPerSecond,
        double predictionLimitBlocksPerSecond) {

    public NotchSelection {
        Objects.requireNonNull(requestedNotch, "requestedNotch");
        if (!Double.isFinite(predictedSpeedBlocksPerSecond)
                || !Double.isFinite(predictionLimitBlocksPerSecond)) {
            throw new IllegalArgumentException("Prediction values must be finite");
        }
    }
}
