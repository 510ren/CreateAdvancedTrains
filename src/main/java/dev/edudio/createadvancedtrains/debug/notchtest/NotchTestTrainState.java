package dev.edudio.createadvancedtrains.debug.notchtest;

import static dev.edudio.createadvancedtrains.constants.UnitConstants.TICKS_PER_SECOND;
import static dev.edudio.createadvancedtrains.constants.UnitConstants.TICKS_PER_SECOND_SQUARED;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.trains.entity.Train;

import dev.edudio.createadvancedtrains.control.notch.Notch;
import dev.edudio.createadvancedtrains.control.notch.NotchProfile;
import dev.edudio.createadvancedtrains.control.notch.NotchResponseModel;
import dev.edudio.createadvancedtrains.control.notch.NotchResponseModel.Response;

final class NotchTestTrainState {

    private static final String TRANSITION_TIMEBASE =
            "one_step_per_server_tick_with_notch_modifier_applied";

    private NotchResponseModel responseModel;
    private Notch lastCompletedNotch = Notch.N;

    private long currentServerTick = Long.MIN_VALUE;
    private double previousSpeedBlocksPerTick;
    private boolean hasPreviousSpeed;

    private boolean responseSteppedThisTick;
    private boolean controlAppliedThisTick;
    private final List<NotchTestApproachCall> approachCalls = new ArrayList<>();
    private int brakingDemandCallCount;
    private int notchModifierAppliedCallCount;
    private Double activeEffectiveAccelerationBlocksPerSecondSquared;
    private Double preCatTargetSpeedBlocksPerSecond;
    private Double finalTargetSpeedBlocksPerSecond;
    private Double currentCallPreCatTargetSpeedBlocksPerSecond;
    private Double currentCallFinalTargetSpeedBlocksPerSecond;
    private Float currentCallOriginalAccelerationMod;
    private StopTargetHoldResult currentCallStopTargetHold = StopTargetHoldResult.inactive();
    private Double baseAccelerationBlocksPerSecondSquared;
    private Double profileTargetAccelerationBlocksPerSecondSquared;
    private Response response = NotchResponseModel.neutralResponse();
    private String applicationState = "no_approach_call";
    private String applicationReason;
    private Float originalAccelerationMod;
    private Float appliedAccelerationMod;

    /**
     * このクラスのインスタンスを初期化します。
     * @param train 対象となるCreate列車。
     */
    NotchTestTrainState(Train train) {
        previousSpeedBlocksPerTick = train.speed;
        hasPreviousSpeed = Double.isFinite(train.speed);
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code beginTick}が示す処理区間を開始または準備します。
     * @param train 対象となるCreate列車。
     * @param serverTick 処理対象となるserver tick。
     * @param fixedNotch 仕様書に個別説明がないため、{@code fixedNotch}が示すノッチ状態またはノッチ候補。
     * @param profile 仕様書に個別説明がないため、{@code profile}が示すノッチまたは制動特性。
     */
    void beginTick(
            Train train,
            long serverTick,
            Notch fixedNotch,
            NotchProfile profile) {
        currentServerTick = serverTick;
        responseSteppedThisTick = false;
        controlAppliedThisTick = false;
        approachCalls.clear();
        brakingDemandCallCount = 0;
        notchModifierAppliedCallCount = 0;
        activeEffectiveAccelerationBlocksPerSecondSquared = null;
        preCatTargetSpeedBlocksPerSecond = finiteOrNull(train.targetSpeed * TICKS_PER_SECOND);
        finalTargetSpeedBlocksPerSecond = preCatTargetSpeedBlocksPerSecond;
        currentCallPreCatTargetSpeedBlocksPerSecond = null;
        currentCallFinalTargetSpeedBlocksPerSecond = null;
        currentCallOriginalAccelerationMod = null;
        currentCallStopTargetHold = StopTargetHoldResult.inactive();
        baseAccelerationBlocksPerSecondSquared = readBaseAcceleration(train);
        profileTargetAccelerationBlocksPerSecondSquared = targetAcceleration(
                train,
                fixedNotch,
                profile,
                baseAccelerationBlocksPerSecondSquared);
        applicationState = "no_approach_call";
        applicationReason = null;
        originalAccelerationMod = null;
        appliedAccelerationMod = null;
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code ensureTick}が示す処理区間を開始または準備します。
     * @param train 対象となるCreate列車。
     * @param serverTick 処理対象となるserver tick。
     * @param fixedNotch 仕様書に個別説明がないため、{@code fixedNotch}が示すノッチ状態またはノッチ候補。
     * @param profile 仕様書に個別説明がないため、{@code profile}が示すノッチまたは制動特性。
     */
    void ensureTick(
            Train train,
            long serverTick,
            Notch fixedNotch,
            NotchProfile profile) {
        if (currentServerTick != serverTick) {
            beginTick(train, serverTick, fixedNotch, profile);
        }
    }

