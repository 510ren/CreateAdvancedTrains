package dev.edudio.createadvancedtrains.debug.notchtest;

import static dev.edudio.createadvancedtrains.constants.UnitConstants.TICKS_PER_SECOND;
import static dev.edudio.createadvancedtrains.constants.UnitConstants.TICKS_PER_SECOND_SQUARED;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.GlobalRailwayManager;
import com.simibubi.create.content.trains.entity.Train;

import dev.edudio.createadvancedtrains.config.AdvancedTrainsConfig;
import dev.edudio.createadvancedtrains.control.notch.FixedNotchSelector;
import dev.edudio.createadvancedtrains.control.notch.Notch;
import dev.edudio.createadvancedtrains.control.notch.NotchProfile;
import dev.edudio.createadvancedtrains.debug.notchtest.StopTargetHoldResult.State;
import dev.edudio.createadvancedtrains.utils.SendMessageToClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Owns the explicitly enabled Phase 5A fixed-notch test session.
 */
public final class Phase5ANotchTestManager {

    public static final Phase5ANotchTestManager INSTANCE = new Phase5ANotchTestManager();

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FIXED_FOR_TEST = "FIXED_FOR_TEST";
    private final NotchProfile profile = NotchProfile.phase5ATemporaryProfile();
    private final Map<UUID, NotchTestTrainState> trainStates = new HashMap<>();
    private final Map<UUID, StopTargetHoldLatch> stopTargetHolds = new HashMap<>();
    private final Map<UUID, NotchTestLogWriter> writers = new HashMap<>();
    private final Set<UUID> failedTrainIds = new HashSet<>();

    private boolean sessionActive;
    private UUID testSessionId;
    private Instant sessionStartedAtUtc;
    private FixedNotchSelector selector;
    private boolean holdStopTargetUntilStop;
    private long currentServerTick = Long.MIN_VALUE;
    private String lastRejectedConfiguration;
    private String lastIgnoredConfiguration;

    /**
     * このクラスのインスタンスを初期化します。
     */
    private Phase5ANotchTestManager() {
    }

    /**
     * ServerTickStartイベントを処理します。
     * @param server 処理対象のMinecraftサーバー。
     */
    public void onServerTickStart(MinecraftServer server) {
        if (!AdvancedTrainsConfig.PHASE5A_NOTCH_TEST_ENABLED.get()) {
            lastRejectedConfiguration = null;
            lastIgnoredConfiguration = null;
            if (sessionActive) {
                endSession("config_disabled");
            }
            return;
        }

        Configuration configuration = readConfiguration();
        if (!configuration.valid()) {
            warnRejectedConfiguration(configuration.reason());
            if (sessionActive) {
                endSession("invalid_configuration");
            }
            return;
        }
        lastRejectedConfiguration = null;

        if (!sessionActive) {
            startSession(configuration.fixedNotch(), configuration.holdStopTargetUntilStop());
        } else if (holdStopTargetUntilStop != configuration.holdStopTargetUntilStop()) {
            Notch activeNotch = selector.select();
            endSession("hold_stop_target_until_stop_changed");
            startSession(activeNotch, configuration.holdStopTargetUntilStop());
        }

        if (selector.select() != configuration.fixedNotch()) {
            warnIgnoredConfiguration(configuration.fixedNotch());
        }

        // Forge fires ServerTick START immediately before Minecraft increments
        // tickCount.
        currentServerTick = (long) server.getTickCount() + 1L;
        ServerLevel level = server.overworld();
        GlobalRailwayManager railwayManager = Create.RAILWAYS.sided(level);
        if (railwayManager == null) {
            return;
        }

        Notch fixedNotch = selector.select();
        for (Train train : railwayManager.trains.values()) {
            trainStates.computeIfAbsent(train.id, ignored -> new NotchTestTrainState(train))
                    .beginTick(train, currentServerTick, fixedNotch, profile);
        }
    }

    /**
     * ServerTickEndイベントを処理します。
     * @param server 処理対象のMinecraftサーバー。
     */
    public void onServerTickEnd(MinecraftServer server) {
        if (!sessionActive) {
            return;
        }

        ServerLevel level = server.overworld();
        GlobalRailwayManager railwayManager = Create.RAILWAYS.sided(level);
        if (railwayManager == null) {
            return;
        }

        long serverTick = server.getTickCount();
        Notch fixedNotch = selector.select();
        Set<UUID> currentTrainIds = new HashSet<>();

        for (Train train : railwayManager.trains.values()) {
            currentTrainIds.add(train.id);
            NotchTestTrainState state = trainStates.computeIfAbsent(
                    train.id,
                    ignored -> new NotchTestTrainState(train));
            state.ensureTick(train, serverTick, fixedNotch, profile);
            NotchTestSnapshot snapshot = state.finishTick(train, serverTick, fixedNotch);
            writeSample(snapshot);
            updateStopTargetHoldReleaseAtTickEnd(train, serverTick);
        }

        removeMissingTrains(currentTrainIds);
    }

