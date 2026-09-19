package dev.edudio.createadvancedtrains.train;

import com.simibubi.create.content.trains.entity.Train;

import dev.edudio.createadvancedtrains.speed.SpeedLimitSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TrainControllerManager {
    public static final TrainControllerManager INSTANCE = new TrainControllerManager();

    private final Map<UUID, TrainController> controllers = new HashMap<>();
    private long currentServerTick = Long.MIN_VALUE;

    /**
     * 現在のOrCreateを返します。
     * @param train 対象となるCreate列車。
     * @return 処理によって得られた結果。
     */
    public TrainController getOrCreate(Train train) {
        return controllers.computeIfAbsent(
                train.id,
                id -> new TrainController(train));
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の処理内容から推定すると、{@code get}に対応する処理を実行します。
     * @param trainId 対象列車を識別するUUID。
     * @return 処理によって得られた結果。
     */
    public TrainController get(UUID trainId) {
        return controllers.get(trainId);
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の処理内容から推定すると、{@code remove}に対応する処理を実行します。
     * @param trainId 対象列車を識別するUUID。
     */
    public void remove(UUID trainId) {
        controllers.remove(trainId);
    }

    /**
     * 保持している状態を消去します。
     */
    public void clear() {
        controllers.clear();
        currentServerTick = Long.MIN_VALUE;
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の処理内容から推定すると、{@code size}に対応する処理を実行します。
     * @return 処理または計算によって得られた数値。
     */
    public int size() {
        return controllers.size();
    }

    /**
     * 現在の入力に基づいて保持状態を更新します。
     * @param train 対象となるCreate列車。
     */
    public void update(Train train) {
        TrainController controller = getOrCreate(train);
        controller.update(train);
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の処理内容から推定すると、{@code updateAll}に対応する処理を実行します。
     * @param trains 仕様書に個別説明がないため処理内容から推定した、{@code trains}に対応する入力値。
     */
    public void updateAll(Iterable<Train> trains) {
        for (Train train : trains) {
            update(train);
        }
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の処理内容から推定すると、{@code refreshSharedState}に対応する処理を実行します。
     * @param train 対象となるCreate列車。
     */
    public void refreshSharedState(Train train) {
        getOrCreate(train).update(train);
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の処理内容から推定すると、{@code beginServerTick}に対応する処理を実行します。
     * @param serverTick 処理対象となるserver tick。
     */
    public void beginServerTick(long serverTick) {
        currentServerTick = serverTick;
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の処理内容から推定すると、{@code currentServerTick}に対応する処理を実行します。
     * @return 処理または計算によって得られた数値。
     */
    public long currentServerTick() {
        return currentServerTick;
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の処理内容から推定すると、{@code refreshAllSharedState}に対応する処理を実行します。
     * @param trains 仕様書に個別説明がないため処理内容から推定した、{@code trains}に対応する入力値。
     */
    public void refreshAllSharedState(Iterable<Train> trains) {
        for (Train train : trains) {
            refreshSharedState(train);
        }
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の処理内容から推定すると、{@code removeMissing}に対応する処理を実行します。
     * @param trains 仕様書に個別説明がないため処理内容から推定した、{@code trains}に対応する入力値。
     */
    public void removeMissing(Iterable<Train> trains) {
        Map<UUID, Boolean> existingTrains = new HashMap<>();

        for (Train train : trains) {
            existingTrains.put(train.id, Boolean.TRUE);
        }

        controllers.keySet().removeIf(
                trainId -> !existingTrains.containsKey(trainId));
    }

    /**
     * SpeedLimitForAllを設定します。
     * @param source 速度制限などの値の供給元。
     * @param speedLimit 適用する速度上限。
     */
    public void setSpeedLimitForAll(
            SpeedLimitSource source,
            double speedLimit) {
        for (TrainController controller : controllers.values()) {
            controller.setSpeedLimit(
                    source,
                    speedLimit);
        }
    }
}
