package dev.edudio.createadvancedtrains.debug.traindata;

import static dev.edudio.createadvancedtrains.constants.UnitConstants.TICKS_PER_SECOND;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

import com.simibubi.create.content.trains.entity.Train;

import dev.edudio.createadvancedtrains.control.AtoControlResult;
import dev.edudio.createadvancedtrains.control.notch.NotchControlResult;
import dev.edudio.createadvancedtrains.control.notch.NotchResponseModel.Response;
import dev.edudio.createadvancedtrains.control.notch.NotchSelection;
import dev.edudio.createadvancedtrains.control.notch.NotchSelector;
import dev.edudio.createadvancedtrains.control.speed.TargetSpeedResolution;
import dev.edudio.createadvancedtrains.train.TrainController;

/** Immutable schema-v2 observation of one existing Create approach call. */
record ApproachCallSnapshot(
        UUID trainId,
        long serverTick,
        int callIndexInServerTick,
        boolean firstApproachCallThisServerTick,
        double speedBlocksPerSecond,
        double nativeTargetBlocksPerSecond,
        double finalTargetBlocksPerSecond,
        String navigationState,
        Double distanceToDestinationBlocks,
        String nativeZeroClassification,
        boolean waitingForSignal,
        boolean manualTick,
        String brakingCurveStatus,
        Double brakingCurveLimitBlocksPerSecond,
        Double brakingCurveUsableDistanceBlocks,
        Double brakingCurvePredictedStoppingDistanceBlocks,
        Double brakingCurvePredictedOvershootBlocks,
        Double resolvedLimitBlocksPerSecond,
        Double safeSpeedBlocksPerSecond,
        Double lowerBandBlocksPerSecond,
        Double upperBandBlocksPerSecond,
        Double brakeLimitBlocksPerSecond,
        String previousRequestedNotch,
        String selectedNotch,
        Double selectionPredictionBlocksPerSecond,
        Boolean selectionBrakeInsufficient,
        boolean responseAdvanced,
        String commandedNotch,
        String appliedNotch,
        Integer transitionElapsedTicks,
        Double transitionProgress,
        Double transitionStartAccelerationBlocksPerSecondSquared,
        Double targetAccelerationBlocksPerSecondSquared,
        Double effectiveAccelerationBlocksPerSecondSquared,
        float originalAccelerationMod,
        float returnedAccelerationMod,
        String atoOperatingState) {

    static ApproachCallSnapshot capture(
            Train train,
            TrainController controller,
            long serverTick,
            int callIndexInServerTick,
            double nativeTargetBlocksPerTick,
            double finalTargetBlocksPerTick,
            float originalAccelerationMod,
            float returnedAccelerationMod) {
        Optional<AtoControlResult> atoResult = controller.getLastAtoControlResult();
        Optional<TargetSpeedResolution> resolution = atoResult.flatMap(AtoControlResult::resolution);
        Optional<NotchSelection> selection = atoResult.flatMap(AtoControlResult::notchSelection);
        Optional<NotchControlResult> notchControl = atoResult.flatMap(AtoControlResult::notchControl);
        Optional<Response> response = notchControl.map(NotchControlResult::response);

        Double resolvedLimit = resolution
                .map(TargetSpeedResolution::finalMagnitudeBlocksPerSecond)
                .map(ApproachCallSnapshot::boxed)
                .orElse(null);
        Double safeSpeed = resolvedLimit == null
                ? null
                : resolvedLimit * NotchSelector.SAFE_SPEED_FACTOR;
        Double brakingLimit = boxed(
                controller.getLastBrakingCurveResult().maximumPermittedSpeedBlocksPerSecond());
        boolean hasGlobalStationDestination = train.navigation != null
                && train.navigation.destination != null;
        Double controlBrakingLimit = hasGlobalStationDestination ? null : brakingLimit;
        Double brakeLimit = safeSpeed == null
                ? null
                : controlBrakingLimit == null ? safeSpeed : Math.min(safeSpeed, controlBrakingLimit);

        return new ApproachCallSnapshot(
                train.id,
                serverTick,
                callIndexInServerTick,
                callIndexInServerTick == 1,
                train.speed * TICKS_PER_SECOND,
                nativeTargetBlocksPerTick * TICKS_PER_SECOND,
                finalTargetBlocksPerTick * TICKS_PER_SECOND,
                controller.getNormalizedNavigationStop().state().name(),
                boxed(controller.getNormalizedNavigationStop().observedCreateDistanceBlocks()),
                controller.getLastNativeZeroClassification().name(),
                train.navigation != null && train.navigation.waitingForSignal != null,
                train.manualTick,
                controller.getLastBrakingCurveResult().status().name(),
                brakingLimit,
                boxed(controller.getLastBrakingCurveResult().usableDistanceBlocks()),
                boxed(controller.getLastBrakingCurveResult().predictedStoppingDistanceBlocks()),
                boxed(controller.getLastBrakingCurveResult().predictedOvershootBlocks()),
                resolvedLimit,
                safeSpeed,
                safeSpeed == null ? null : safeSpeed
                        - NotchSelector.NORMAL_BAND_OFFSET_BLOCKS_PER_SECOND
                        - NotchSelector.HYSTERESIS_BLOCKS_PER_SECOND,
                safeSpeed == null ? null : safeSpeed
                        + NotchSelector.NORMAL_BAND_OFFSET_BLOCKS_PER_SECOND
                        + NotchSelector.HYSTERESIS_BLOCKS_PER_SECOND,
                brakeLimit,
                atoResult.flatMap(AtoControlResult::previousRequestedNotch)
                        .map(value -> value.name()).orElse(null),
                selection.map(value -> value.requestedNotch().name()).orElse(null),
                selection.map(NotchSelection::predictedSpeedBlocksPerSecond).orElse(null),
                selection.map(NotchSelection::serviceBrakeInsufficient).orElse(null),
                atoResult.map(AtoControlResult::responseAdvanced).orElse(false),
                response.map(value -> value.commandedNotch().name()).orElse(null),
                response.map(value -> value.appliedNotch().name()).orElse(null),
                response.map(Response::transitionElapsedTicks).orElse(null),
                response.map(Response::transitionProgress).orElse(null),
                response.map(Response::transitionStartAcceleration).orElse(null),
                response.map(Response::targetAcceleration).orElse(null),
                response.map(Response::effectiveAcceleration).orElse(null),
                originalAccelerationMod,
                returnedAccelerationMod,
                controller.getAtoController().getOperatingState().name());
    }

    private static Double boxed(OptionalDouble value) {
        return value.isPresent() ? value.getAsDouble() : null;
    }
}
