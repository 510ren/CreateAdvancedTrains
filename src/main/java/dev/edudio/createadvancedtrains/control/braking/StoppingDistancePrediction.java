package dev.edudio.createadvancedtrains.control.braking;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Result of the response-aware B7 forward integration.
 * @param stoppingDistanceBlocks 仕様書に個別説明がないため、{@code stoppingDistanceBlocks}が示す距離または位置量。単位はblocks。
 * @param simulatedTicks 仕様書に個別説明がないため、{@code simulatedTicks}が示すtick数またはserver tick値。
 * @param failure 仕様書に個別説明がないため、現在の処理内容から推定した、{@code failure}として使用される入力値。
 */
public record StoppingDistancePrediction(
        OptionalDouble stoppingDistanceBlocks,
        int simulatedTicks,
        BrakingCurveFailure failure) {

    /**
     * 仕様書に独立したコンストラクタ契約がないため、現在の処理内容から推定してレコード構成値を検証し、初期化します。
     * @param stoppingDistanceBlocks 仕様書に個別説明がないため、{@code stoppingDistanceBlocks}が示す距離または位置量。単位はblocks。
     * @param simulatedTicks 仕様書に個別説明がないため、{@code simulatedTicks}が示すtick数またはserver tick値。
     * @param failure 仕様書に個別説明がないため、現在の処理内容から推定した、{@code failure}として使用される入力値。
     */
    public StoppingDistancePrediction {
        Objects.requireNonNull(stoppingDistanceBlocks, "stoppingDistanceBlocks");
        Objects.requireNonNull(failure, "failure");
        if (simulatedTicks < 0) {
            throw new IllegalArgumentException("simulatedTicks must not be negative");
        }
        if (stoppingDistanceBlocks.isPresent() != (failure == BrakingCurveFailure.NONE)) {
            throw new IllegalArgumentException("A successful prediction must have a distance and no failure");
        }
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code success}が示す状態の結果オブジェクトを生成します。
     * @param distanceBlocks 仕様書に個別説明がないため、{@code distanceBlocks}が示す距離または位置量。単位はblocks。
     * @param simulatedTicks 仕様書に個別説明がないため、{@code simulatedTicks}が示すtick数またはserver tick値。
     * @return 処理によって得られた結果。
     */
    public static StoppingDistancePrediction success(double distanceBlocks, int simulatedTicks) {
        return new StoppingDistancePrediction(
                OptionalDouble.of(distanceBlocks),
                simulatedTicks,
                BrakingCurveFailure.NONE);
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code failure}が示す状態の結果オブジェクトを生成します。
     * @param failure 仕様書に個別説明がないため、現在の処理内容から推定した、{@code failure}として使用される入力値。
     * @param simulatedTicks 仕様書に個別説明がないため、{@code simulatedTicks}が示すtick数またはserver tick値。
     * @return 処理によって得られた結果。
     */
    public static StoppingDistancePrediction failure(BrakingCurveFailure failure, int simulatedTicks) {
        if (failure == BrakingCurveFailure.NONE) {
            throw new IllegalArgumentException("A failed prediction needs a failure reason");
        }
        return new StoppingDistancePrediction(OptionalDouble.empty(), simulatedTicks, failure);
    }

    /**
     * Successfulに関する条件を判定します。
     * @return 条件を満たす場合はtrue、それ以外はfalse。
     */
    public boolean isSuccessful() {
        return failure == BrakingCurveFailure.NONE;
    }
}
