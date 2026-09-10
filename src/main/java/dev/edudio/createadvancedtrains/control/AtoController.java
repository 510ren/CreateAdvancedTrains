package dev.edudio.createadvancedtrains.control;

import java.util.Objects;

import dev.edudio.createadvancedtrains.config.AdvancedTrainsConfig;
import dev.edudio.createadvancedtrains.speed.SpeedLimitController;
import dev.edudio.createadvancedtrains.train.TrainOperationalFlags;
import dev.edudio.createadvancedtrains.train.error.CatCalculationErrorSnapshot;

public class AtoController {

    private final SpeedLimitController speedLimitController;
    private AtoOperatingState operatingState;

    public AtoController() {
        this.speedLimitController = new SpeedLimitController();
        this.operatingState = AtoOperatingState.ACTIVE;
    }

    public SpeedLimitController getSpeedLimitController() {
        return speedLimitController;
    }

    public AtoOperatingState getOperatingState() {
        return operatingState;
    }

    /**
     * Consumes centrally-derived state. This controller never owns or mutates
     * the shared arrival flag or the latched CAT error.
     */
    public AtoTargetSpeedDecision evaluateTargetSpeed(
            double createTargetSpeed,
            TrainOperationalFlags operationalFlags,
            CatCalculationErrorSnapshot calculationError) {
        AtoOperatingState observedState = observeOperationalState(
                operationalFlags,
                calculationError);
        if (observedState == AtoOperatingState.CALCULATION_ERROR) {
            return AtoTargetSpeedDecision.suppressed(operatingState);
        }
        if (observedState == AtoOperatingState.ARRIVAL_PENDING) {
            return AtoTargetSpeedDecision.suppressed(operatingState);
        }

        return AtoTargetSpeedDecision.target(calculateTargetSpeed(createTargetSpeed));
    }

    public AtoOperatingState observeOperationalState(
            TrainOperationalFlags operationalFlags,
            CatCalculationErrorSnapshot calculationError) {
        Objects.requireNonNull(operationalFlags, "operationalFlags");
        Objects.requireNonNull(calculationError, "calculationError");
        if (calculationError.active()) {
            operatingState = AtoOperatingState.CALCULATION_ERROR;
        } else if (operationalFlags.atDestinationOrArrivalPending()) {
            operatingState = AtoOperatingState.ARRIVAL_PENDING;
        } else {
            operatingState = AtoOperatingState.ACTIVE;
        }
        return operatingState;
    }

    public double calculateTargetSpeed(
            double createTargetSpeed) {

        /*
         * 速度制限機能が無効なら、
         * CreateのtargetSpeedをそのまま返す。
         */
        if (!AdvancedTrainsConfig.SPEED_LIMIT_ENABLED.get()) {
            return createTargetSpeed;
        }

        return speedLimitController.apply(
                createTargetSpeed);
    }
}
