package dev.edudio.createadvancedtrains.debug.notchtest;

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
 * Server lifecycle boundary for the Phase 5A fixed-notch test.
 */
@Mod.EventBusSubscriber(modid = CreateAdvancedTrains.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Phase5ANotchTestEvents {

    /**
     * このクラスのインスタンスを初期化します。
     */
    private Phase5ANotchTestEvents() {
    }

    /**
     * ServerTickStartイベントを処理します。
     * @param event Forgeから通知されたイベント。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerTickStart(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            Phase5ANotchTestManager.INSTANCE.onServerTickStart(event.getServer());
        }
    }

    /**
     * ServerTickEndイベントを処理します。
     * @param event Forgeから通知されたイベント。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTickEnd(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Phase5ANotchTestManager.INSTANCE.onServerTickEnd(event.getServer());
        }
    }

    /**
     * LevelUnloadイベントを処理します。
     * @param event Forgeから通知されたイベント。
     */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension() == Level.OVERWORLD) {
            Phase5ANotchTestManager.INSTANCE.onOverworldUnload();
        }
    }

    /**
     * ServerStoppingイベントを処理します。
     * @param event Forgeから通知されたイベント。
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        Phase5ANotchTestManager.INSTANCE.onServerStopping();
    }
}
