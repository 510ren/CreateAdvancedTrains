package dev.edudio.createadvancedtrains.train;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.GlobalRailwayManager;
import com.simibubi.create.content.trains.entity.Train;

import dev.edudio.createadvancedtrains.CreateAdvancedTrains;
import dev.edudio.createadvancedtrains.network.ModNetwork;
import dev.edudio.createadvancedtrains.network.TrainDebugPacket;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.EventPriority;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = CreateAdvancedTrains.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrainDebugEvents {
    private TrainDebugEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLevelTick(TickEvent.LevelTickEvent event) {

        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (event.level.isClientSide()) {
            return;
        }

        ServerLevel level = (ServerLevel) event.level;

        if (level.dimension() != Level.OVERWORLD) {
            return;
        }

        MinecraftServer server = level.getServer();

        GlobalRailwayManager railwayManager = Create.RAILWAYS.sided(level);

        if (railwayManager == null) {
            return;
        }

        Iterable<Train> trains = railwayManager.trains.values();

        // TrainControllerを更新
        TrainControllerManager.INSTANCE.updateAll(trains);

        // Createから消えたTrainのControllerを削除
        TrainControllerManager.INSTANCE.removeMissing(trains);

        // デバッグ表示用データ
        if (server.getTickCount() % 20 != 0) {
            return;
        }

        List<TrainDebugData> debugData = new ArrayList<>();

        for (Train train : trains) {

            TrainController controller = TrainControllerManager.INSTANCE.get(train.id);

            if (controller == null) {
                controller = TrainControllerManager.INSTANCE.getOrCreate(train);
            }

            TrainState state = controller.getState();

            debugData.add(new TrainDebugData(
                    controller.getTrainId(),
                    train.speed,
                    state.createTargetSpeed(),
                    state.atoTargetSpeed()));
        }

        TrainDebugPacket packet = new TrainDebugPacket(debugData);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {

            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    packet);
        }
    }
}