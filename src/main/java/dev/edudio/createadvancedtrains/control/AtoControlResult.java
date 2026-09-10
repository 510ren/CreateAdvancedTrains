package dev.edudio.createadvancedtrains.control;

import java.util.Objects;
import java.util.Optional;

import dev.edudio.createadvancedtrains.control.notch.NotchControlResult;
import dev.edudio.createadvancedtrains.control.notch.Notch;
import dev.edudio.createadvancedtrains.control.notch.NotchSelection;
import dev.edudio.createadvancedtrains.control.speed.TargetSpeedResolution;

public record AtoControlResult(
        double finalTargetSpeedBlocksPerTick,
        float accelerationMod,
        AtoOperatingState operatingState,
        boolean suspended,
        boolean responseAdvanced,
        Optional<Notch> previousRequestedNotch,
        Optional<TargetSpeedResolution> resolution,
        Optional<NotchSelection> notchSelection,
        Optional<NotchControlResult> notchControl) {

    public AtoControlResult {
        Objects.requireNonNull(operatingState, "operatingState");
        previousRequestedNotch = Objects.requireNonNull(previousRequestedNotch, "previousRequestedNotch");
        resolution = Objects.requireNonNull(resolution, "resolution");
        notchSelection = Objects.requireNonNull(notchSelection, "notchSelection");
        notchControl = Objects.requireNonNull(notchControl, "notchControl");
        if (!suspended && (!Double.isFinite(finalTargetSpeedBlocksPerTick)
                || !Float.isFinite(accelerationMod)
                || accelerationMod < 0.0f)) {
            throw new IllegalArgumentException("ATO Create-boundary result is invalid");
        }
    }

    public static AtoControlResult passThrough(
            double nativeTargetSpeedBlocksPerTick,
            float originalAccelerationMod,
            AtoOperatingState state,
            boolean suspended) {
        return new AtoControlResult(
                nativeTargetSpeedBlocksPerTick,
                originalAccelerationMod,
                state,
                suspended,
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }
}
