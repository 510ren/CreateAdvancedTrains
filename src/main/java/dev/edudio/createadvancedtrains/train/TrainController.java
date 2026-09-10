package dev.edudio.createadvancedtrains.train;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.simibubi.create.content.trains.entity.Train;

import dev.edudio.createadvancedtrains.config.AdvancedTrainsConfig;
import dev.edudio.createadvancedtrains.control.AtoController;
import dev.edudio.createadvancedtrains.control.AtoTargetSpeedDecision;
import dev.edudio.createadvancedtrains.control.braking.BrakingCurve;
import dev.edudio.createadvancedtrains.control.braking.BrakingCurveFailure;
import dev.edudio.createadvancedtrains.control.braking.BrakingCurveInput;
import dev.edudio.createadvancedtrains.control.braking.BrakingCurveResult;
import dev.edudio.createadvancedtrains.speed.SpeedLimitSource;
import dev.edudio.createadvancedtrains.train.error.CatCalculationErrorCode;
import dev.edudio.createadvancedtrains.train.error.CatCalculationErrorSnapshot;
import dev.edudio.createadvancedtrains.train.error.CatCalculationErrorState;
import dev.edudio.createadvancedtrains.train.error.CatErrorRecoveryResult;
import dev.edudio.createadvancedtrains.train.error.CatErrorRecoveryValidation;
import dev.edudio.createadvancedtrains.train.query.CreateTrainQueryUtil;
import dev.edudio.createadvancedtrains.train.query.NavigationStopState;
import dev.edudio.createadvancedtrains.train.query.NormalizedNavigationStop;

public class TrainController {

    private @Nonnull final UUID trainId;

    private final AtoController atoController;
    private final BrakingCurve brakingCurve;
    private final FlagDeterminer flagDeterminer;
    private final CatCalculationErrorState calculationErrorState;

    private TrainState state;
    private NormalizedNavigationStop normalizedNavigationStop;

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
        this.brakingCurve = BrakingCurve.phase5B();
        this.flagDeterminer = new FlagDeterminer(trainId);
        this.calculationErrorState = new CatCalculationErrorState();

        this.createTargetSpeed = train.targetSpeed;

        this.atoTargetSpeed = train.targetSpeed;

        refreshSharedState(train);
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

    public TrainOperationalFlags getOperationalFlags() {
        return flagDeterminer.currentFlags();
    }

    public NormalizedNavigationStop getNormalizedNavigationStop() {
        return normalizedNavigationStop;
    }

    public CatCalculationErrorSnapshot getCalculationError() {
        return calculationErrorState.snapshot();
    }

    public void applyAtoTargetSpeed(Train train, boolean isTargetSpeedCalculated) {

        refreshSharedState(train);
        atoController.observeOperationalState(
                flagDeterminer.currentFlags(),
                calculationErrorState.snapshot());

        /*
         * CATによる列車制御全体が無効なら、
         * CreateのtargetSpeedには介入しない。
         */
        if (!AdvancedTrainsConfig.CONTROL_ENABLED.get()) {
            updateState(train);
            return;
        }

        /*
         * ATOが無効なら、
         * CreateのtargetSpeedには介入しない。
         */
        if (!AdvancedTrainsConfig.ATO_ENABLED.get()) {
            updateState(train);
            return;
        }

        /*
         * CATが介入する前のCreateのtargetSpeedを保存する。
         */
        if (isTargetSpeedCalculated == true) {
            createTargetSpeed = train.targetSpeed;
        }

        if (!Double.isFinite(createTargetSpeed)) {
            calculationErrorState.latch(
                    CatCalculationErrorCode.NON_FINITE_INPUT,
                    "Create target speed is not finite");
        }

        /*
         * ATOによる最終目標速度を計算する。
         */
        AtoTargetSpeedDecision decision = atoController.evaluateTargetSpeed(
                createTargetSpeed,
                flagDeterminer.currentFlags(),
                calculationErrorState.snapshot());

        if (!decision.emitsTargetSpeedIntent()) {
            // Suppression deliberately leaves Create's current target untouched.
            // A concrete coasting target is outside the approved Phase 5B scope.
            atoTargetSpeed = train.targetSpeed;
            updateState(train);
            return;
        }
        atoTargetSpeed = decision.targetSpeed().getAsDouble();

        /*
         * Createへ目標速度を返す。
         */
        train.targetSpeed = atoTargetSpeed;

        updateState(train);
    }

