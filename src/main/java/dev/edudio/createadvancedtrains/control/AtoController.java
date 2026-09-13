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

    /**
     * このクラスのインスタンスを初期化します。
     */
    public AtoController() {
        NotchProfile profile = NotchProfile.phase5BProductionProfile();
        speedLimitController = new SpeedLimitController();
        targetSpeedResolver = new TargetSpeedResolver();
        notchSelector = new NotchSelector(profile);
        notchController = new NotchController(profile);
        operatingState = AtoOperatingState.ACTIVE;
        lastAdvancedServerTick = Long.MIN_VALUE;
    }

    /**
     * 現在のSpeedLimitControllerを返します。
     * @return 処理によって得られた結果。
     */
    public SpeedLimitController getSpeedLimitController() {
        return speedLimitController;
    }

    /**
     * 現在のOperatingStateを返します。
     * @return 処理によって得られた結果。
     */
    public AtoOperatingState getOperatingState() {
        return operatingState;
    }

    /**
     * 仕様書に独立した関数契約がないため、現在保持している{@code current effective acceleration}を返します。
     * @return 処理または計算によって得られた数値。
     */
    public double currentEffectiveAcceleration() {
        return notchController.effectiveAcceleration();
    }

    /**
     * Compatibility boundary for pre-Phase-6 target-only callers.
     * @param createTargetSpeed Createが設定した目標速度。
     * @param operationalFlags 仕様書に個別説明がないため、現在の処理内容から推定した、{@code operationalFlags}として使用される入力値。
     * @param calculationError 仕様書に個別説明がないため、現在の処理内容から推定した、{@code calculationError}として使用される入力値。
     * @return 処理によって得られた結果。
     */
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

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code control}としてまとめられている処理を実行します。
     * @param input 計算または判定に使用する入力値。
     * @return 処理によって得られた結果。
     */
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

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code suspendNotchResponse}としてまとめられている処理を実行します。
     */
    public void suspendNotchResponse() {
        notchController.suspend();
        cachedResolution = null;
        cachedSelection = null;
        cachedEffectiveAcceleration = 0.0;
        lastAdvancedServerTick = Long.MIN_VALUE;
    }

    /**
     * 仕様書に独立した関数契約がないため、現在値を{@code observeOperationalState}が示す観測状態へ記録します。
     * @param operationalFlags 仕様書に個別説明がないため、現在の処理内容から推定した、{@code operationalFlags}として使用される入力値。
     * @param calculationError 仕様書に個別説明がないため、現在の処理内容から推定した、{@code calculationError}として使用される入力値。
     * @return 処理によって得られた結果。
     */
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

    /**
     * Preserved for callers that only need the pre-Phase-6 limit calculation.
     * @param createTargetSpeed Createが設定した目標速度。
     * @return 処理または計算によって得られた数値。
     */
    public double calculateTargetSpeed(double createTargetSpeed) {
        if (!AdvancedTrainsConfig.SPEED_LIMIT_ENABLED.get()) {
            return createTargetSpeed;
        }
        return speedLimitController.apply(createTargetSpeed);
    }

    /**
     * 仕様書に独立した型契約がないため、現在の利用箇所から推定した不変データを保持します。
     * @param serverTick 処理対象となるserver tick。
     * @param nativeTargetSpeedBlocksPerTick 仕様書に個別説明がないため、{@code nativeTargetSpeedBlocksPerTick}が示すCreate境界の速度。単位はblocks/tick。
     * @param originalAccelerationMod CATが変更する前のCreate加速度倍率。
     * @param currentSpeedBlocksPerTick 仕様書に個別説明がないため、{@code currentSpeedBlocksPerTick}が示すCreate境界の速度。単位はblocks/tick。
     * @param currentSpeedBlocksPerSecond 現在速度の大きさ。単位はblocks/s。
     * @param baseAccelerationBlocksPerSecondSquared Create基本加速度の大きさ。単位はblocks/s^2。
     * @param brakingCurveLimitBlocksPerSecond 仕様書に個別説明がないため、{@code brakingCurveLimitBlocksPerSecond}が示す速度。単位はblocks/s。
     * @param resolverInput 仕様書に個別説明がないため、現在の処理内容から推定した、{@code resolverInput}として使用される入力値。
     * @param operationalFlags 仕様書に個別説明がないため、現在の処理内容から推定した、{@code operationalFlags}として使用される入力値。
     * @param calculationError 仕様書に個別説明がないため、現在の処理内容から推定した、{@code calculationError}として使用される入力値。
     * @param notchControlEnabled 仕様書に個別説明がないため、現在の処理内容から推定した、{@code notchControlEnabled}として使用される入力値。
     */
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
