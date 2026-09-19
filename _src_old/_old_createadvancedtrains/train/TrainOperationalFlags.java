package dev.edudio.createadvancedtrains.train;

import java.util.Objects;

import dev.edudio.createadvancedtrains.train.query.NavigationStopState;

/**
 * Immutable shared flags derived centrally from Create observations.
 * @param atDestinationOrArrivalPending 仕様書に個別説明がないため、{@code atDestinationOrArrivalPending}が示すNavigationまたは目的地情報。
 * @param navigationStopState 仕様書に個別説明がないため、{@code navigationStopState}が示す現在または判定後の状態。
 */
public record TrainOperationalFlags(
        boolean atDestinationOrArrivalPending,
        NavigationStopState navigationStopState) {

    /**
     * 仕様書に独立したコンストラクタ契約がないため、現在の処理内容から推定してレコード構成値を検証し、初期化します。
     * @param atDestinationOrArrivalPending 仕様書に個別説明がないため、{@code atDestinationOrArrivalPending}が示すNavigationまたは目的地情報。
     * @param navigationStopState 仕様書に個別説明がないため、{@code navigationStopState}が示す現在または判定後の状態。
     */
    public TrainOperationalFlags {
        Objects.requireNonNull(navigationStopState, "navigationStopState");
        boolean expectedArrivalFlag = navigationStopState
                == NavigationStopState.AT_DESTINATION_OR_ARRIVAL_PENDING;
        if (atDestinationOrArrivalPending != expectedArrivalFlag) {
            throw new IllegalArgumentException("Arrival flag must be derived from Navigation state");
        }
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code fromNavigationState}としてまとめられている処理を実行します。
     * @param state 仕様書に個別説明がないため、{@code state}が示す現在または判定後の状態。
     * @return 処理によって得られた結果。
     */
    public static TrainOperationalFlags fromNavigationState(NavigationStopState state) {
        return new TrainOperationalFlags(
                state == NavigationStopState.AT_DESTINATION_OR_ARRIVAL_PENDING,
                state);
    }
}
