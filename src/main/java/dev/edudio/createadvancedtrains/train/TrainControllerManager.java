package dev.edudio.createadvancedtrains.train;

import com.simibubi.create.content.trains.entity.Train;

import dev.edudio.createadvancedtrains.speed.SpeedLimitSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TrainControllerManager {
    public static final TrainControllerManager INSTANCE = new TrainControllerManager();

    private final Map<UUID, TrainController> controllers = new HashMap<>();

    public TrainController getOrCreate(Train train) {
        return controllers.computeIfAbsent(
                train.id,
                id -> new TrainController(train));
    }

    public TrainController get(UUID trainId) {
        return controllers.get(trainId);
    }

    public void remove(UUID trainId) {
        controllers.remove(trainId);
    }

    public void clear() {
        controllers.clear();
    }

    public int size() {
        return controllers.size();
    }

    public void update(Train train) {
        TrainController controller = getOrCreate(train);
        controller.applyAtoTargetSpeed(train, false);
    }

    public void updateAll(Iterable<Train> trains) {
        for (Train train : trains) {
            update(train);
        }
    }

    public void refreshSharedState(Train train) {
        getOrCreate(train).update(train);
    }

    public void refreshAllSharedState(Iterable<Train> trains) {
        for (Train train : trains) {
            refreshSharedState(train);
        }
    }

    public void removeMissing(Iterable<Train> trains) {
        Map<UUID, Boolean> existingTrains = new HashMap<>();

        for (Train train : trains) {
            existingTrains.put(train.id, Boolean.TRUE);
        }

        controllers.keySet().removeIf(
                trainId -> !existingTrains.containsKey(trainId));
    }

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
