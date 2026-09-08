package dev.edudio.createadvancedtrains.train.client;

import dev.edudio.createadvancedtrains.train.TrainDebugData;

import java.util.Collections;
import java.util.List;

public final class TrainDebugClientData {

    private static List<TrainDebugData> trains = Collections.emptyList();

    private TrainDebugClientData() {
    }

    public static void setTrains(List<TrainDebugData> newTrains) {
        trains = List.copyOf(newTrains);
    }

    public static List<TrainDebugData> getTrains() {
        return trains;
    }

    public static void clear() {
        trains = Collections.emptyList();
    }
}