    public void update(Train train) {
        refreshSharedState(train);
        atoController.observeOperationalState(
                flagDeterminer.currentFlags(),
                calculationErrorState.snapshot());
        updateState(train);
    }

    /**
     * Evaluates the pure braking model and latches calculation failures for this
     * train. It does not apply the returned speed to Create.
     */
    public BrakingCurveResult evaluateBrakingCurve(BrakingCurveInput input) {
        BrakingCurveResult result = brakingCurve.calculate(input);
        if (result.isCalculationError()) {
            latchBrakingCurveFailure(result.failure());
        }
        return result;
    }

    public void reportCalculationError(CatCalculationErrorCode code, String reason) {
        calculationErrorState.latch(code, reason);
    }

    /**
     * Recovery boundary for a future server-side wrench handler. Merely fixing
     * the source data never clears the latched state.
     */
    public CatErrorRecoveryResult recoverCalculationErrorAfterWrench(
            CatErrorRecoveryValidation validation) {
        return calculationErrorState.recoverAfterWrench(validation);
    }

    private void refreshSharedState(Train train) {
        validateTrainIdentity(train);
        normalizedNavigationStop = CreateTrainQueryUtil.queryNormalizedNavigationStop(train);
        flagDeterminer.update(train.id, normalizedNavigationStop);

        if (normalizedNavigationStop.state() == NavigationStopState.PAST_DESTINATION_OR_INVALID_STATE) {
            calculationErrorState.latch(
                    CatCalculationErrorCode.NAVIGATION_DESTINATION_OVERRUN,
                    "Create Navigation destination remains active after its distance became negative");
        } else if (normalizedNavigationStop.state() == NavigationStopState.INVALID) {
            calculationErrorState.latch(
                    CatCalculationErrorCode.INVALID_NAVIGATION_STATE,
                    "Create Navigation state is invalid: " + normalizedNavigationStop.invalidReason());
        }
    }

    private void validateTrainIdentity(Train train) {
        if (train == null || train.id == null || !trainId.equals(train.id)) {
            throw new IllegalArgumentException("TrainController may only observe its own train UUID");
        }
    }

    private void latchBrakingCurveFailure(BrakingCurveFailure failure) {
        CatCalculationErrorCode code = switch (failure) {
            case NON_FINITE_INPUT -> CatCalculationErrorCode.NON_FINITE_INPUT;
            case INVALID_BASE_ACCELERATION -> CatCalculationErrorCode.INVALID_BASE_ACCELERATION;
            case INVALID_PROFILE -> CatCalculationErrorCode.INVALID_PROFILE;
            case ITERATION_LIMIT_REACHED -> CatCalculationErrorCode.ITERATION_LIMIT_REACHED;
            case NON_POSITIVE_FORWARD_DISTANCE, NEGATIVE_SPEED, INVALID_SPEED_CEILING ->
                CatCalculationErrorCode.INVALID_BRAKING_CURVE_INPUT;
            case NONE -> throw new IllegalArgumentException("Cannot latch a successful braking result");
        };
        calculationErrorState.latch(code, "BrakingCurve failed: " + failure);
    }

    private void updateState(Train train) {

        state = new TrainState(
                train.speed,
                createTargetSpeed,
                atoTargetSpeed,
                flagDeterminer.currentFlags(),
                atoController.getOperatingState(),
                calculationErrorState.snapshot());
    }
}
