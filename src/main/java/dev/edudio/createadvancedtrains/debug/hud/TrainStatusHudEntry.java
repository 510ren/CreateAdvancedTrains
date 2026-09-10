package dev.edudio.createadvancedtrains.debug.hud;

import java.util.Objects;
import java.util.UUID;

import dev.edudio.createadvancedtrains.control.notch.Notch;
import dev.edudio.createadvancedtrains.train.query.StopTargetDistance;

/**
 * One server-observed train row for the development HUD, in CAT units.
 */
public record TrainStatusHudEntry(
        UUID trainId,
        double speedBlocksPerSecond,
        Notch commandedNotch,
        Notch appliedNotch,
        boolean notchControlApplied,
        double createTargetSpeedBlocksPerSecond,
        double atoTargetSpeedBlocksPerSecond,
        double measuredAccelerationBlocksPerSecondSquared,
        double createBaseAccelerationBlocksPerSecondSquared,
        StopTargetDistance stopTargetDistance) {

    public TrainStatusHudEntry {
        Objects.requireNonNull(trainId, "trainId");
        Objects.requireNonNull(commandedNotch, "commandedNotch");
        Objects.requireNonNull(appliedNotch, "appliedNotch");
        Objects.requireNonNull(stopTargetDistance, "stopTargetDistance");
    }
}
