package dev.edudio.createadvancedtrains.debug.notchtest;

/**
 * 仕様書に独立した型契約がないため、現在の利用箇所から推定した不変データを保持します。
 * @param sequenceInServerTick 仕様書に個別説明がないため、{@code sequenceInServerTick}が示すtick数またはserver tick値。
 * @param preCatTargetSpeedBlocksPerSecond 仕様書に個別説明がないため、{@code preCatTargetSpeedBlocksPerSecond}が示す速度。単位はblocks/s。
 * @param nativeTargetSpeedBlocksPerSecond Create由来の目標速度。単位はblocks/s。
 * @param finalTargetSpeedBlocksPerSecond 仕様書に個別説明がないため、{@code finalTargetSpeedBlocksPerSecond}が示す速度。単位はblocks/s。
 * @param brakingDemand 仕様書に個別説明がないため、現在の処理内容から推定した、{@code brakingDemand}として使用される入力値。
 * @param applicationReason 仕様書に個別説明がないため、{@code applicationReason}が示す理由または診断情報。
 * @param originalAccelerationMod CATが変更する前のCreate加速度倍率。
 * @param returnedAccelerationMod 最終的にCreateへ返す加速度倍率。
 * @param notchModifierApplied 仕様書に個別説明がないため、現在の処理内容から推定した、{@code notchModifierApplied}として使用される入力値。
 * @param stopTargetHoldState 仕様書に個別説明がないため、{@code stopTargetHoldState}が示す現在または判定後の状態。
 * @param stopTargetHoldOverrideApplied 仕様書に個別説明がないため、現在の処理内容から推定した、{@code stopTargetHoldOverrideApplied}として使用される入力値。
 * @param stopTargetHoldTriggerReason 仕様書に個別説明がないため、{@code stopTargetHoldTriggerReason}が示す理由または診断情報。
 */
record NotchTestApproachCall(
        int sequenceInServerTick,
        Double preCatTargetSpeedBlocksPerSecond,
        Double nativeTargetSpeedBlocksPerSecond,
        Double finalTargetSpeedBlocksPerSecond,
        boolean brakingDemand,
        String applicationReason,
        Float originalAccelerationMod,
        Float returnedAccelerationMod,
        boolean notchModifierApplied,
        StopTargetHoldResult.State stopTargetHoldState,
        boolean stopTargetHoldOverrideApplied,
        String stopTargetHoldTriggerReason) {
}
