package dev.edudio.createadvancedtrains.control.speed;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * One immutable blocks/s diagnostic candidate considered by the resolver.
 * @param source 速度制限などの値の供給元。
 * @param magnitudeBlocksPerSecond 仕様書に個別説明がないため、{@code magnitudeBlocksPerSecond}が示す速度。単位はblocks/s。
 * @param included 仕様書に個別説明がないため、{@code included}が示す条件の有効・無効を表す値。
 * @param diagnostic 仕様書に個別説明がないため、{@code diagnostic}が示す理由または診断情報。
 */
public record TargetSpeedCandidate(
        TargetSpeedCandidateSource source,
        OptionalDouble magnitudeBlocksPerSecond,
        boolean included,
        String diagnostic) {

    /**
     * 仕様書に独立したコンストラクタ契約がないため、現在の処理内容から推定してレコード構成値を検証し、初期化します。
     * @param source 速度制限などの値の供給元。
     * @param magnitudeBlocksPerSecond 仕様書に個別説明がないため、{@code magnitudeBlocksPerSecond}が示す速度。単位はblocks/s。
     * @param included 仕様書に個別説明がないため、{@code included}が示す条件の有効・無効を表す値。
     * @param diagnostic 仕様書に個別説明がないため、{@code diagnostic}が示す理由または診断情報。
     */
    public TargetSpeedCandidate {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(magnitudeBlocksPerSecond, "magnitudeBlocksPerSecond");
        Objects.requireNonNull(diagnostic, "diagnostic");
        if (magnitudeBlocksPerSecond.isPresent()) {
            double value = magnitudeBlocksPerSecond.getAsDouble();
            if (!Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException("Candidate magnitude must be finite and non-negative");
            }
        }
    }
}
