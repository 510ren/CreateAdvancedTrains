package dev.edudio.createadvancedtrains.train;

import static dev.edudio.createadvancedtrains.constants.UnitConstants.TICKS_PER_SECOND;
import static dev.edudio.createadvancedtrains.constants.UnitConstants.TICKS_PER_SECOND_SQUARED;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.simibubi.create.content.trains.entity.Train;

import dev.edudio.createadvancedtrains.config.AdvancedTrainsConfig;
import dev.edudio.createadvancedtrains.control.AtoControlResult;
import dev.edudio.createadvancedtrains.control.AtoController;
import dev.edudio.createadvancedtrains.control.braking.BrakingCurve;
import dev.edudio.createadvancedtrains.control.braking.BrakingCurveFailure;
import dev.edudio.createadvancedtrains.control.braking.BrakingCurveInput;
import dev.edudio.createadvancedtrains.control.braking.BrakingCurveResult;
import dev.edudio.createadvancedtrains.control.speed.NativeTargetZeroClassification;
import dev.edudio.createadvancedtrains.control.speed.TargetSpeedResolver;
import dev.edudio.createadvancedtrains.speed.SpeedLimitSource;
import dev.edudio.createadvancedtrains.train.error.CatCalculationErrorCode;
import dev.edudio.createadvancedtrains.train.error.CatCalculationErrorSnapshot;
import dev.edudio.createadvancedtrains.train.error.CatCalculationErrorState;
import dev.edudio.createadvancedtrains.train.error.CatErrorRecoveryResult;
import dev.edudio.createadvancedtrains.train.error.CatErrorRecoveryValidation;
import dev.edudio.createadvancedtrains.train.query.CreateTrainQueryUtil;
import dev.edudio.createadvancedtrains.train.query.NavigationStopState;
import dev.edudio.createadvancedtrains.train.query.NavigationTravelDirection;
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
    private Optional<AtoControlResult> lastAtoControlResult;
    private BrakingCurveResult lastBrakingCurveResult;
    private NativeTargetZeroClassification lastNativeZeroClassification;

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
        this.lastAtoControlResult = Optional.empty();
        this.lastBrakingCurveResult = BrakingCurveResult.noForwardStopTarget();
        this.lastNativeZeroClassification = NativeTargetZeroClassification.NOT_ZERO;

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

    public Optional<AtoControlResult> getLastAtoControlResult() {
        return lastAtoControlResult;
    }

    public BrakingCurveResult getLastBrakingCurveResult() {
        return lastBrakingCurveResult;
    }

    public NativeTargetZeroClassification getLastNativeZeroClassification() {
        return lastNativeZeroClassification;
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

    public float applyAtoControl(
            Train train,
            float originalAccelerationMod,
            long serverTick) {
        refreshSharedState(train);
        atoController.observeOperationalState(
                flagDeterminer.currentFlags(),
                calculationErrorState.snapshot());

        createTargetSpeed = train.targetSpeed;
        lastBrakingCurveResult = BrakingCurveResult.noForwardStopTarget();
        boolean hasGlobalStationDestination = hasGlobalStationDestination(train);
        lastNativeZeroClassification = TargetSpeedResolver.classifyNativeZero(
                createTargetSpeed * TICKS_PER_SECOND,
                normalizedNavigationStop.state(),
                hasGlobalStationDestination,
                train.navigation != null && train.navigation.waitingForSignal != null,
                train.manualTick);

        if (!AdvancedTrainsConfig.CONTROL_ENABLED.get()
                || !AdvancedTrainsConfig.ATO_ENABLED.get()) {
            atoController.suspendNotchResponse();
            atoTargetSpeed = train.targetSpeed;
            lastAtoControlResult = Optional.empty();
            updateState(train);
            return originalAccelerationMod;
        }

        TrainOperationalFlags flags = flagDeterminer.currentFlags();
        if (calculationErrorState.isActive() || flags.atDestinationOrArrivalPending()) {
            atoController.observeOperationalState(flags, calculationErrorState.snapshot());
            atoController.suspendNotchResponse();
            atoTargetSpeed = train.targetSpeed;
            lastAtoControlResult = Optional.of(AtoControlResult.passThrough(
                    train.targetSpeed,
                    originalAccelerationMod,
                    atoController.getOperatingState(),
                    true));
            updateState(train);
            return originalAccelerationMod;
        }

        if (!Double.isFinite(createTargetSpeed)
                || !Double.isFinite(train.speed)
                || !Float.isFinite(originalAccelerationMod)
                || originalAccelerationMod < 0.0f) {
            calculationErrorState.latch(
                    CatCalculationErrorCode.NON_FINITE_INPUT,
                    "Create speed, target speed, or acceleration modifier is invalid");
            return suspendCurrentCall(train, originalAccelerationMod);
        }

        double currentSpeedBlocksPerSecond = Math.abs(train.speed) * TICKS_PER_SECOND;
        double baseAccelerationBlocksPerSecondSquared = Math.abs(train.acceleration())
                * TICKS_PER_SECOND_SQUARED;
        double createSpeedCeilingBlocksPerSecond = Math.abs(train.maxSpeed()) * TICKS_PER_SECOND;
        if (!Double.isFinite(baseAccelerationBlocksPerSecondSquared)
                || baseAccelerationBlocksPerSecondSquared <= 0.0) {
            calculationErrorState.latch(
                    CatCalculationErrorCode.INVALID_BASE_ACCELERATION,
                    "Create base acceleration is not finite and positive");
            return suspendCurrentCall(train, originalAccelerationMod);
        }
        if (!Double.isFinite(createSpeedCeilingBlocksPerSecond)
                || createSpeedCeilingBlocksPerSecond < 0.0) {
            calculationErrorState.latch(
                    CatCalculationErrorCode.INVALID_BRAKING_CURVE_INPUT,
                    "Create maxSpeed ceiling is invalid");
            return suspendCurrentCall(train, originalAccelerationMod);
        }

        lastBrakingCurveResult = evaluateBrakingCandidate(
                currentSpeedBlocksPerSecond,
                baseAccelerationBlocksPerSecondSquared,
                createSpeedCeilingBlocksPerSecond);
        OptionalDouble diagnosticBrakingCurveLimit = lastBrakingCurveResult.isCalculationError()
                ? OptionalDouble.empty()
                : lastBrakingCurveResult.maximumPermittedSpeedBlocksPerSecond();
        if (calculationErrorState.isActive()) {
            return suspendCurrentCall(train, originalAccelerationMod);
        }
        OptionalDouble controlBrakingCurveLimit = hasGlobalStationDestination
                ? OptionalDouble.empty()
                : diagnosticBrakingCurveLimit;

        OptionalDouble catSpeedLimit = enabledCatSpeedLimit();
        double directionSign = resolveDirectionSign(train, normalizedNavigationStop);
        TargetSpeedResolver.Input resolverInput = new TargetSpeedResolver.Input(
                createTargetSpeed * TICKS_PER_SECOND,
                createSpeedCeilingBlocksPerSecond,
                catSpeedLimit,
                diagnosticBrakingCurveLimit,
                directionSign,
                normalizedNavigationStop.state(),
                hasGlobalStationDestination,
                train.navigation != null && train.navigation.waitingForSignal != null,
                train.manualTick);

        AtoControlResult result;
        try {
            result = atoController.control(new AtoController.Input(
                    serverTick,
                    createTargetSpeed,
                    originalAccelerationMod,
                    train.speed,
                    currentSpeedBlocksPerSecond,
                    baseAccelerationBlocksPerSecondSquared,
                    controlBrakingCurveLimit,
                    resolverInput,
                    flags,
                    calculationErrorState.snapshot(),
                    AdvancedTrainsConfig.NOTCH_ENABLED.get()));
        } catch (IllegalArgumentException exception) {
            calculationErrorState.latch(
                    CatCalculationErrorCode.INVALID_PROFILE,
                    "Phase 6 control calculation failed: " + exception.getMessage());
            return suspendCurrentCall(train, originalAccelerationMod);
        }

        train.targetSpeed = result.finalTargetSpeedBlocksPerTick();
        atoTargetSpeed = train.targetSpeed;
        lastAtoControlResult = Optional.of(result);

        updateState(train);
        return result.accelerationMod();
    }

    public void update(Train train) {
        refreshSharedState(train);
        atoController.observeOperationalState(
                flagDeterminer.currentFlags(),
                calculationErrorState.snapshot());
        updateState(train);
    }

    private BrakingCurveResult evaluateBrakingCandidate(
            double currentSpeedBlocksPerSecond,
            double baseAccelerationBlocksPerSecondSquared,
            double createSpeedCeilingBlocksPerSecond) {
        if (!AdvancedTrainsConfig.BRAKING_ENABLED.get()
                || normalizedNavigationStop.state() != NavigationStopState.AHEAD) {
            return BrakingCurveResult.noForwardStopTarget();
        }

        BrakingCurveResult result = evaluateBrakingCurve(new BrakingCurveInput(
                normalizedNavigationStop.forwardDistanceBlocks().orElseThrow(),
                currentSpeedBlocksPerSecond,
                atoController.currentEffectiveAcceleration(),
                baseAccelerationBlocksPerSecondSquared,
                createSpeedCeilingBlocksPerSecond));
        return result;
    }

    private OptionalDouble enabledCatSpeedLimit() {
        if (!AdvancedTrainsConfig.SPEED_LIMIT_ENABLED.get()) {
            return OptionalDouble.empty();
        }
        double limit = atoController.getSpeedLimitController().getEffectiveLimit();
        return Double.isFinite(limit) ? OptionalDouble.of(limit) : OptionalDouble.empty();
    }

    private static boolean hasGlobalStationDestination(Train train) {
        // Create 6.0.8 declares Navigation.destination as GlobalStation.
        return train.navigation != null && train.navigation.destination != null;
    }

    private float suspendCurrentCall(Train train, float originalAccelerationMod) {
        atoController.observeOperationalState(
                flagDeterminer.currentFlags(),
                calculationErrorState.snapshot());
        atoController.suspendNotchResponse();
        atoTargetSpeed = train.targetSpeed;
        lastAtoControlResult = Optional.of(AtoControlResult.passThrough(
                train.targetSpeed,
                originalAccelerationMod,
                atoController.getOperatingState(),
                true));
        updateState(train);
        return originalAccelerationMod;
    }

    private static double resolveDirectionSign(
            Train train,
            NormalizedNavigationStop navigationStop) {
        if (train.targetSpeed != 0.0) {
            return Math.signum(train.targetSpeed);
        }
        if (navigationStop.direction() == NavigationTravelDirection.FORWARD) {
            return 1.0;
        }
        if (navigationStop.direction() == NavigationTravelDirection.BACKWARD) {
            return -1.0;
        }
        if (train.speed != 0.0) {
            return Math.signum(train.speed);
        }
        return 1.0;
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
