package dev.edudio.createadvancedtrains.debug.hud;

import static dev.edudio.createadvancedtrains.constants.UnitConstants.TICKS_PER_SECOND;
import static dev.edudio.createadvancedtrains.constants.UnitConstants.TICKS_PER_SECOND_SQUARED;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.GlobalRailwayManager;
import com.simibubi.create.content.trains.entity.Train;

import dev.edudio.createadvancedtrains.CreateAdvancedTrains;
import dev.edudio.createadvancedtrains.control.notch.Notch;
import dev.edudio.createadvancedtrains.debug.hud.TrainTargetSpeedTracker.TargetSpeeds;
import dev.edudio.createadvancedtrains.debug.notchtest.Phase5ANotchTestManager;
import dev.edudio.createadvancedtrains.debug.notchtest.Phase5ANotchTestManager.NotchStatus;
import dev.edudio.createadvancedtrains.network.ModNetwork;
import dev.edudio.createadvancedtrains.train.TrainController;
import dev.edudio.createadvancedtrains.train.TrainControllerManager;
import dev.edudio.createadvancedtrains.train.query.CreateTrainQueryUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Samples all Create trains without creating or updating CAT controllers.
 */
@Mod.EventBusSubscriber(modid = CreateAdvancedTrains.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrainStatusHudServerEvents {

    private static final int SYNC_INTERVAL_TICKS = 4;
    private static final Map<UUID, Double> PREVIOUS_SPEEDS = new HashMap<>();
    private static final Map<UUID, Double> MEASURED_ACCELERATIONS = new HashMap<>();

    private TrainStatusHudServerEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        GlobalRailwayManager railwayManager = Create.RAILWAYS.sided(level);
        if (railwayManager == null) {
            clearState();
            return;
        }

        Set<UUID> currentTrainIds = new HashSet<>();
        for (Train train : railwayManager.trains.values()) {
            currentTrainIds.add(train.id);
            updateMeasuredAcceleration(train);
        }

        PREVIOUS_SPEEDS.keySet().removeIf(trainId -> !currentTrainIds.contains(trainId));
        MEASURED_ACCELERATIONS.keySet().removeIf(trainId -> !currentTrainIds.contains(trainId));
        TrainTargetSpeedTracker.INSTANCE.retainAll(currentTrainIds);

        if (server.getTickCount() % SYNC_INTERVAL_TICKS != 0
                || server.getPlayerList().getPlayerCount() == 0) {
            return;
        }

        List<TrainStatusHudEntry> entries = new ArrayList<>(currentTrainIds.size());
        for (Train train : railwayManager.trains.values()) {
            entries.add(capture(train));
        }
        entries.sort(Comparator.comparing(entry -> entry.trainId().toString()));

        ModNetwork.CHANNEL.send(
                PacketDistributor.ALL.noArg(),
                new TrainStatusHudPacket(entries));
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension() == Level.OVERWORLD) {
            clearState();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        clearState();
    }

    private static void updateMeasuredAcceleration(Train train) {
        double currentSpeed = train.speed;
        Double previousSpeed = PREVIOUS_SPEEDS.put(train.id, currentSpeed);
        if (previousSpeed == null
                || !Double.isFinite(previousSpeed)
                || !Double.isFinite(currentSpeed)) {
            MEASURED_ACCELERATIONS.put(train.id, Double.NaN);
            return;
        }

        double measuredAcceleration = (Math.abs(currentSpeed) - Math.abs(previousSpeed))
                * TICKS_PER_SECOND_SQUARED;
        MEASURED_ACCELERATIONS.put(train.id, measuredAcceleration);
    }

    private static TrainStatusHudEntry capture(Train train) {
        TargetSpeeds observedTargets = TrainTargetSpeedTracker.INSTANCE.get(train.id);
        double createTargetSpeed = observedTargets == null
                ? train.targetSpeed
                : observedTargets.createTargetSpeedBlocksPerTick();
        double atoTargetSpeed = observedTargets == null
                ? train.targetSpeed
                : observedTargets.atoTargetSpeedBlocksPerTick();
        NotchStatus notchStatus = Phase5ANotchTestManager.INSTANCE.getNotchStatus(train.id);

        return new TrainStatusHudEntry(
                train.id,
                toBlocksPerSecond(train.speed),
                currentNotch(train, notchStatus),
                toBlocksPerSecond(createTargetSpeed),
                toBlocksPerSecond(atoTargetSpeed),
                MEASURED_ACCELERATIONS.getOrDefault(train.id, Double.NaN),
                toBlocksPerSecondSquared(Math.abs(train.acceleration())),
                CreateTrainQueryUtil.queryNextStopDistance(train));
    }

    private static Notch currentNotch(Train train, NotchStatus phase5AStatus) {
        if (phase5AStatus.controlApplied()) {
            return phase5AStatus.commandedNotch();
        }

        TrainController controller = TrainControllerManager.INSTANCE.get(train.id);
        if (controller == null) {
            return Notch.N;
        }
        return controller.getLastAtoControlResult()
                .flatMap(result -> result.notchSelection())
                .map(selection -> selection.requestedNotch())
                .orElse(Notch.N);
    }

    private static double toBlocksPerSecond(double blocksPerTick) {
        return blocksPerTick * TICKS_PER_SECOND;
    }

    private static double toBlocksPerSecondSquared(double blocksPerTickSquared) {
        return blocksPerTickSquared * TICKS_PER_SECOND_SQUARED;
    }

    private static void clearState() {
        PREVIOUS_SPEEDS.clear();
        MEASURED_ACCELERATIONS.clear();
        TrainTargetSpeedTracker.INSTANCE.clear();
    }
}
