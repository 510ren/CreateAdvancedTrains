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
    private Notch lastCompletedNotch = Notch.COAST;

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
    private Response response = NotchResponseModel.coastResponse();
    private String applicationState = "no_approach_call";
    private String applicationReason;
    private Float originalAccelerationMod;
    private Float appliedAccelerationMod;

    NotchTestTrainState(Train train) {
        previousSpeedBlocksPerTick = train.speed;
        hasPreviousSpeed = Double.isFinite(train.speed);
    }

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

    void ensureTick(
            Train train,
            long serverTick,
            Notch fixedNotch,
            NotchProfile profile) {
        if (currentServerTick != serverTick) {
            beginTick(train, serverTick, fixedNotch, profile);
        }
    }

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

    void markNoBrakingDemand(String reason) {
        if (!controlAppliedThisTick) {
            applicationState = "no_braking_demand";
            applicationReason = reason;
        }
    }

    void markInvalidInput(String reason) {
        if (!controlAppliedThisTick) {
            applicationState = "invalid_input";
            applicationReason = reason;
        }
    }

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

    private static Double readBaseAcceleration(Train train) {
        double acceleration = Math.abs(train.acceleration()) * TICKS_PER_SECOND_SQUARED;
        return Double.isFinite(acceleration) && acceleration > 0.0 ? acceleration : null;
    }

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

    private static Double distanceToDestination(Train train) {
        if (train.navigation == null || train.navigation.destination == null) {
            return null;
        }

        double distance = train.navigation.distanceToDestination;
        return Double.isFinite(distance) ? distance : null;
    }

    private static Double finiteOrNull(double value) {
        return Double.isFinite(value) ? value : null;
    }

    private static Float finiteOrNull(float value) {
        return Float.isFinite(value) ? value : null;
    }

    private void recordCurrentCall() {
        preCatTargetSpeedBlocksPerSecond = currentCallPreCatTargetSpeedBlocksPerSecond;
        finalTargetSpeedBlocksPerSecond = currentCallFinalTargetSpeedBlocksPerSecond;
        originalAccelerationMod = currentCallOriginalAccelerationMod;
    }

    Notch appliedNotch() {
        return response.appliedNotch();
    }

    boolean controlAppliedThisTick() {
        return controlAppliedThisTick;
    }
}
