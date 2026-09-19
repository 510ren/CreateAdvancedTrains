package dev.edudio.createadvancedtrains.debug.hud;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Observes pre-ATO and post-ATO target speeds at the existing Train mixin boundary.
 */
public final class TrainTargetSpeedTracker {

    public static final TrainTargetSpeedTracker INSTANCE = new TrainTargetSpeedTracker();

    private final Map<UUID, TargetSpeeds> targetSpeedsByTrain = new HashMap<>();

    /**
     * このクラスのインスタンスを初期化します。
     */
    private TrainTargetSpeedTracker() {
    }

    /**
     * 仕様書に独立した関数契約がないため、現在値を{@code observe}が示す観測状態へ記録します。
     * @param trainId 対象列車を識別するUUID。
     * @param createTargetSpeedBlocksPerTick 仕様書に個別説明がないため、{@code createTargetSpeedBlocksPerTick}が示すCreate境界の速度。単位はblocks/tick。
     * @param atoTargetSpeedBlocksPerTick 仕様書に個別説明がないため、{@code atoTargetSpeedBlocksPerTick}が示すCreate境界の速度。単位はblocks/tick。
     */
    public void observe(
            UUID trainId,
            double createTargetSpeedBlocksPerTick,
            double atoTargetSpeedBlocksPerTick) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) {
            return;
        }

        targetSpeedsByTrain.put(
                trainId,
                new TargetSpeeds(
                        createTargetSpeedBlocksPerTick,
                        atoTargetSpeedBlocksPerTick));
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code get}としてまとめられている処理を実行します。
     * @param trainId 対象列車を識別するUUID。
     * @return 処理によって得られた結果。
     */
    TargetSpeeds get(UUID trainId) {
        return targetSpeedsByTrain.get(trainId);
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code retainAll}としてまとめられている処理を実行します。
     * @param trainIds 仕様書に個別説明がないため、{@code trainIds}が示す対象識別子。
     */
    void retainAll(Set<UUID> trainIds) {
        targetSpeedsByTrain.keySet().removeIf(trainId -> !trainIds.contains(trainId));
    }

    /**
     * 保持している状態を消去します。
     */
    void clear() {
        targetSpeedsByTrain.clear();
    }

    /**
     * 仕様書に独立した型契約がないため、現在の利用箇所から推定した不変データを保持します。
     * @param createTargetSpeedBlocksPerTick 仕様書に個別説明がないため、{@code createTargetSpeedBlocksPerTick}が示すCreate境界の速度。単位はblocks/tick。
     * @param atoTargetSpeedBlocksPerTick 仕様書に個別説明がないため、{@code atoTargetSpeedBlocksPerTick}が示すCreate境界の速度。単位はblocks/tick。
     */
    record TargetSpeeds(
            double createTargetSpeedBlocksPerTick,
            double atoTargetSpeedBlocksPerTick) {
    }
}
