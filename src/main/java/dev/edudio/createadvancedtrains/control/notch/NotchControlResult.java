package dev.edudio.createadvancedtrains.control.notch;

import java.util.Objects;

public record NotchControlResult(
        double finalTargetSpeedBlocksPerTick,
        float accelerationMod,
        NotchResponseModel.Response response,
        boolean suspended) {

    public NotchControlResult {
        Objects.requireNonNull(response, "response");
        if (!Double.isFinite(finalTargetSpeedBlocksPerTick)
                || !Float.isFinite(accelerationMod)
                || accelerationMod < 0.0f) {
            throw new IllegalArgumentException("Create boundary result is invalid");
        }
    }
}
