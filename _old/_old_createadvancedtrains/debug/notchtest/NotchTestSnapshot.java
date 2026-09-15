package dev.edudio.createadvancedtrains.debug.notchtest;

import java.util.List;
import java.util.UUID;

import dev.edudio.createadvancedtrains.control.notch.Notch;

/**
 * 仕様書に独立した型契約がないため、現在の利用箇所から推定した不変データを保持します。
 * @param serverTick 処理対象となるserver tick。
 * @param trainId 対象列車を識別するUUID。
 * @param fixedTestNotch 仕様書に個別説明がないため、{@code fixedTestNotch}が示すノッチ状態またはノッチ候補。
 * @param commandedNotch 仕様書に個別説明がないため、{@code commandedNotch}が示すノッチ状態またはノッチ候補。
 * @param appliedNotch 仕様書に個別説明がないため、{@code appliedNotch}が示すノッチ状態またはノッチ候補。
 * @param transitionElapsedTicks 仕様書に個別説明がないため、{@code transitionElapsedTicks}が示すtick数またはserver tick値。
 * @param transitionProgress 仕様書に個別説明がないため、現在の処理内容から推定した、{@code transitionProgress}として使用される入力値。
 * @param currentSpeedBlocksPerSecond 現在速度の大きさ。単位はblocks/s。
 * @param preCatTargetSpeedBlocksPerSecond 仕様書に個別説明がないため、{@code preCatTargetSpeedBlocksPerSecond}が示す速度。単位はblocks/s。
 * @param finalTargetSpeedBlocksPerSecond 仕様書に個別説明がないため、{@code finalTargetSpeedBlocksPerSecond}が示す速度。単位はblocks/s。
 * @param baseAccelerationBlocksPerSecondSquared Create基本加速度の大きさ。単位はblocks/s^2。
 * @param profileTargetAccelerationBlocksPerSecondSquared 仕様書に個別説明がないため、{@code profileTargetAccelerationBlocksPerSecondSquared}が示す加速度。単位はblocks/s^2。
 * @param effectiveAccelerationBlocksPerSecondSquared 仕様書に個別説明がないため、{@code effectiveAccelerationBlocksPerSecondSquared}が示す加速度。単位はblocks/s^2。
 * @param measuredAccelerationBlocksPerSecondSquared 仕様書に個別説明がないため、{@code measuredAccelerationBlocksPerSecondSquared}が示す加速度。単位はblocks/s^2。
 * @param signedVelocityAccelerationBlocksPerSecondSquared 仕様書に個別説明がないため、{@code signedVelocityAccelerationBlocksPerSecondSquared}が示す加速度。単位はblocks/s^2。
 * @param applicationState 仕様書に個別説明がないため、{@code applicationState}が示す現在または判定後の状態。
 * @param applicationReason 仕様書に個別説明がないため、{@code applicationReason}が示す理由または診断情報。
 * @param controlApplied 仕様書に個別説明がないため、{@code controlApplied}が示す条件の有効・無効を表す値。
 * @param originalAccelerationMod CATが変更する前のCreate加速度倍率。
 * @param appliedAccelerationMod 仕様書に個別説明がないため、{@code appliedAccelerationMod}が示す加速度または加速度倍率。単位は呼出元の境界定義に従います。
 * @param approachCallCount 仕様書に個別説明がないため、現在の処理内容から推定した、{@code approachCallCount}として使用される入力値。
 * @param brakingDemandCallCount 仕様書に個別説明がないため、現在の処理内容から推定した、{@code brakingDemandCallCount}として使用される入力値。
 * @param notchModifierAppliedCallCount 仕様書に個別説明がないため、現在の処理内容から推定した、{@code notchModifierAppliedCallCount}として使用される入力値。
 * @param approachCalls 仕様書に個別説明がないため、現在の処理内容から推定した、{@code approachCalls}として使用される入力値。
 * @param responseState 仕様書に個別説明がないため、{@code responseState}が示す現在または判定後の状態。
 * @param distanceToDestinationBlocks 仕様書に個別説明がないため、{@code distanceToDestinationBlocks}が示す距離または位置量。単位はblocks。
 */
record NotchTestSnapshot(
        long serverTick,
        UUID trainId,
        Notch fixedTestNotch,
        Notch commandedNotch,
        Notch appliedNotch,
        int transitionElapsedTicks,
        double transitionProgress,
        Double currentSpeedBlocksPerSecond,
        Double preCatTargetSpeedBlocksPerSecond,
        Double finalTargetSpeedBlocksPerSecond,
        Double baseAccelerationBlocksPerSecondSquared,
        Double profileTargetAccelerationBlocksPerSecondSquared,
        double effectiveAccelerationBlocksPerSecondSquared,
        Double measuredAccelerationBlocksPerSecondSquared,
        Double signedVelocityAccelerationBlocksPerSecondSquared,
        String applicationState,
        String applicationReason,
        boolean controlApplied,
        Float originalAccelerationMod,
        Float appliedAccelerationMod,
        int approachCallCount,
        int brakingDemandCallCount,
        int notchModifierAppliedCallCount,
        List<NotchTestApproachCall> approachCalls,
        NotchTestResponseState responseState,
        Double distanceToDestinationBlocks) {
}
