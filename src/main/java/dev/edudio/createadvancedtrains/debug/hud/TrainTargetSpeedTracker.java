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

    private TrainTargetSpeedTracker() {
    }

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

    TargetSpeeds get(UUID trainId) {
        return targetSpeedsByTrain.get(trainId);
    }

    void retainAll(Set<UUID> trainIds) {
        targetSpeedsByTrain.keySet().removeIf(trainId -> !trainIds.contains(trainId));
    }

    void clear() {
        targetSpeedsByTrain.clear();
    }

    record TargetSpeeds(
            double createTargetSpeedBlocksPerTick,
            double atoTargetSpeedBlocksPerTick) {
    }
}
