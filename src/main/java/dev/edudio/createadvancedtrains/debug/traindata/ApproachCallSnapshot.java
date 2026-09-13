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

/**
 * Immutable schema-v2 observation of one existing Create approach call.
 * @param trainId 対象列車を識別するUUID。
 * @param serverTick 処理対象となるserver tick。
 * @param callIndexInServerTick 仕様書に個別説明がないため、{@code callIndexInServerTick}が示すtick数またはserver tick値。
 * @param firstApproachCallThisServerTick 仕様書に個別説明がないため、{@code firstApproachCallThisServerTick}が示すtick数またはserver tick値。
 * @param speedBlocksPerSecond 仕様書に個別説明がないため、{@code speedBlocksPerSecond}が示す速度。単位はblocks/s。
 * @param nativeTargetBlocksPerSecond 仕様書に個別説明がないため、{@code nativeTargetBlocksPerSecond}が示す速度。単位はblocks/s。
 * @param finalTargetBlocksPerSecond 仕様書に個別説明がないため、{@code finalTargetBlocksPerSecond}が示す速度。単位はblocks/s。
 * @param navigationState 仕様書に個別説明がないため、{@code navigationState}が示す現在または判定後の状態。
 * @param distanceToDestinationBlocks 仕様書に個別説明がないため、{@code distanceToDestinationBlocks}が示す距離または位置量。単位はblocks。
 * @param nativeZeroClassification 仕様書に個別説明がないため、現在の処理内容から推定した、{@code nativeZeroClassification}として使用される入力値。
 * @param waitingForSignal 仕様書に個別説明がないため、{@code waitingForSignal}が示す条件の有効・無効を表す値。
 * @param manualTick 仕様書に個別説明がないため、{@code manualTick}が示すtick数またはserver tick値。
 * @param brakingCurveStatus 仕様書に個別説明がないため、{@code brakingCurveStatus}が示す現在または判定後の状態。
 * @param brakingCurveLimitBlocksPerSecond 仕様書に個別説明がないため、{@code brakingCurveLimitBlocksPerSecond}が示す速度。単位はblocks/s。
 * @param brakingCurveUsableDistanceBlocks 仕様書に個別説明がないため、{@code brakingCurveUsableDistanceBlocks}が示す距離または位置量。単位はblocks。
 * @param brakingCurvePredictedStoppingDistanceBlocks 仕様書に個別説明がないため、{@code brakingCurvePredictedStoppingDistanceBlocks}が示す距離または位置量。単位はblocks。
 * @param brakingCurvePredictedOvershootBlocks 仕様書に個別説明がないため、{@code brakingCurvePredictedOvershootBlocks}が示す距離または位置量。単位はblocks。
 * @param resolvedLimitBlocksPerSecond 仕様書に個別説明がないため、{@code resolvedLimitBlocksPerSecond}が示す速度。単位はblocks/s。
 * @param safeSpeedBlocksPerSecond 仕様書に個別説明がないため、{@code safeSpeedBlocksPerSecond}が示す速度。単位はblocks/s。
 * @param lowerBandBlocksPerSecond 仕様書に個別説明がないため、{@code lowerBandBlocksPerSecond}が示す速度。単位はblocks/s。
 * @param upperBandBlocksPerSecond 仕様書に個別説明がないため、{@code upperBandBlocksPerSecond}が示す速度。単位はblocks/s。
 * @param brakeLimitBlocksPerSecond 仕様書に個別説明がないため、{@code brakeLimitBlocksPerSecond}が示す速度。単位はblocks/s。
 * @param previousRequestedNotch 仕様書に個別説明がないため、{@code previousRequestedNotch}が示すノッチ状態またはノッチ候補。
 * @param selectedNotch 仕様書に個別説明がないため、{@code selectedNotch}が示すノッチ状態またはノッチ候補。
 * @param selectionPredictionBlocksPerSecond 仕様書に個別説明がないため、{@code selectionPredictionBlocksPerSecond}が示す速度。単位はblocks/s。
 * @param selectionBrakeInsufficient 仕様書に個別説明がないため、{@code selectionBrakeInsufficient}が示す計算・判定・観測結果。
 * @param responseAdvanced 仕様書に個別説明がないため、{@code responseAdvanced}が示す計算・判定・観測結果。
 * @param commandedNotch 仕様書に個別説明がないため、{@code commandedNotch}が示すノッチ状態またはノッチ候補。
 * @param appliedNotch 仕様書に個別説明がないため、{@code appliedNotch}が示すノッチ状態またはノッチ候補。
 * @param transitionElapsedTicks 仕様書に個別説明がないため、{@code transitionElapsedTicks}が示すtick数またはserver tick値。
 * @param transitionProgress 仕様書に個別説明がないため、現在の処理内容から推定した、{@code transitionProgress}として使用される入力値。
 * @param transitionStartAccelerationBlocksPerSecondSquared 仕様書に個別説明がないため、{@code transitionStartAccelerationBlocksPerSecondSquared}が示す加速度。単位はblocks/s^2。
 * @param targetAccelerationBlocksPerSecondSquared 仕様書に個別説明がないため、{@code targetAccelerationBlocksPerSecondSquared}が示す加速度。単位はblocks/s^2。
 * @param effectiveAccelerationBlocksPerSecondSquared 仕様書に個別説明がないため、{@code effectiveAccelerationBlocksPerSecondSquared}が示す加速度。単位はblocks/s^2。
 * @param originalAccelerationMod CATが変更する前のCreate加速度倍率。
 * @param returnedAccelerationMod 最終的にCreateへ返す加速度倍率。
 * @param atoOperatingState 仕様書に個別説明がないため、{@code atoOperatingState}が示す現在または判定後の状態。
 */
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

    /**
     * 現在状態を読み取り専用スナップショットとして取得します。
     * @param train 対象となるCreate列車。
     * @param controller 仕様書に個別説明がないため、{@code controller}が示す条件の有効・無効を表す値。
     * @param serverTick 処理対象となるserver tick。
     * @param callIndexInServerTick 仕様書に個別説明がないため、{@code callIndexInServerTick}が示すtick数またはserver tick値。
     * @param nativeTargetBlocksPerTick Create由来の目標速度。単位はblocks/tick。
     * @param finalTargetBlocksPerTick CAT処理後の最終目標速度。単位はblocks/tick。
     * @param originalAccelerationMod CATが変更する前のCreate加速度倍率。
     * @param returnedAccelerationMod 最終的にCreateへ返す加速度倍率。
     * @return 処理によって得られた結果。
     */
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

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code boxed}としてまとめられている処理を実行します。
     * @param value 処理対象の値。
     * @return 処理または計算によって得られた数値。
     */
    private static Double boxed(OptionalDouble value) {
        return value.isPresent() ? value.getAsDouble() : null;
    }
}
