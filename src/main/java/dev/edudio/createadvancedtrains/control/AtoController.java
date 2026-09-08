package dev.edudio.createadvancedtrains.control;

import dev.edudio.createadvancedtrains.config.AdvancedTrainsConfig;
import dev.edudio.createadvancedtrains.speed.SpeedLimitController;

public class AtoController {

    private final SpeedLimitController speedLimitController;

    public AtoController() {
        this.speedLimitController = new SpeedLimitController();
    }

    public SpeedLimitController getSpeedLimitController() {
        return speedLimitController;
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