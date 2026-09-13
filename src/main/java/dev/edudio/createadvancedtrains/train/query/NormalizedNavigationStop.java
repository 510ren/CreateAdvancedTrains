package dev.edudio.createadvancedtrains.train.query;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Immutable, read-only Create Navigation observation in CAT public units.
 * Forward distance is present only for the AHEAD state.
 * @param state 仕様書に個別説明がないため、{@code state}が示す現在または判定後の状態。
 * @param direction 仕様書に個別説明がないため、現在の処理内容から推定した、{@code direction}として使用される入力値。
 * @param forwardDistanceBlocks 仕様書に個別説明がないため、{@code forwardDistanceBlocks}が示す距離または位置量。単位はblocks。
 * @param observedCreateDistanceBlocks 仕様書に個別説明がないため、{@code observedCreateDistanceBlocks}が示す距離または位置量。単位はblocks。
 * @param invalidReason 仕様書に個別説明がないため、{@code invalidReason}が示す理由または診断情報。
 */
public record NormalizedNavigationStop(
        NavigationStopState state,
        NavigationTravelDirection direction,
        OptionalDouble forwardDistanceBlocks,
        OptionalDouble observedCreateDistanceBlocks,
        NavigationStopInvalidReason invalidReason) {

    /**
     * 仕様書に独立したコンストラクタ契約がないため、現在の処理内容から推定してレコード構成値を検証し、初期化します。
     * @param state 仕様書に個別説明がないため、{@code state}が示す現在または判定後の状態。
     * @param direction 仕様書に個別説明がないため、現在の処理内容から推定した、{@code direction}として使用される入力値。
     * @param forwardDistanceBlocks 仕様書に個別説明がないため、{@code forwardDistanceBlocks}が示す距離または位置量。単位はblocks。
     * @param observedCreateDistanceBlocks 仕様書に個別説明がないため、{@code observedCreateDistanceBlocks}が示す距離または位置量。単位はblocks。
     * @param invalidReason 仕様書に個別説明がないため、{@code invalidReason}が示す理由または診断情報。
     */
    public NormalizedNavigationStop {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(forwardDistanceBlocks, "forwardDistanceBlocks");
        Objects.requireNonNull(observedCreateDistanceBlocks, "observedCreateDistanceBlocks");
        Objects.requireNonNull(invalidReason, "invalidReason");

        if (forwardDistanceBlocks.isPresent()) {
            double distance = forwardDistanceBlocks.getAsDouble();
            if (state != NavigationStopState.AHEAD || !Double.isFinite(distance) || distance <= 0.0) {
                throw new IllegalArgumentException("Only AHEAD may expose a finite positive forward distance");
            }
        } else if (state == NavigationStopState.AHEAD) {
            throw new IllegalArgumentException("AHEAD requires a forward distance");
        }

        if (observedCreateDistanceBlocks.isPresent()
                && !Double.isFinite(observedCreateDistanceBlocks.getAsDouble())) {
            throw new IllegalArgumentException("Observed Create distance must be finite when present");
        }
        if ((state == NavigationStopState.INVALID) != (invalidReason != NavigationStopInvalidReason.NONE)) {
            throw new IllegalArgumentException("Only INVALID may have an invalid reason");
        }
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code noActiveDestination}が示す状態の結果オブジェクトを生成します。
     * @return 処理によって得られた結果。
     */
    public static NormalizedNavigationStop noActiveDestination() {
        return new NormalizedNavigationStop(
                NavigationStopState.NO_ACTIVE_DESTINATION,
                NavigationTravelDirection.UNKNOWN,
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                NavigationStopInvalidReason.NONE);
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code ahead}が示す状態の結果オブジェクトを生成します。
     * @param distanceBlocks 仕様書に個別説明がないため、{@code distanceBlocks}が示す距離または位置量。単位はblocks。
     * @param direction 仕様書に個別説明がないため、現在の処理内容から推定した、{@code direction}として使用される入力値。
     * @return 処理によって得られた結果。
     */
    public static NormalizedNavigationStop ahead(
            double distanceBlocks,
            NavigationTravelDirection direction) {
        return new NormalizedNavigationStop(
                NavigationStopState.AHEAD,
                direction,
                OptionalDouble.of(distanceBlocks),
                OptionalDouble.of(distanceBlocks),
                NavigationStopInvalidReason.NONE);
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code arrivalPending}が示す状態の結果オブジェクトを生成します。
     * @param direction 仕様書に個別説明がないため、現在の処理内容から推定した、{@code direction}として使用される入力値。
     * @return 処理によって得られた結果。
     */
    public static NormalizedNavigationStop arrivalPending(NavigationTravelDirection direction) {
        return new NormalizedNavigationStop(
                NavigationStopState.AT_DESTINATION_OR_ARRIVAL_PENDING,
                direction,
                OptionalDouble.empty(),
                OptionalDouble.of(0.0),
                NavigationStopInvalidReason.NONE);
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code pastDestination}が示す状態の結果オブジェクトを生成します。
     * @param observedDistanceBlocks 仕様書に個別説明がないため、{@code observedDistanceBlocks}が示す距離または位置量。単位はblocks。
     * @param direction 仕様書に個別説明がないため、現在の処理内容から推定した、{@code direction}として使用される入力値。
     * @return 処理によって得られた結果。
     */
    public static NormalizedNavigationStop pastDestination(
            double observedDistanceBlocks,
            NavigationTravelDirection direction) {
        return new NormalizedNavigationStop(
                NavigationStopState.PAST_DESTINATION_OR_INVALID_STATE,
                direction,
                OptionalDouble.empty(),
                OptionalDouble.of(observedDistanceBlocks),
                NavigationStopInvalidReason.NONE);
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code invalid}が示す状態の結果オブジェクトを生成します。
     * @param reason 処理を行う理由を表す文字列。
     * @return 処理によって得られた結果。
     */
    public static NormalizedNavigationStop invalid(NavigationStopInvalidReason reason) {
        if (reason == NavigationStopInvalidReason.NONE) {
            throw new IllegalArgumentException("INVALID requires an invalid reason");
        }
        return new NormalizedNavigationStop(
                NavigationStopState.INVALID,
                NavigationTravelDirection.UNKNOWN,
                OptionalDouble.empty(),
                OptionalDouble.empty(),
                reason);
    }
}
