package dev.edudio.createadvancedtrains.control;

import static dev.edudio.createadvancedtrains.constants.UnitConstants.TICKS_PER_SECOND;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

import dev.edudio.createadvancedtrains.config.AdvancedTrainsConfig;
import dev.edudio.createadvancedtrains.control.notch.NotchControlResult;
import dev.edudio.createadvancedtrains.control.notch.NotchController;
import dev.edudio.createadvancedtrains.control.notch.Notch;
import dev.edudio.createadvancedtrains.control.notch.NotchProfile;
import dev.edudio.createadvancedtrains.control.notch.NotchSelection;
import dev.edudio.createadvancedtrains.control.notch.NotchSelector;
import dev.edudio.createadvancedtrains.control.speed.TargetSpeedResolution;
import dev.edudio.createadvancedtrains.control.speed.TargetSpeedResolver;
import dev.edudio.createadvancedtrains.speed.SpeedLimitController;
import dev.edudio.createadvancedtrains.train.TrainOperationalFlags;
import dev.edudio.createadvancedtrains.train.error.CatCalculationErrorSnapshot;

/** Per-train Phase 6 coordinator. Calculation rules remain in dedicated types. */
public final class AtoController {

    private final SpeedLimitController speedLimitController;
    private final TargetSpeedResolver targetSpeedResolver;
    private final NotchSelector notchSelector;
    private final NotchController notchController;

    private AtoOperatingState operatingState;
    private long lastAdvancedServerTick;
    private TargetSpeedResolution cachedResolution;
    private NotchSelection cachedSelection;
    private double cachedEffectiveAcceleration;

    public AtoController() {
        NotchProfile profile = NotchProfile.phase5BProductionProfile();
        speedLimitController = new SpeedLimitController();
        targetSpeedResolver = new TargetSpeedResolver();
        notchSelector = new NotchSelector(profile);
        notchController = new NotchController(profile);
        operatingState = AtoOperatingState.ACTIVE;
        lastAdvancedServerTick = Long.MIN_VALUE;
    }

    public SpeedLimitController getSpeedLimitController() {
        return speedLimitController;
    }

    public AtoOperatingState getOperatingState() {
        return operatingState;
    }

    public double currentEffectiveAcceleration() {
        return notchController.effectiveAcceleration();
    }

    /** Compatibility boundary for pre-Phase-6 target-only callers. */
    public AtoTargetSpeedDecision evaluateTargetSpeed(
            double createTargetSpeed,
            TrainOperationalFlags operationalFlags,
            CatCalculationErrorSnapshot calculationError) {
        AtoOperatingState observedState = observeOperationalState(operationalFlags, calculationError);
        if (observedState != AtoOperatingState.ACTIVE) {
            return AtoTargetSpeedDecision.suppressed(observedState);
        }
        return AtoTargetSpeedDecision.target(calculateTargetSpeed(createTargetSpeed));
    }

    public AtoControlResult control(Input input) {
        Objects.requireNonNull(input, "input");
        AtoOperatingState observedState = observeOperationalState(
                input.operationalFlags(), input.calculationError());
        if (observedState != AtoOperatingState.ACTIVE) {
            suspendNotchResponse();
            return AtoControlResult.passThrough(
                    input.nativeTargetSpeedBlocksPerTick(),
                    input.originalAccelerationMod(),
                    operatingState,
                    true);
        }

        TargetSpeedResolution callResolution = targetSpeedResolver.resolve(input.resolverInput());
        if (callResolution.signedFinalSpeedBlocksPerSecond().isEmpty()) {
            suspendNotchResponse();
            return AtoControlResult.passThrough(
                    input.nativeTargetSpeedBlocksPerTick(),
                    input.originalAccelerationMod(),
                    operatingState,
                    true);
        }

        if (!input.notchControlEnabled()) {
            suspendNotchResponse();
            return new AtoControlResult(
                    callResolution.signedFinalSpeedBlocksPerSecond().getAsDouble() / TICKS_PER_SECOND,
                    input.originalAccelerationMod(),
                    operatingState,
                    false,
                    false,
                    Optional.empty(),
                    Optional.of(callResolution),
                    Optional.empty(),
                    Optional.empty());
        }

        boolean firstCallThisServerTick = input.serverTick() == Long.MIN_VALUE
                || input.serverTick() != lastAdvancedServerTick;
        Optional<Notch> previousRequestedNotch;
        if (firstCallThisServerTick) {
            previousRequestedNotch = notchController.requestedNotch();
            NotchSelection selection = notchSelector.select(new NotchSelector.Input(
                    input.currentSpeedBlocksPerSecond(),
                    callResolution.finalMagnitudeBlocksPerSecond().orElseThrow(),
                    input.brakingCurveLimitBlocksPerSecond(),
                    notchController.requestedNotch(),
                    notchController.effectiveAcceleration(),
                    input.baseAccelerationBlocksPerSecondSquared()));
            cachedSelection = selection;
            cachedEffectiveAcceleration = notchController.advance(
                    selection.requestedNotch(),
                    input.currentSpeedBlocksPerSecond(),
                    input.baseAccelerationBlocksPerSecondSquared())
                    .effectiveAcceleration();
            cachedResolution = callResolution;
            lastAdvancedServerTick = input.serverTick();
        } else {
            previousRequestedNotch = Optional.of(cachedSelection.requestedNotch());
            if (callResolution.finalMagnitudeBlocksPerSecond().orElseThrow()
                    < cachedResolution.finalMagnitudeBlocksPerSecond().orElseThrow()) {
                cachedResolution = callResolution;
            }
        }

        NotchControlResult notchControl = notchController.applyBoundary(
                cachedResolution.signedFinalSpeedBlocksPerSecond().orElseThrow(),
                input.currentSpeedBlocksPerTick(),
                input.baseAccelerationBlocksPerSecondSquared(),
                cachedEffectiveAcceleration);
        return new AtoControlResult(
                notchControl.finalTargetSpeedBlocksPerTick(),
                notchControl.accelerationMod(),
                operatingState,
                false,
                firstCallThisServerTick,
                previousRequestedNotch,
                Optional.of(cachedResolution),
                Optional.of(cachedSelection),
                Optional.of(notchControl));
    }

    public void suspendNotchResponse() {
        notchController.suspend();
        cachedResolution = null;
        cachedSelection = null;
        cachedEffectiveAcceleration = 0.0;
        lastAdvancedServerTick = Long.MIN_VALUE;
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

    /** Preserved for callers that only need the pre-Phase-6 limit calculation. */
    public double calculateTargetSpeed(double createTargetSpeed) {
        if (!AdvancedTrainsConfig.SPEED_LIMIT_ENABLED.get()) {
            return createTargetSpeed;
        }
        return speedLimitController.apply(createTargetSpeed);
    }

    public record Input(
            long serverTick,
            double nativeTargetSpeedBlocksPerTick,
            float originalAccelerationMod,
            double currentSpeedBlocksPerTick,
            double currentSpeedBlocksPerSecond,
            double baseAccelerationBlocksPerSecondSquared,
            OptionalDouble brakingCurveLimitBlocksPerSecond,
            TargetSpeedResolver.Input resolverInput,
            TrainOperationalFlags operationalFlags,
            CatCalculationErrorSnapshot calculationError,
            boolean notchControlEnabled) {
    }
}
