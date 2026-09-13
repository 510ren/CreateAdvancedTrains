package dev.edudio.createadvancedtrains.debug.traindata;

import dev.edudio.createadvancedtrains.CreateAdvancedTrains;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Connects passive train data logging to server lifecycle events.
 */
@Mod.EventBusSubscriber(modid = CreateAdvancedTrains.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrainDataDebugEvents {

    /**
     * このクラスのインスタンスを初期化します。
     */
    private TrainDataDebugEvents() {
    }

    /**
     * ServerTickイベントを処理します。
     * @param event Forgeから通知されたイベント。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            TrainDataDebugger.INSTANCE.onServerTickStart(event.getServer());
            return;
        }
        if (event.phase == TickEvent.Phase.END) {
            TrainDataDebugger.INSTANCE.onServerTick(event.getServer());
        }
    }

    /**
     * LevelUnloadイベントを処理します。
     * @param event Forgeから通知されたイベント。
     */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        if (level.dimension() == Level.OVERWORLD) {
            TrainDataDebugger.INSTANCE.onOverworldUnload();
        }
    }

    /**
     * ServerStoppingイベントを処理します。
     * @param event Forgeから通知されたイベント。
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TrainDataDebugger.INSTANCE.onServerStopping();
    }
}
