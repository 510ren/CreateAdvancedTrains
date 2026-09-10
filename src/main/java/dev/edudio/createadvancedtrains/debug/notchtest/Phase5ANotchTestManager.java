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

    private Phase5ANotchTestManager() {
    }

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

    /**
     * Returns a read-only notch view for diagnostics without creating test state.
     */
    public NotchStatus getNotchStatus(UUID trainId) {
        if (!sessionActive || selector == null) {
            return NotchStatus.inactive();
        }

        Notch commandedNotch = selector.select();
        NotchTestTrainState state = trainStates.get(trainId);
        if (state == null) {
            return new NotchStatus(commandedNotch, Notch.COAST, false);
        }

        return new NotchStatus(
                commandedNotch,
                state.appliedNotch(),
                state.controlAppliedThisTick());
    }

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

    private void warnRejectedConfiguration(String reason) {
        if (reason.equals(lastRejectedConfiguration)) {
            return;
        }

        lastRejectedConfiguration = reason;
        LOGGER.warn(
                "[Create: Advanced Trains] Phase 5A notch test did not start: {}.",
                reason);
    }

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

    private static String normalize(String value) {
        return value == null ? "null" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static Path outputDirectory() {
        return FMLPaths.GAMEDIR.get()
                .resolve("logs")
                .resolve("create_advanced_trains")
                .resolve("notch_test");
    }

    private record Configuration(
            boolean valid,
            Notch fixedNotch,
            boolean holdStopTargetUntilStop,
            String reason) {

        private static Configuration valid(Notch fixedNotch, boolean holdStopTargetUntilStop) {
            return new Configuration(true, fixedNotch, holdStopTargetUntilStop, null);
        }

        private static Configuration invalid(String reason) {
            return new Configuration(false, null, false, reason);
        }
    }

    public record NotchStatus(
            Notch commandedNotch,
            Notch appliedNotch,
            boolean controlApplied) {

        private static NotchStatus inactive() {
            return new NotchStatus(Notch.COAST, Notch.COAST, false);
        }
    }
}
