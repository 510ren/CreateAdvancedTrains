package dev.edudio.createadvancedtrains.train;

import dev.edudio.createadvancedtrains.control.AtoOperatingState;
import dev.edudio.createadvancedtrains.train.error.CatCalculationErrorSnapshot;

public record TrainState(
        double speed,
        double createTargetSpeed,
        double atoTargetSpeed,
        TrainOperationalFlags operationalFlags,
        AtoOperatingState atoOperatingState,
        CatCalculationErrorSnapshot calculationError) {
}
