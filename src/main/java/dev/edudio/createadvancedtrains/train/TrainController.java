package dev.edudio.createadvancedtrains.train;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.simibubi.create.content.trains.entity.Train;

import dev.edudio.createadvancedtrains.config.AdvancedTrainsConfig;
import dev.edudio.createadvancedtrains.control.AtoController;
import dev.edudio.createadvancedtrains.speed.SpeedLimitSource;

public class TrainController {

    private @Nonnull final UUID trainId;

    private final AtoController atoController;

    private TrainState state;

    private double createTargetSpeed;
    private double atoTargetSpeed;

    public TrainController(Train train) {
        if (train == null) {
            throw new IllegalArgumentException("Train must not be null");
        }

        if (train.id == null) {
            throw new IllegalArgumentException("Train.id must not be null");
        }

        this.trainId = train.id;

        this.atoController = new AtoController();

        atoController.getSpeedLimitController().setLimit(SpeedLimitSource.TEST, 20.0);

        this.createTargetSpeed = train.targetSpeed;

        this.atoTargetSpeed = train.targetSpeed;

        updateState(train);
    }

    public UUID getTrainId() {
        return trainId;
    }

    public TrainState getState() {
        return state;
    }

    public void setSpeedLimit(
            SpeedLimitSource source,
            double speedLimit) {
        atoController
                .getSpeedLimitController()
                .setLimit(source, speedLimit);
    }

    public double getCreateTargetSpeed() {
        return createTargetSpeed;
    }

    public double getAtoTargetSpeed() {
        return atoTargetSpeed;
    }

    public AtoController getAtoController() {
        return atoController;
    }

    public void applyAtoTargetSpeed(Train train, boolean isTargetSpeedCalculated) {

        /*
         * CATによる列車制御全体が無効なら、
         * CreateのtargetSpeedには介入しない。
         */
        if (!AdvancedTrainsConfig.CONTROL_ENABLED.get()) {
            return;
        }

        /*
         * ATOが無効なら、
         * CreateのtargetSpeedには介入しない。
         */
        if (!AdvancedTrainsConfig.ATO_ENABLED.get()) {
            return;
        }

        /*
         * CATが介入する前のCreateのtargetSpeedを保存する。
         */
        if (isTargetSpeedCalculated == true) {
            createTargetSpeed = train.targetSpeed;
        }

        /*
         * ATOによる最終目標速度を計算する。
         */
        atoTargetSpeed = atoController.calculateTargetSpeed(
                createTargetSpeed);

        /*
         * Createへ目標速度を返す。
         */
        train.targetSpeed = atoTargetSpeed;

        updateState(train);
    }

    public void update(Train train) {
        updateState(train);
    }

    private void updateState(Train train) {

        state = new TrainState(
                train.speed,
                createTargetSpeed,
                atoTargetSpeed);
    }
}