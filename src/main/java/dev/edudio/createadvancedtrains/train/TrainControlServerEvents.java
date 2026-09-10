package dev.edudio.createadvancedtrains.train;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.GlobalRailwayManager;

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
 * Refreshes per-train shared observations before the server performs train
 * control for the tick. This boundary never applies a target speed.
 */
@Mod.EventBusSubscriber(modid = CreateAdvancedTrains.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrainControlServerEvents {

    private TrainControlServerEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerTickStart(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        TrainControllerManager.INSTANCE.beginServerTick(
                (long) event.getServer().getTickCount() + 1L);

        ServerLevel level = event.getServer().overworld();
        GlobalRailwayManager railwayManager = Create.RAILWAYS.sided(level);
        if (railwayManager == null) {
            TrainControllerManager.INSTANCE.clear();
            return;
        }

        TrainControllerManager.INSTANCE.refreshAllSharedState(railwayManager.trains.values());
        TrainControllerManager.INSTANCE.removeMissing(railwayManager.trains.values());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension() == Level.OVERWORLD) {
            TrainControllerManager.INSTANCE.clear();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TrainControllerManager.INSTANCE.clear();
    }
}
