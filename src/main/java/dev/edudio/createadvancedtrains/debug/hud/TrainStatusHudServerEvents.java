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

    /**
     * このクラスのインスタンスを初期化します。
     */
    private TrainStatusHudServerEvents() {
    }

    /**
     * ServerTickイベントを処理します。
     * @param event Forgeから通知されたイベント。
     */
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

    /**
     * LevelUnloadイベントを処理します。
     * @param event Forgeから通知されたイベント。
     */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension() == Level.OVERWORLD) {
            clearState();
        }
    }

    /**
     * ServerStoppingイベントを処理します。
     * @param event Forgeから通知されたイベント。
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        clearState();
    }

    /**
     * 仕様書に独立した関数契約がないため、{@code updateMeasuredAcceleration}が対象とする保持状態を最新の入力で更新します。
     * @param train 対象となるCreate列車。
     */
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

    /**
     * 現在状態を読み取り専用スナップショットとして取得します。
     * @param train 対象となるCreate列車。
     * @return 処理によって得られた結果。
     */
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

    /**
     * 仕様書に独立した関数契約がないため、現在保持している{@code current notch}を返します。
     * @param train 対象となるCreate列車。
     * @param phase5AStatus 仕様書に個別説明がないため、{@code phase5AStatus}が示す現在または判定後の状態。
     * @return 処理によって得られた結果。
     */
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

    /**
     * 仕様書に独立した関数契約がないため、入力値を{@code toBlocksPerSecond}が示す単位または表現へ変換します。
     * @param blocksPerTick 仕様書に個別説明がないため、{@code blocksPerTick}が示すCreate境界の速度。単位はblocks/tick。
     * @return 処理または計算によって得られた数値。
     */
    private static double toBlocksPerSecond(double blocksPerTick) {
        return blocksPerTick * TICKS_PER_SECOND;
    }

    /**
     * 仕様書に独立した関数契約がないため、入力値を{@code toBlocksPerSecondSquared}が示す単位または表現へ変換します。
     * @param blocksPerTickSquared 仕様書に個別説明がないため、{@code blocksPerTickSquared}が示すCreate境界の加速度。単位はblocks/tick^2。
     * @return 処理または計算によって得られた数値。
     */
    private static double toBlocksPerSecondSquared(double blocksPerTickSquared) {
        return blocksPerTickSquared * TICKS_PER_SECOND_SQUARED;
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code clearState}としてまとめられている処理を実行します。
     */
    private static void clearState() {
        PREVIOUS_SPEEDS.clear();
        MEASURED_ACCELERATIONS.clear();
        TrainTargetSpeedTracker.INSTANCE.clear();
    }
}
