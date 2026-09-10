package dev.edudio.createadvancedtrains.debug.traindata;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.GlobalRailwayManager;
import com.simibubi.create.content.trains.entity.Train;

import dev.edudio.createadvancedtrains.config.AdvancedTrainsConfig;
import dev.edudio.createadvancedtrains.train.TrainController;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Owns the lifecycle of passive, server-side train data logging.
 */
public final class TrainDataDebugger {

    public static final TrainDataDebugger INSTANCE = new TrainDataDebugger();

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<UUID, TrainDataLogWriter> writers = new HashMap<>();
    private final Set<UUID> failedTrainIds = new HashSet<>();
    private final Map<UUID, CallCounter> approachCallCounters = new HashMap<>();

    private boolean sessionActive;
    private Instant sessionStartedAtUtc;
    private String sessionDimension;

    private TrainDataDebugger() {
    }

    public void onServerTickStart(MinecraftServer server) {
        if (!AdvancedTrainsConfig.TRAIN_DATA_DEBUG_ENABLED.get()) {
            if (sessionActive) {
                endSession("config_disabled");
            }
            return;
        }
        ensureSession(server);
    }

    public void onServerTick(MinecraftServer server) {
        if (!AdvancedTrainsConfig.TRAIN_DATA_DEBUG_ENABLED.get()) {
            if (sessionActive) {
                endSession("config_disabled");
            }
            return;
        }

        ensureSession(server);

        ServerLevel level = server.overworld();
        GlobalRailwayManager railwayManager = Create.RAILWAYS.sided(level);

        if (railwayManager == null) {
            closeAllWriters("railway_manager_unavailable");
            return;
        }

        Set<UUID> currentTrainIds = new HashSet<>();
        for (Train train : railwayManager.trains.values()) {
            currentTrainIds.add(train.id);
        }
        closeMissingTrainWriters(currentTrainIds);

        int sampleIntervalTicks = AdvancedTrainsConfig.TRAIN_DATA_DEBUG_SAMPLE_INTERVAL_TICKS.get();
        long serverTick = server.getTickCount();

        if (serverTick % sampleIntervalTicks != 0) {
            return;
        }

        long levelGameTime = level.getGameTime();
        for (Train train : railwayManager.trains.values()) {
            writeSample(train, serverTick, levelGameTime);
        }
    }

    public void onOverworldUnload() {
        if (sessionActive) {
            endSession("level_unload");
        }
    }

    public void onServerStopping() {
        if (sessionActive) {
            endSession("server_stopping");
        }
    }

    public void recordApproachCall(
            Train train,
            TrainController controller,
            long serverTick,
            double nativeTargetBlocksPerTick,
            double finalTargetBlocksPerTick,
            float originalAccelerationMod,
            float returnedAccelerationMod) {
        if (!AdvancedTrainsConfig.TRAIN_DATA_DEBUG_ENABLED.get() || !sessionActive) {
            return;
        }
        UUID trainId = train.id;
        if (failedTrainIds.contains(trainId)) {
            return;
        }

        CallCounter previous = approachCallCounters.get(trainId);
        int callIndex = previous != null && previous.serverTick() == serverTick
                ? previous.callCount() + 1
                : 1;
        approachCallCounters.put(trainId, new CallCounter(serverTick, callIndex));

        ApproachCallSnapshot snapshot;
        try {
            snapshot = ApproachCallSnapshot.capture(
                    train,
                    controller,
                    serverTick,
                    callIndex,
                    nativeTargetBlocksPerTick,
                    finalTargetBlocksPerTick,
                    originalAccelerationMod,
                    returnedAccelerationMod);
        } catch (RuntimeException exception) {
            failTrainLogging(trainId, "Could not capture an approach-call sample", exception);
            return;
        }

        TrainDataLogWriter writer = getOrOpenWriter(trainId);
        if (writer == null) {
            return;
        }
        if (!writer.writeApproachCall(snapshot)) {
            writers.remove(trainId);
            failedTrainIds.add(trainId);
            LOGGER.warn(
                    "[Create: Advanced Trains] Approach-call logging failed for {} in {}. Logging for this train is disabled until the next session.",
                    trainId,
                    writer.getPath());
        }
    }

