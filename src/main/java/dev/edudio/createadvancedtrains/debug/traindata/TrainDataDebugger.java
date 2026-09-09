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

    private boolean sessionActive;
    private Instant sessionStartedAtUtc;

    private TrainDataDebugger() {
    }

    public void onServerTick(MinecraftServer server) {
        if (!AdvancedTrainsConfig.TRAIN_DATA_DEBUG_ENABLED.get()) {
            if (sessionActive) {
                endSession("config_disabled");
            }
            return;
        }

        if (!sessionActive) {
            sessionActive = true;
            sessionStartedAtUtc = Instant.now();
            failedTrainIds.clear();
        }

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
        String dimension = level.dimension().location().toString();

        for (Train train : railwayManager.trains.values()) {
            writeSample(
                    train,
                    serverTick,
                    levelGameTime,
                    dimension,
                    sampleIntervalTicks);
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

    private void writeSample(
            Train train,
            long serverTick,
            long levelGameTime,
            String dimension,
            int sampleIntervalTicks) {
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

        TrainDataLogWriter writer = writers.get(trainId);
        if (writer == null) {
            try {
                writer = TrainDataLogWriter.open(
                        outputDirectory(),
                        trainId,
                        sessionStartedAtUtc,
                        dimension,
                        sampleIntervalTicks);
                writers.put(trainId, writer);
            } catch (IOException | RuntimeException exception) {
                failedTrainIds.add(trainId);
                LOGGER.warn(
                        "[Create: Advanced Trains] Could not open a train data log for {}. Logging for this train is disabled until the next session.",
                        trainId,
                        exception);
                return;
            }
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
    }

    private void endSession(String reason) {
        closeAllWriters(reason);
        failedTrainIds.clear();
        sessionStartedAtUtc = null;
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
}
