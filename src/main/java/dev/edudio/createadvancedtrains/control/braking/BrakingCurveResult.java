package dev.edudio.createadvancedtrains.control.braking;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Pure calculation output. It contains no target-speed, notch, EB, or world
 * mutation command.
 * @param status 仕様書に個別説明がないため、{@code status}が示す現在または判定後の状態。
 * @param maximumPermittedSpeedBlocksPerSecond 仕様書に個別説明がないため、{@code maximumPermittedSpeedBlocksPerSecond}が示す速度。単位はblocks/s。
 * @param predictedStoppingDistanceBlocks 仕様書に個別説明がないため、{@code predictedStoppingDistanceBlocks}が示す距離または位置量。単位はblocks。
 * @param usableDistanceBlocks 仕様書に個別説明がないため、{@code usableDistanceBlocks}が示す距離または位置量。単位はblocks。
 * @param predictedOvershootBlocks 仕様書に個別説明がないため、{@code predictedOvershootBlocks}が示す距離または位置量。単位はblocks。
 * @param failure 仕様書に個別説明がないため、現在の処理内容から推定した、{@code failure}として使用される入力値。
 */
public record BrakingCurveResult(
        BrakingCurveStatus status,
        OptionalDouble maximumPermittedSpeedBlocksPerSecond,
        OptionalDouble predictedStoppingDistanceBlocks,
        OptionalDouble usableDistanceBlocks,
        OptionalDouble predictedOvershootBlocks,
        BrakingCurveFailure failure) {

    /**
     * 仕様書に独立したコンストラクタ契約がないため、現在の処理内容から推定してレコード構成値を検証し、初期化します。
     * @param status 仕様書に個別説明がないため、{@code status}が示す現在または判定後の状態。
     * @param maximumPermittedSpeedBlocksPerSecond 仕様書に個別説明がないため、{@code maximumPermittedSpeedBlocksPerSecond}が示す速度。単位はblocks/s。
     * @param predictedStoppingDistanceBlocks 仕様書に個別説明がないため、{@code predictedStoppingDistanceBlocks}が示す距離または位置量。単位はblocks。
     * @param usableDistanceBlocks 仕様書に個別説明がないため、{@code usableDistanceBlocks}が示す距離または位置量。単位はblocks。
     * @param predictedOvershootBlocks 仕様書に個別説明がないため、{@code predictedOvershootBlocks}が示す距離または位置量。単位はblocks。
     * @param failure 仕様書に個別説明がないため、現在の処理内容から推定した、{@code failure}として使用される入力値。
     */
    public BrakingCurveResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(maximumPermittedSpeedBlocksPerSecond, "maximumPermittedSpeedBlocksPerSecond");
        Objects.requireNonNull(predictedStoppingDistanceBlocks, "predictedStoppingDistanceBlocks");
        Objects.requireNonNull(usableDistanceBlocks, "usableDistanceBlocks");
        Objects.requireNonNull(predictedOvershootBlocks, "predictedOvershootBlocks");
        Objects.requireNonNull(failure, "failure");
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code noForwardStopTarget}が示す状態の結果オブジェクトを生成します。
     * @return 処理によって得られた結果。
     */
    public static BrakingCurveResult noForwardStopTarget() {
        return new BrakingCurveResult(
                BrakingCurveStatus.NO_FORWARD_STOP_TARGET,
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                BrakingCurveFailure.NONE);
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code invalid}が示す状態の結果オブジェクトを生成します。
     * @param failure 仕様書に個別説明がないため、現在の処理内容から推定した、{@code failure}として使用される入力値。
     * @return 処理によって得られた結果。
     */
    public static BrakingCurveResult invalid(BrakingCurveFailure failure) {
        if (failure == BrakingCurveFailure.NONE) {
            throw new IllegalArgumentException("An invalid result needs a failure reason");
        }
        BrakingCurveStatus status = failure == BrakingCurveFailure.ITERATION_LIMIT_REACHED
                ? BrakingCurveStatus.ITERATION_LIMIT_REACHED
                : BrakingCurveStatus.INVALID_INPUT;
        return new BrakingCurveResult(
                status,
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                failure);
    }

    /**
     * CalculationErrorに関する条件を判定します。
     * @return 条件を満たす場合はtrue、それ以外はfalse。
     */
    public boolean isCalculationError() {
        return status.isCalculationError();
    }
}
