package dev.edudio.createadvancedtrains.debug.hud;

import java.util.Objects;
import java.util.UUID;

import dev.edudio.createadvancedtrains.control.notch.Notch;
import dev.edudio.createadvancedtrains.train.query.StopTargetDistance;

/**
 * One server-observed train row for the development HUD, in CAT units.
 * @param trainId 対象列車を識別するUUID。
 * @param speedBlocksPerSecond 仕様書に個別説明がないため、{@code speedBlocksPerSecond}が示す速度。単位はblocks/s。
 * @param currentNotch 仕様書に個別説明がないため、{@code currentNotch}が示すノッチ状態またはノッチ候補。
 * @param createTargetSpeedBlocksPerSecond 仕様書に個別説明がないため、{@code createTargetSpeedBlocksPerSecond}が示す速度。単位はblocks/s。
 * @param atoTargetSpeedBlocksPerSecond 仕様書に個別説明がないため、{@code atoTargetSpeedBlocksPerSecond}が示す速度。単位はblocks/s。
 * @param measuredAccelerationBlocksPerSecondSquared 仕様書に個別説明がないため、{@code measuredAccelerationBlocksPerSecondSquared}が示す加速度。単位はblocks/s^2。
 * @param createBaseAccelerationBlocksPerSecondSquared 仕様書に個別説明がないため、{@code createBaseAccelerationBlocksPerSecondSquared}が示す加速度。単位はblocks/s^2。
 * @param stopTargetDistance 仕様書に個別説明がないため、{@code stopTargetDistance}が示す距離または位置。単位は呼出元の境界定義に従います。
 */
public record TrainStatusHudEntry(
        UUID trainId,
        double speedBlocksPerSecond,
        Notch currentNotch,
        double createTargetSpeedBlocksPerSecond,
        double atoTargetSpeedBlocksPerSecond,
        double measuredAccelerationBlocksPerSecondSquared,
        double createBaseAccelerationBlocksPerSecondSquared,
        StopTargetDistance stopTargetDistance) {

    /**
     * 仕様書に独立したコンストラクタ契約がないため、現在の処理内容から推定してレコード構成値を検証し、初期化します。
     * @param trainId 対象列車を識別するUUID。
     * @param speedBlocksPerSecond 仕様書に個別説明がないため、{@code speedBlocksPerSecond}が示す速度。単位はblocks/s。
     * @param currentNotch 仕様書に個別説明がないため、{@code currentNotch}が示すノッチ状態またはノッチ候補。
     * @param createTargetSpeedBlocksPerSecond 仕様書に個別説明がないため、{@code createTargetSpeedBlocksPerSecond}が示す速度。単位はblocks/s。
     * @param atoTargetSpeedBlocksPerSecond 仕様書に個別説明がないため、{@code atoTargetSpeedBlocksPerSecond}が示す速度。単位はblocks/s。
     * @param measuredAccelerationBlocksPerSecondSquared 仕様書に個別説明がないため、{@code measuredAccelerationBlocksPerSecondSquared}が示す加速度。単位はblocks/s^2。
     * @param createBaseAccelerationBlocksPerSecondSquared 仕様書に個別説明がないため、{@code createBaseAccelerationBlocksPerSecondSquared}が示す加速度。単位はblocks/s^2。
     * @param stopTargetDistance 仕様書に個別説明がないため、{@code stopTargetDistance}が示す距離または位置。単位は呼出元の境界定義に従います。
     */
    public TrainStatusHudEntry {
        Objects.requireNonNull(trainId, "trainId");
        Objects.requireNonNull(currentNotch, "currentNotch");
        Objects.requireNonNull(stopTargetDistance, "stopTargetDistance");
    }
}
