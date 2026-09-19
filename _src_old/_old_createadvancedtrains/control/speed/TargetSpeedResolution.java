package dev.edudio.createadvancedtrains.control.speed;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * 仕様書に独立した型契約がないため、現在の利用箇所から推定した不変データを保持します。
 * @param finalMagnitudeBlocksPerSecond 仕様書に個別説明がないため、{@code finalMagnitudeBlocksPerSecond}が示す速度。単位はblocks/s。
 * @param signedFinalSpeedBlocksPerSecond 仕様書に個別説明がないため、{@code signedFinalSpeedBlocksPerSecond}が示す速度。単位はblocks/s。
 * @param adoptedSources 仕様書に個別説明がないため、{@code adoptedSources}が示す値または処理の供給元。
 * @param candidates 仕様書に個別説明がないため、{@code candidates}が示す条件の有効・無効を表す値。
 * @param nativeZeroClassification 仕様書に個別説明がないため、現在の処理内容から推定した、{@code nativeZeroClassification}として使用される入力値。
 * @param diagnostic 仕様書に個別説明がないため、{@code diagnostic}が示す理由または診断情報。
 */
public record TargetSpeedResolution(
        OptionalDouble finalMagnitudeBlocksPerSecond,
        OptionalDouble signedFinalSpeedBlocksPerSecond,
        Set<TargetSpeedCandidateSource> adoptedSources,
        List<TargetSpeedCandidate> candidates,
        NativeTargetZeroClassification nativeZeroClassification,
        String diagnostic) {

    /**
     * 仕様書に独立したコンストラクタ契約がないため、現在の処理内容から推定してレコード構成値を検証し、初期化します。
     * @param finalMagnitudeBlocksPerSecond 仕様書に個別説明がないため、{@code finalMagnitudeBlocksPerSecond}が示す速度。単位はblocks/s。
     * @param signedFinalSpeedBlocksPerSecond 仕様書に個別説明がないため、{@code signedFinalSpeedBlocksPerSecond}が示す速度。単位はblocks/s。
     * @param adoptedSources 仕様書に個別説明がないため、{@code adoptedSources}が示す値または処理の供給元。
     * @param candidates 仕様書に個別説明がないため、{@code candidates}が示す条件の有効・無効を表す値。
     * @param nativeZeroClassification 仕様書に個別説明がないため、現在の処理内容から推定した、{@code nativeZeroClassification}として使用される入力値。
     * @param diagnostic 仕様書に個別説明がないため、{@code diagnostic}が示す理由または診断情報。
     */
    public TargetSpeedResolution {
        Objects.requireNonNull(finalMagnitudeBlocksPerSecond, "finalMagnitudeBlocksPerSecond");
        Objects.requireNonNull(signedFinalSpeedBlocksPerSecond, "signedFinalSpeedBlocksPerSecond");
        adoptedSources = Set.copyOf(adoptedSources);
        candidates = List.copyOf(candidates);
        Objects.requireNonNull(nativeZeroClassification, "nativeZeroClassification");
        Objects.requireNonNull(diagnostic, "diagnostic");
    }
}