    /**
     * 仕様書に独立した関数契約がないため、現在値を{@code observeTargets}が示す観測状態へ記録します。
     * @param preCatTargetSpeedBlocksPerTick 仕様書に個別説明がないため、{@code preCatTargetSpeedBlocksPerTick}が示すCreate境界の速度。単位はblocks/tick。
     * @param finalTargetSpeedBlocksPerTick 仕様書に個別説明がないため、{@code finalTargetSpeedBlocksPerTick}が示すCreate境界の速度。単位はblocks/tick。
     * @param accelerationMod Createへ渡される加速度倍率。
     * @param stopTargetHold 仕様書に個別説明がないため、現在の処理内容から推定した、{@code stopTargetHold}として使用される入力値。
     */
    void observeTargets(
            double preCatTargetSpeedBlocksPerTick,
            double finalTargetSpeedBlocksPerTick,
            float accelerationMod,
            StopTargetHoldResult stopTargetHold) {
        currentCallPreCatTargetSpeedBlocksPerSecond = finiteOrNull(
                preCatTargetSpeedBlocksPerTick * TICKS_PER_SECOND);
        currentCallFinalTargetSpeedBlocksPerSecond = finiteOrNull(
                finalTargetSpeedBlocksPerTick * TICKS_PER_SECOND);
        currentCallOriginalAccelerationMod = finiteOrNull(accelerationMod);
        currentCallStopTargetHold = stopTargetHold;

        if (!controlAppliedThisTick) {
            recordCurrentCall();
        }
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code markNoBrakingDemand}が示す状態を保持状態へ記録します。
     * @param reason 処理を行う理由を表す文字列。
     */
    void markNoBrakingDemand(String reason) {
        if (!controlAppliedThisTick) {
            applicationState = "no_braking_demand";
            applicationReason = reason;
        }
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code markInvalidInput}が示す状態を保持状態へ記録します。
     * @param reason 処理を行う理由を表す文字列。
     */
    void markInvalidInput(String reason) {
        if (!controlAppliedThisTick) {
            applicationState = "invalid_input";
            applicationReason = reason;
        }
    }

    /**
     * 仕様書に独立した関数契約がないため、現在値を{@code recordApproachCall}が示す観測状態へ記録します。
     * @param brakingDemand 仕様書に個別説明がないため、現在の処理内容から推定した、{@code brakingDemand}として使用される入力値。
     * @param reason 処理を行う理由を表す文字列。
     * @param returnedAccelerationMod 最終的にCreateへ返す加速度倍率。
     * @param notchModifierApplied 仕様書に個別説明がないため、現在の処理内容から推定した、{@code notchModifierApplied}として使用される入力値。
     */
    void recordApproachCall(
            boolean brakingDemand,
            String reason,
            float returnedAccelerationMod,
            boolean notchModifierApplied) {
        if (brakingDemand) {
            brakingDemandCallCount++;
        }
        if (notchModifierApplied) {
            notchModifierAppliedCallCount++;
            activeEffectiveAccelerationBlocksPerSecondSquared = response.effectiveAcceleration();
        }

        approachCalls.add(new NotchTestApproachCall(
                approachCalls.size() + 1,
                currentCallPreCatTargetSpeedBlocksPerSecond,
                currentCallPreCatTargetSpeedBlocksPerSecond,
                currentCallFinalTargetSpeedBlocksPerSecond,
                brakingDemand,
                reason,
                currentCallOriginalAccelerationMod,
                finiteOrNull(returnedAccelerationMod),
                notchModifierApplied,
                currentCallStopTargetHold.state(),
                currentCallStopTargetHold.overrideApplied(),
                currentCallStopTargetHold.triggerReason()));
    }