    /**
     * Applies the optional Phase 5A stop-target fixture at the existing Create
     * boundary.
     * @param train 対象となるCreate列車。
     * @param nativeTargetSpeedBlocksPerTick 仕様書に個別説明がないため、{@code nativeTargetSpeedBlocksPerTick}が示すCreate境界の速度。単位はblocks/tick。
     * @return 処理によって得られた結果。
     */
    public StopTargetHoldResult applyStopTargetHold(
            Train train,
            double nativeTargetSpeedBlocksPerTick) {
        if (!sessionActive
                || currentServerTick == Long.MIN_VALUE
                || !holdStopTargetUntilStop) {
            return StopTargetHoldResult.inactive();
        }

        StopTargetHoldLatch latch = stopTargetHolds.get(train.id);
        if (latch != null
                && (latch.isReleasePending() || StopTargetHoldLatch.shouldRelease(train))) {
            SendMessageToClient
                    .send("[CAT : Phase5ANotchTestManager]: TargetSpeedの固定は解除されてよいと判定されました（trainId=" + train.id
                            + "）");
            stopTargetHolds.remove(train.id);
            return StopTargetHoldResult.released();
        }

        String triggerReason = null;
        if (latch == null) {
            if (!StopTargetHoldLatch.canLatch(train, nativeTargetSpeedBlocksPerTick)) {
                return StopTargetHoldResult.inactive();
            }

            stopTargetHolds.put(train.id, new StopTargetHoldLatch());
            triggerReason = StopTargetHoldLatch.NAVIGATION_NATIVE_STOP_TARGET;
            SendMessageToClient
                    .send("[CAT : Phase5ANotchTestManager]: TargetSpeedの固定が有効になりました：" + triggerReason);
        }

        train.targetSpeed = 0.0;
        return StopTargetHoldResult.latched(triggerReason);
    }

