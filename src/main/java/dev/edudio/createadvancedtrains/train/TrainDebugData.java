package dev.edudio.createadvancedtrains.train;

import java.util.UUID;

public record TrainDebugData(
        UUID trainId,
        double speed,
        double createTargetSpeed,
        double atoTargetSpeed) {
}