    /**
     * 入力値へこのクラスの規則を適用して結果を返します。
     * @param commandedNotch 仕様書に個別説明がないため、{@code commandedNotch}が示すノッチ状態またはノッチ候補。
     * @param baseAcceleration 仕様書に個別説明がないため、{@code baseAcceleration}が示す加速度または加速度倍率。単位は呼出元の境界定義に従います。
     * @param profileTargetAcceleration 仕様書に個別説明がないため、{@code profileTargetAcceleration}が示す加速度または加速度倍率。単位は呼出元の境界定義に従います。
     * @return 処理または計算によって得られた数値。
     */
    Float apply(
            Notch commandedNotch,
            double baseAcceleration,
            double profileTargetAcceleration) {
        baseAccelerationBlocksPerSecondSquared = baseAcceleration;
        profileTargetAccelerationBlocksPerSecondSquared = profileTargetAcceleration;

        if (!responseSteppedThisTick) {
            if (responseModel == null) {
                responseModel = new NotchResponseModel(lastCompletedNotch);
            }
            response = responseModel.step(commandedNotch, profileTargetAcceleration);
            if (response.transitionElapsedTicks() >= NotchResponseModel.TRANSITION_TICKS) {
                lastCompletedNotch = response.appliedNotch();
            }
            responseSteppedThisTick = true;
        }

        double modifier = -response.effectiveAcceleration() / baseAcceleration;
        if (!Double.isFinite(modifier) || modifier < 0.0 || modifier > Float.MAX_VALUE) {
            markInvalidInput("effective_acceleration_modifier_invalid");
            return null;
        }

        appliedAccelerationMod = (float) modifier;
        recordCurrentCall();
        controlAppliedThisTick = true;
        applicationState = "applied";
        applicationReason = null;
        return appliedAccelerationMod;
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code finishTick}が示す処理区間を終了します。
     * @param train 対象となるCreate列車。
     * @param serverTick 処理対象となるserver tick。
     * @param fixedNotch 仕様書に個別説明がないため、{@code fixedNotch}が示すノッチ状態またはノッチ候補。
     * @return 処理によって得られた結果。
     */
    NotchTestSnapshot finishTick(
            Train train,
            long serverTick,
            Notch fixedNotch) {
        // A missing/non-braking approach call only pauses the fixed-notch response.
        // The owning manager discards this state when the session ends or the train disappears.
        double currentSpeedBlocksPerTick = train.speed;
        Double measuredAcceleration = null;
        Double signedVelocityAcceleration = null;

        if (hasPreviousSpeed && Double.isFinite(currentSpeedBlocksPerTick)) {
            measuredAcceleration = (Math.abs(currentSpeedBlocksPerTick)
                    - Math.abs(previousSpeedBlocksPerTick)) * TICKS_PER_SECOND_SQUARED;
            signedVelocityAcceleration = (currentSpeedBlocksPerTick
                    - previousSpeedBlocksPerTick) * TICKS_PER_SECOND_SQUARED;
        }

        previousSpeedBlocksPerTick = currentSpeedBlocksPerTick;
        hasPreviousSpeed = Double.isFinite(currentSpeedBlocksPerTick);

        return new NotchTestSnapshot(
                serverTick,
                train.id,
                fixedNotch,
                controlAppliedThisTick ? response.commandedNotch() : fixedNotch,
                response.appliedNotch(),
                response.transitionElapsedTicks(),
                response.transitionProgress(),
                finiteOrNull(currentSpeedBlocksPerTick * TICKS_PER_SECOND),
                preCatTargetSpeedBlocksPerSecond,
                finalTargetSpeedBlocksPerSecond,
                baseAccelerationBlocksPerSecondSquared,
                profileTargetAccelerationBlocksPerSecondSquared,
                response.effectiveAcceleration(),
                measuredAcceleration,
                signedVelocityAcceleration,
                applicationState,
                applicationReason,
                controlAppliedThisTick,
                originalAccelerationMod,
                appliedAccelerationMod,
                approachCalls.size(),
                brakingDemandCallCount,
                notchModifierAppliedCallCount,
                List.copyOf(approachCalls),
                new NotchTestResponseState(
                        response.effectiveAcceleration(),
                        activeEffectiveAccelerationBlocksPerSecondSquared,
                        response.transitionElapsedTicks(),
                        TRANSITION_TIMEBASE),
                distanceToDestination(train));
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code readBaseAcceleration}が示す値を現在状態から読み取ります。
     * @param train 対象となるCreate列車。
     * @return 処理または計算によって得られた数値。
     */
    private static Double readBaseAcceleration(Train train) {
        double acceleration = Math.abs(train.acceleration()) * TICKS_PER_SECOND_SQUARED;
        return Double.isFinite(acceleration) && acceleration > 0.0 ? acceleration : null;
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code targetAcceleration}が示す状態の結果オブジェクトを生成します。
     * @param train 対象となるCreate列車。
     * @param fixedNotch 仕様書に個別説明がないため、{@code fixedNotch}が示すノッチ状態またはノッチ候補。
     * @param profile 仕様書に個別説明がないため、{@code profile}が示すノッチまたは制動特性。
     * @param baseAcceleration 仕様書に個別説明がないため、{@code baseAcceleration}が示す加速度または加速度倍率。単位は呼出元の境界定義に従います。
     * @return 処理または計算によって得られた数値。
     */
    private static Double targetAcceleration(
            Train train,
            Notch fixedNotch,
            NotchProfile profile,
            Double baseAcceleration) {
        if (baseAcceleration == null || !Double.isFinite(train.speed)) {
            return null;
        }

        return profile.targetAcceleration(
                fixedNotch,
                train.speed * TICKS_PER_SECOND,
                baseAcceleration);
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code distanceToDestination}としてまとめられている処理を実行します。
     * @param train 対象となるCreate列車。
     * @return 処理または計算によって得られた数値。
     */
    private static Double distanceToDestination(Train train) {
        if (train.navigation == null || train.navigation.destination == null) {
            return null;
        }

        double distance = train.navigation.distanceToDestination;
        return Double.isFinite(distance) ? distance : null;
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code finiteOrNull}としてまとめられている処理を実行します。
     * @param value 処理対象の値。
     * @return 処理または計算によって得られた数値。
     */
    private static Double finiteOrNull(double value) {
        return Double.isFinite(value) ? value : null;
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code finiteOrNull}としてまとめられている処理を実行します。
     * @param value 処理対象の値。
     * @return 処理または計算によって得られた数値。
     */
    private static Float finiteOrNull(float value) {
        return Float.isFinite(value) ? value : null;
    }

    /**
     * 仕様書に独立した関数契約がないため、現在値を{@code recordCurrentCall}が示す観測状態へ記録します。
     */
    private void recordCurrentCall() {
        preCatTargetSpeedBlocksPerSecond = currentCallPreCatTargetSpeedBlocksPerSecond;
        finalTargetSpeedBlocksPerSecond = currentCallFinalTargetSpeedBlocksPerSecond;
        originalAccelerationMod = currentCallOriginalAccelerationMod;
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code appliedNotch}としてまとめられている処理を実行します。
     * @return 処理によって得られた結果。
     */
    Notch appliedNotch() {
        return response.appliedNotch();
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code controlAppliedThisTick}としてまとめられている処理を実行します。
     * @return 条件を満たす場合はtrue、それ以外はfalse。
     */
    boolean controlAppliedThisTick() {
        return controlAppliedThisTick;
    }
}
