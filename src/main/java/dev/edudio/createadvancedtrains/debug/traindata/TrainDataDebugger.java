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

    /**
     * このクラスのインスタンスを初期化します。
     */
    private TrainDataDebugger() {
    }

    /**
     * ServerTickStartイベントを処理します。
     * @param server 処理対象のMinecraftサーバー。
     */
    public void onServerTickStart(MinecraftServer server) {
        if (!AdvancedTrainsConfig.TRAIN_DATA_DEBUG_ENABLED.get()) {
            if (sessionActive) {
                endSession("config_disabled");
            }
            return;
        }
        ensureSession(server);
    }

    /**
     * ServerTickイベントを処理します。
     * @param server 処理対象のMinecraftサーバー。
     */
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

    /**
     * OverworldUnloadイベントを処理します。
     */
    public void onOverworldUnload() {
        if (sessionActive) {
            endSession("level_unload");
        }
    }

    /**
     * ServerStoppingイベントを処理します。
     */
    public void onServerStopping() {
        if (sessionActive) {
            endSession("server_stopping");
        }
    }

    /**
     * 仕様書に独立した関数契約がないため、現在値を{@code recordApproachCall}が示す観測状態へ記録します。
     * @param train 対象となるCreate列車。
     * @param controller 仕様書に個別説明がないため、{@code controller}が示す条件の有効・無効を表す値。
     * @param serverTick 処理対象となるserver tick。
     * @param nativeTargetBlocksPerTick Create由来の目標速度。単位はblocks/tick。
     * @param finalTargetBlocksPerTick CAT処理後の最終目標速度。単位はblocks/tick。
     * @param originalAccelerationMod CATが変更する前のCreate加速度倍率。
     * @param returnedAccelerationMod 最終的にCreateへ返す加速度倍率。
     */
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

    /**
     * 仕様書に独立した関数契約がないため、{@code writeSample}が示すデータを出力先へ書き込みます。
     * @param train 対象となるCreate列車。
     * @param serverTick 処理対象となるserver tick。
     * @param levelGameTime 仕様書に個別説明がないため、現在の処理内容から推定した、{@code levelGameTime}として使用される入力値。
     */
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

    /**
     * 仕様書に独立した関数契約がないため、{@code closeMissingTrainWriters}が対象とする資源を終了処理して閉じます。
     * @param currentTrainIds 仕様書に個別説明がないため、{@code currentTrainIds}が示す対象識別子。
     */
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

    /**
     * 仕様書に独立した関数契約がないため、{@code ensureSession}が示す処理区間を開始または準備します。
     * @param server 処理対象のMinecraftサーバー。
     */
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

    /**
     * 現在のOrOpenWriterを返します。
     * @param trainId 対象列車を識別するUUID。
     * @return 処理によって得られた結果。
     */
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

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code failTrainLogging}としてまとめられている処理を実行します。
     * @param trainId 対象列車を識別するUUID。
     * @param action 仕様書に個別説明がないため、現在の処理内容から推定した、{@code action}として使用される入力値。
     * @param exception 記録または処理対象の例外。
     */
    private void failTrainLogging(UUID trainId, String action, Exception exception) {
        failedTrainIds.add(trainId);
        LOGGER.warn(
                "[Create: Advanced Trains] {} for {}. Logging for this train is disabled until the next session.",
                action,
                trainId,
                exception);
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code endSession}が示す処理区間を終了します。
     * @param reason 処理を行う理由を表す文字列。
     */
    private void endSession(String reason) {
        closeAllWriters(reason);
        failedTrainIds.clear();
        approachCallCounters.clear();
        sessionStartedAtUtc = null;
        sessionDimension = null;
        sessionActive = false;
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code closeAllWriters}が対象とする資源を終了処理して閉じます。
     * @param reason 処理を行う理由を表す文字列。
     */
    private void closeAllWriters(String reason) {
        for (TrainDataLogWriter writer : writers.values()) {
            closeWriter(writer, reason);
        }
        writers.clear();
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code closeWriter}が対象とする資源を終了処理して閉じます。
     * @param writer 出力先のログライター。
     * @param reason 処理を行う理由を表す文字列。
     */
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

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code outputDirectory}としてまとめられている処理を実行します。
     * @return 処理によって得られた結果。
     */
    private static Path outputDirectory() {
        return FMLPaths.GAMEDIR.get()
                .resolve("logs")
                .resolve("create_advanced_trains")
                .resolve("train_data");
    }

    /**
     * 仕様書に独立した型契約がないため、現在の利用箇所から推定した不変データを保持します。
     * @param serverTick 処理対象となるserver tick。
     * @param callCount 仕様書に個別説明がないため、現在の処理内容から推定した、{@code callCount}として使用される入力値。
     */
    private record CallCounter(long serverTick, int callCount) {
    }
}
