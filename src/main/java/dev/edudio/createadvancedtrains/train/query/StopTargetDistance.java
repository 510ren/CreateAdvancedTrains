package dev.edudio.createadvancedtrains.train.query;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Immutable result of a read-only stop-target distance query, in blocks.
 * @param distanceBlocks 仕様書に個別説明がないため、{@code distanceBlocks}が示す距離または位置量。単位はblocks。
 * @param source 速度制限などの値の供給元。
 * @param unavailableReason 仕様書に個別説明がないため、{@code unavailableReason}が示す理由または診断情報。
 */
public record StopTargetDistance(
        OptionalDouble distanceBlocks,
        Source source,
        UnavailableReason unavailableReason) {

    /**
     * 仕様書に独立したコンストラクタ契約がないため、現在の処理内容から推定してレコード構成値を検証し、初期化します。
     * @param distanceBlocks 仕様書に個別説明がないため、{@code distanceBlocks}が示す距離または位置量。単位はblocks。
     * @param source 速度制限などの値の供給元。
     * @param unavailableReason 仕様書に個別説明がないため、{@code unavailableReason}が示す理由または診断情報。
     */
    public StopTargetDistance {
        Objects.requireNonNull(distanceBlocks, "distanceBlocks");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(unavailableReason, "unavailableReason");

        if (distanceBlocks.isPresent()) {
            if (!Double.isFinite(distanceBlocks.getAsDouble())) {
                throw new IllegalArgumentException("Available distance must be finite");
            }
            if (source == Source.UNAVAILABLE || unavailableReason != UnavailableReason.NONE) {
                throw new IllegalArgumentException("Available distance cannot have an unavailable source or reason");
            }
        } else if (source != Source.UNAVAILABLE || unavailableReason == UnavailableReason.NONE) {
            throw new IllegalArgumentException("Unavailable distance must have an unavailable reason");
        }
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code available}が示す状態の結果オブジェクトを生成します。
     * @param distanceBlocks 仕様書に個別説明がないため、{@code distanceBlocks}が示す距離または位置量。単位はblocks。
     * @param source 速度制限などの値の供給元。
     * @return 処理によって得られた結果。
     */
    public static StopTargetDistance available(double distanceBlocks, Source source) {
        return new StopTargetDistance(OptionalDouble.of(distanceBlocks), source, UnavailableReason.NONE);
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code unavailable}が示す状態の結果オブジェクトを生成します。
     * @param reason 処理を行う理由を表す文字列。
     * @return 処理によって得られた結果。
     */
    public static StopTargetDistance unavailable(UnavailableReason reason) {
        return new StopTargetDistance(OptionalDouble.empty(), Source.UNAVAILABLE, reason);
    }

    /**
     * Availableに関する条件を判定します。
     * @return 条件を満たす場合はtrue、それ以外はfalse。
     */
    public boolean isAvailable() {
        return distanceBlocks.isPresent();
    }

    public enum Source {
        NAVIGATION,
        MANUAL_CREATE_EQUIVALENT,
        UNAVAILABLE
    }

    public enum UnavailableReason {
        NONE,
        NAVIGATION_UNAVAILABLE,
        NAVIGATION_DISTANCE_NOT_FINITE,
        GRAPH_UNAVAILABLE,
        CARRIAGES_UNAVAILABLE,
        MANUAL_DIRECTION_UNAVAILABLE,
        NO_APPROACHABLE_STATION,
        STATION_DISTANCE_NOT_FOUND
    }
}