    /**
     * Returns the acceleration modifier that Create should use for this call.
     * No Train field is assigned here.
     * @param train 対象となるCreate列車。
     * @param preCatTargetSpeedBlocksPerTick 仕様書に個別説明がないため、{@code preCatTargetSpeedBlocksPerTick}が示すCreate境界の速度。単位はblocks/tick。
     * @param finalTargetSpeedBlocksPerTick 仕様書に個別説明がないため、{@code finalTargetSpeedBlocksPerTick}が示すCreate境界の速度。単位はblocks/tick。
     * @param originalAccelerationMod CATが変更する前のCreate加速度倍率。
     * @param stopTargetHold 仕様書に個別説明がないため、現在の処理内容から推定した、{@code stopTargetHold}として使用される入力値。
     * @return 処理または計算によって得られた数値。
     */
    public float modifyAccelerationMod(
            Train train,
            double preCatTargetSpeedBlocksPerTick,
            double finalTargetSpeedBlocksPerTick,
            float originalAccelerationMod,
            StopTargetHoldResult stopTargetHold) {
        if (!sessionActive || currentServerTick == Long.MIN_VALUE) {
            return originalAccelerationMod;
        }

        Notch fixedNotch = selector.select();
        NotchTestTrainState state = trainStates.computeIfAbsent(
                train.id,
                ignored -> new NotchTestTrainState(train));
        state.ensureTick(train, currentServerTick, fixedNotch, profile);
        state.observeTargets(
                preCatTargetSpeedBlocksPerTick,
                finalTargetSpeedBlocksPerTick,
                originalAccelerationMod,
                stopTargetHold);

        if (!Double.isFinite(train.speed)
                || !Double.isFinite(finalTargetSpeedBlocksPerTick)) {
            String reason = "speed_or_target_non_finite";
            state.markInvalidInput(reason);
            state.recordApproachCall(false, reason, originalAccelerationMod, false);
            return originalAccelerationMod;
        }

        String demandFailure = brakingDemandFailure(train.speed, finalTargetSpeedBlocksPerTick);
        if (demandFailure != null) {
            state.markNoBrakingDemand(demandFailure);
            state.recordApproachCall(false, demandFailure, originalAccelerationMod, false);
            return originalAccelerationMod;
        }

        double baseAcceleration = Math.abs(train.acceleration()) * TICKS_PER_SECOND_SQUARED;
        if (!Double.isFinite(baseAcceleration) || baseAcceleration <= 0.0) {
            String reason = "base_acceleration_non_finite_or_non_positive";
            state.markInvalidInput(reason);
            state.recordApproachCall(true, reason, originalAccelerationMod, false);
            return originalAccelerationMod;
        }

        if (!Float.isFinite(originalAccelerationMod) || originalAccelerationMod < 0.0f) {
            String reason = "create_acceleration_modifier_invalid";
            state.markInvalidInput(reason);
            state.recordApproachCall(true, reason, originalAccelerationMod, false);
            return originalAccelerationMod;
        }

        double currentSpeedBlocksPerSecond = train.speed * TICKS_PER_SECOND;
        double profileTargetAcceleration = profile.targetAcceleration(
                fixedNotch,
                currentSpeedBlocksPerSecond,
                baseAcceleration);
        Float modifiedAcceleration = state.apply(
                fixedNotch,
                baseAcceleration,
                profileTargetAcceleration);
        if (modifiedAcceleration == null) {
            String reason = "effective_acceleration_modifier_invalid";
            state.recordApproachCall(true, reason, originalAccelerationMod, false);
            return originalAccelerationMod;
        }

        state.recordApproachCall(true, null, modifiedAcceleration, true);
        return modifiedAcceleration;
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
     * Returns a read-only notch view for diagnostics without creating test state.
     * @param trainId 対象列車を識別するUUID。
     * @return 処理によって得られた結果。
     */
    public NotchStatus getNotchStatus(UUID trainId) {
        if (!sessionActive || selector == null) {
            return NotchStatus.inactive();
        }

        Notch commandedNotch = selector.select();
        NotchTestTrainState state = trainStates.get(trainId);
        if (state == null) {
            return new NotchStatus(commandedNotch, Notch.N, false);
        }

        return new NotchStatus(
                commandedNotch,
                state.appliedNotch(),
                state.controlAppliedThisTick());
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code startSession}が示す処理区間を開始または準備します。
     * @param fixedNotch 仕様書に個別説明がないため、{@code fixedNotch}が示すノッチ状態またはノッチ候補。
     * @param holdStopTargetUntilStop 仕様書に個別説明がないため、{@code holdStopTargetUntilStop}が示す条件の有効・無効を表す値。
     */
    private void startSession(Notch fixedNotch, boolean holdStopTargetUntilStop) {
        sessionActive = true;
        testSessionId = UUID.randomUUID();
        sessionStartedAtUtc = Instant.now();
        selector = new FixedNotchSelector(fixedNotch);
        this.holdStopTargetUntilStop = holdStopTargetUntilStop;
        currentServerTick = Long.MIN_VALUE;
        trainStates.clear();
        stopTargetHolds.clear();
        failedTrainIds.clear();
        lastIgnoredConfiguration = null;
        LOGGER.info(
                "[Create: Advanced Trains] Phase 5A notch test started: session={}, fixedNotch={}, holdStopTargetUntilStop={}.",
                testSessionId,
                fixedNotch,
                holdStopTargetUntilStop);
        SendMessageToClient
                .send("[CAT : Phase5ANotchTestManager]: セッションが開始されました（testSessionId=" + testSessionId
                        + ", fixedNotch=" + fixedNotch + ", holdStopTargetUntilStop=" + holdStopTargetUntilStop + "）");
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code endSession}が示す処理区間を終了します。
     * @param reason 処理を行う理由を表す文字列。
     */
    private void endSession(String reason) {
        for (NotchTestLogWriter writer : writers.values()) {
            if (!writer.close(reason)) {
                LOGGER.warn(
                        "[Create: Advanced Trains] Could not cleanly close Phase 5A notch test log {} (reason: {}).",
                        writer.getPath(),
                        reason);
            }
        }

        SendMessageToClient
                .send("[CAT : Phase5ANotchTestManager]: セッションは次の理由で終了しました： " + reason);

        writers.clear();
        trainStates.clear();
        stopTargetHolds.clear();
        failedTrainIds.clear();
        sessionActive = false;
        testSessionId = null;
        sessionStartedAtUtc = null;
        selector = null;
        holdStopTargetUntilStop = false;
        currentServerTick = Long.MIN_VALUE;
        lastIgnoredConfiguration = null;
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code writeSample}が示すデータを出力先へ書き込みます。
     * @param snapshot 記録または判定に使用する不変スナップショット。
     */
    private void writeSample(NotchTestSnapshot snapshot) {
        UUID trainId = snapshot.trainId();
        if (failedTrainIds.contains(trainId)) {
            return;
        }

        NotchTestLogWriter writer = writers.get(trainId);
        if (writer == null) {
            try {
                writer = NotchTestLogWriter.open(
                        outputDirectory(),
                        trainId,
                        testSessionId,
                        sessionStartedAtUtc,
                        selector.select(),
                        holdStopTargetUntilStop);
                writers.put(trainId, writer);
            } catch (IOException | RuntimeException exception) {
                failedTrainIds.add(trainId);
                LOGGER.warn(
                        "[Create: Advanced Trains] Could not open Phase 5A notch test log for {}. Logging for this train is disabled until the next session.",
                        trainId,
                        exception);
                return;
            }
        }

        if (!writer.writeSample(snapshot)) {
            writers.remove(trainId);
            failedTrainIds.add(trainId);
            LOGGER.warn(
                    "[Create: Advanced Trains] Phase 5A notch test logging failed for {} in {}. Logging for this train is disabled until the next session.",
                    trainId,
                    writer.getPath());
        }
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code removeMissingTrains}が示す対象を保持状態から削除します。
     * @param currentTrainIds 仕様書に個別説明がないため、{@code currentTrainIds}が示す対象識別子。
     */
    private void removeMissingTrains(Set<UUID> currentTrainIds) {
        trainStates.keySet().removeIf(trainId -> !currentTrainIds.contains(trainId));
        stopTargetHolds.keySet().removeIf(trainId -> !currentTrainIds.contains(trainId));

        Iterator<Map.Entry<UUID, NotchTestLogWriter>> iterator = writers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, NotchTestLogWriter> entry = iterator.next();
            if (currentTrainIds.contains(entry.getKey())) {
                continue;
            }

            if (!entry.getValue().close("train_disappeared")) {
                LOGGER.warn(
                        "[Create: Advanced Trains] Could not cleanly close Phase 5A notch test log {}.",
                        entry.getValue().getPath());
            }
            iterator.remove();
        }
        failedTrainIds.removeIf(trainId -> !currentTrainIds.contains(trainId));
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code updateStopTargetHoldReleaseAtTickEnd}が対象とする保持状態を最新の入力で更新します。
     * @param train 対象となるCreate列車。
     * @param serverTick 処理対象となるserver tick。
     */
    private void updateStopTargetHoldReleaseAtTickEnd(Train train, long serverTick) {
        StopTargetHoldLatch latch = stopTargetHolds.get(train.id);
        if (latch == null) {
            return;
        }

        if (latch.releaseWasPendingBefore(serverTick)) {
            stopTargetHolds.remove(train.id);
            return;
        }

        if (StopTargetHoldLatch.shouldRelease(train)) {
            latch.markReleasePending(serverTick);
        }
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code readConfiguration}が示す値を現在状態から読み取ります。
     * @return 処理によって得られた結果。
     */
    private Configuration readConfiguration() {
        String mode = normalize(AdvancedTrainsConfig.PHASE5A_NOTCH_TEST_SELECTION_MODE.get());
        if (!FIXED_FOR_TEST.equals(mode)) {
            return Configuration.invalid(
                    "selection_mode must be FIXED_FOR_TEST for Phase 5A train control (configured: " + mode + ")");
        }

        String configuredNotch = AdvancedTrainsConfig.PHASE5A_NOTCH_TEST_FIXED_NOTCH.get();
        Optional<Notch> fixedNotch = Notch.parseServiceBrake(configuredNotch);
        if (fixedNotch.isEmpty()) {
            return Configuration.invalid(
                    "fixed_notch must be one of B1 through B7 (configured: " + configuredNotch + ")");
        }
        return Configuration.valid(
                fixedNotch.get(),
                AdvancedTrainsConfig.PHASE5A_NOTCH_TEST_HOLD_STOP_TARGET_UNTIL_STOP.get());
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code warnRejectedConfiguration}としてまとめられている処理を実行します。
     * @param reason 処理を行う理由を表す文字列。
     */
    private void warnRejectedConfiguration(String reason) {
        if (reason.equals(lastRejectedConfiguration)) {
            return;
        }

        lastRejectedConfiguration = reason;
        LOGGER.warn(
                "[Create: Advanced Trains] Phase 5A notch test did not start: {}.",
                reason);
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code warnIgnoredConfiguration}としてまとめられている処理を実行します。
     * @param configuredNotch 仕様書に個別説明がないため、{@code configuredNotch}が示すノッチ状態またはノッチ候補。
     */
    private void warnIgnoredConfiguration(Notch configuredNotch) {
        String message = "fixed_notch=" + configuredNotch;
        if (message.equals(lastIgnoredConfiguration)) {
            return;
        }

        lastIgnoredConfiguration = message;
        LOGGER.warn(
                "[Create: Advanced Trains] Phase 5A notch test keeps {} for active session {}; {} is ignored until the test is disabled and enabled again.",
                selector.select(),
                testSessionId,
                message);
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code brakingDemandFailure}としてまとめられている処理を実行します。
     * @param speed 仕様書に個別説明がないため、{@code speed}が示す速度値。単位は呼出元の境界定義に従います。
     * @param targetSpeed 仕様書に個別説明がないため、{@code targetSpeed}が示す速度値。単位は呼出元の境界定義に従います。
     * @return 処理によって得られた結果。
     */
    private static String brakingDemandFailure(double speed, double targetSpeed) {
        if (!Double.isFinite(speed) || !Double.isFinite(targetSpeed)) {
            return "speed_or_target_non_finite";
        }
        if (Mth.equal(speed, 0.0)) {
            return "train_not_moving";
        }
        if (Mth.equal(speed, targetSpeed)) {
            return "target_matches_current_speed";
        }
        if (targetSpeed != 0.0 && Math.signum(speed) != Math.signum(targetSpeed)) {
            return "target_not_same_direction";
        }
        if (Math.abs(targetSpeed) >= Math.abs(speed)) {
            return "target_not_lower_than_current_speed";
        }
        return null;
    }