    private void writeSample(
            Train train,
            long serverTick,
            long levelGameTime) {
        UUID trainId = train.id;

        if (failedTrainIds.contains(trainId)) {
            return;
        }

        TrainDataSnapshot snapshot;
        try {
            snapshot = TrainDataSnapshot.capture(train, serverTick, levelGameTime);
        } catch (RuntimeException exception) {
            failedTrainIds.add(trainId);
            LOGGER.warn(
                    "[Create: Advanced Trains] Could not sample train data for {}. Logging for this train is disabled until the next session.",
                    trainId,
                    exception);
            return;
        }

        TrainDataLogWriter writer = getOrOpenWriter(trainId);
        if (writer == null) {
            return;
        }

        if (!writer.writeSample(snapshot)) {
            writers.remove(trainId);
            failedTrainIds.add(trainId);
            LOGGER.warn(
                    "[Create: Advanced Trains] Train data logging failed for {} in {}. Logging for this train is disabled until the next session.",
                    trainId,
                    writer.getPath());
        }
    }

    private void closeMissingTrainWriters(Set<UUID> currentTrainIds) {
        Iterator<Map.Entry<UUID, TrainDataLogWriter>> iterator = writers.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, TrainDataLogWriter> entry = iterator.next();
            if (!currentTrainIds.contains(entry.getKey())) {
                closeWriter(entry.getValue(), "train_disappeared");
                iterator.remove();
            }
        }
        approachCallCounters.keySet().removeIf(trainId -> !currentTrainIds.contains(trainId));
    }

    private void ensureSession(MinecraftServer server) {
        if (sessionActive) {
            return;
        }
        sessionActive = true;
        sessionStartedAtUtc = Instant.now();
        sessionDimension = server.overworld().dimension().location().toString();
        failedTrainIds.clear();
        approachCallCounters.clear();
    }

    private TrainDataLogWriter getOrOpenWriter(UUID trainId) {
        TrainDataLogWriter writer = writers.get(trainId);
        if (writer != null) {
            return writer;
        }
        try {
            writer = TrainDataLogWriter.open(
                    outputDirectory(),
                    trainId,
                    sessionStartedAtUtc,
                    sessionDimension,
                    AdvancedTrainsConfig.TRAIN_DATA_DEBUG_SAMPLE_INTERVAL_TICKS.get());
            writers.put(trainId, writer);
            return writer;
        } catch (IOException | RuntimeException exception) {
            failTrainLogging(trainId, "Could not open a train data log", exception);
            return null;
        }
    }

    private void failTrainLogging(UUID trainId, String action, Exception exception) {
        failedTrainIds.add(trainId);
        LOGGER.warn(
                "[Create: Advanced Trains] {} for {}. Logging for this train is disabled until the next session.",
                action,
                trainId,
                exception);
    }

    private void endSession(String reason) {
        closeAllWriters(reason);
        failedTrainIds.clear();
        approachCallCounters.clear();
        sessionStartedAtUtc = null;
        sessionDimension = null;
        sessionActive = false;
    }

    private void closeAllWriters(String reason) {
        for (TrainDataLogWriter writer : writers.values()) {
            closeWriter(writer, reason);
        }
        writers.clear();
    }

    private void closeWriter(
            TrainDataLogWriter writer,
            String reason) {
        if (!writer.close(reason)) {
            LOGGER.warn(
                    "[Create: Advanced Trains] Could not cleanly close train data log {} (reason: {}).",
                    writer.getPath(),
                    reason);
        }
    }

    private static Path outputDirectory() {
        return FMLPaths.GAMEDIR.get()
                .resolve("logs")
                .resolve("create_advanced_trains")
                .resolve("train_data");
    }

    private record CallCounter(long serverTick, int callCount) {
    }
}
