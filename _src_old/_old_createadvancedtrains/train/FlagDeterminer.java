package dev.edudio.createadvancedtrains.train;

import java.util.Objects;
import java.util.UUID;

import dev.edudio.createadvancedtrains.train.query.NormalizedNavigationStop;

/**
 * Per-train owner of shared, read-only operational flag derivation.
 */
public final class FlagDeterminer {

    private final UUID trainId;
    private TrainOperationalFlags currentFlags;

    /**
     * このクラスのインスタンスを初期化します。
     * @param trainId 対象列車を識別するUUID。
     */
    public FlagDeterminer(UUID trainId) {
        this.trainId = Objects.requireNonNull(trainId, "trainId");
        currentFlags = TrainOperationalFlags.fromNavigationState(
                dev.edudio.createadvancedtrains.train.query.NavigationStopState.NO_ACTIVE_DESTINATION);
    }

    /**
     * 現在の入力に基づいて保持状態を更新します。
     * @param observedTrainId 仕様書に個別説明がないため、{@code observedTrainId}が示す対象識別子。
     * @param navigationStop 仕様書に個別説明がないため、{@code navigationStop}が示すNavigationまたは目的地情報。
     * @return 処理によって得られた結果。
     */
    public TrainOperationalFlags update(
            UUID observedTrainId,
            NormalizedNavigationStop navigationStop) {
        if (!trainId.equals(Objects.requireNonNull(observedTrainId, "observedTrainId"))) {
            throw new IllegalArgumentException("FlagDeterminer cannot observe a different train UUID");
        }
        Objects.requireNonNull(navigationStop, "navigationStop");
        currentFlags = TrainOperationalFlags.fromNavigationState(navigationStop.state());
        return currentFlags;
    }

    /**
     * 仕様書に独立した関数契約がないため、現在保持している{@code current flags}を返します。
     * @return 処理によって得られた結果。
     */
    public TrainOperationalFlags currentFlags() {
        return currentFlags;
    }
}
