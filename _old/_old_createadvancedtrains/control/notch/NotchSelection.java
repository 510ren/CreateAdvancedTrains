package dev.edudio.createadvancedtrains.control.notch;

import java.util.Objects;

/**
 * 仕様書に独立した型契約がないため、現在の利用箇所から推定した不変データを保持します。
 * @param requestedNotch 仕様書に個別説明がないため、{@code requestedNotch}が示すノッチ状態またはノッチ候補。
 * @param serviceBrakeInsufficient 仕様書に個別説明がないため、現在の処理内容から推定した、{@code serviceBrakeInsufficient}として使用される入力値。
 * @param predictedSpeedBlocksPerSecond 仕様書に個別説明がないため、{@code predictedSpeedBlocksPerSecond}が示す速度。単位はblocks/s。
 * @param predictionLimitBlocksPerSecond 仕様書に個別説明がないため、{@code predictionLimitBlocksPerSecond}が示す速度。単位はblocks/s。
 */
public record NotchSelection(
        Notch requestedNotch,
        boolean serviceBrakeInsufficient,
        double predictedSpeedBlocksPerSecond,
        double predictionLimitBlocksPerSecond) {

    /**
     * 仕様書に独立したコンストラクタ契約がないため、現在の処理内容から推定してレコード構成値を検証し、初期化します。
     * @param requestedNotch 仕様書に個別説明がないため、{@code requestedNotch}が示すノッチ状態またはノッチ候補。
     * @param serviceBrakeInsufficient 仕様書に個別説明がないため、現在の処理内容から推定した、{@code serviceBrakeInsufficient}として使用される入力値。
     * @param predictedSpeedBlocksPerSecond 仕様書に個別説明がないため、{@code predictedSpeedBlocksPerSecond}が示す速度。単位はblocks/s。
     * @param predictionLimitBlocksPerSecond 仕様書に個別説明がないため、{@code predictionLimitBlocksPerSecond}が示す速度。単位はblocks/s。
     */
    public NotchSelection {
        Objects.requireNonNull(requestedNotch, "requestedNotch");
        if (!Double.isFinite(predictedSpeedBlocksPerSecond)
                || !Double.isFinite(predictionLimitBlocksPerSecond)) {
            throw new IllegalArgumentException("Prediction values must be finite");
        }
    }
}