    /**
     * 仕様書に独立した関数契約がないため、入力値を{@code normalize}が示す形式へ整えます。
     * @param value 処理対象の値。
     * @return 処理によって得られた結果。
     */
    private static String normalize(String value) {
        return value == null ? "null" : value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code outputDirectory}としてまとめられている処理を実行します。
     * @return 処理によって得られた結果。
     */
    private static Path outputDirectory() {
        return FMLPaths.GAMEDIR.get()
                .resolve("logs")
                .resolve("create_advanced_trains")
                .resolve("notch_test");
    }

    /**
     * 仕様書に独立した型契約がないため、現在の利用箇所から推定した不変データを保持します。
     * @param valid 仕様書に個別説明がないため、{@code valid}が示す対象識別子。
     * @param fixedNotch 仕様書に個別説明がないため、{@code fixedNotch}が示すノッチ状態またはノッチ候補。
     * @param holdStopTargetUntilStop 仕様書に個別説明がないため、{@code holdStopTargetUntilStop}が示す条件の有効・無効を表す値。
     * @param reason 処理を行う理由を表す文字列。
     */
    private record Configuration(
            boolean valid,
            Notch fixedNotch,
            boolean holdStopTargetUntilStop,
            String reason) {

        /**
         * 仕様書に独立した関数契約がないため、{@code valid}が示す状態の結果オブジェクトを生成します。
         * @param fixedNotch 仕様書に個別説明がないため、{@code fixedNotch}が示すノッチ状態またはノッチ候補。
         * @param holdStopTargetUntilStop 仕様書に個別説明がないため、{@code holdStopTargetUntilStop}が示す条件の有効・無効を表す値。
         * @return 処理によって得られた結果。
         */
        private static Configuration valid(Notch fixedNotch, boolean holdStopTargetUntilStop) {
            return new Configuration(true, fixedNotch, holdStopTargetUntilStop, null);
        }

        /**
         * 仕様書に独立した関数契約がないため、{@code invalid}が示す状態の結果オブジェクトを生成します。
         * @param reason 処理を行う理由を表す文字列。
         * @return 処理によって得られた結果。
         */
        private static Configuration invalid(String reason) {
            return new Configuration(false, null, false, reason);
        }
    }

    /**
     * 仕様書に独立した型契約がないため、現在の利用箇所から推定した不変データを保持します。
     * @param commandedNotch 仕様書に個別説明がないため、{@code commandedNotch}が示すノッチ状態またはノッチ候補。
     * @param appliedNotch 仕様書に個別説明がないため、{@code appliedNotch}が示すノッチ状態またはノッチ候補。
     * @param controlApplied 仕様書に個別説明がないため、{@code controlApplied}が示す条件の有効・無効を表す値。
     */
    public record NotchStatus(
            Notch commandedNotch,
            Notch appliedNotch,
            boolean controlApplied) {

        /**
         * 仕様書に独立した関数契約がないため、{@code inactive}が示す状態の結果オブジェクトを生成します。
         * @return 処理によって得られた結果。
         */
        private static NotchStatus inactive() {
            return new NotchStatus(Notch.N, Notch.N, false);
        }
    }
}
