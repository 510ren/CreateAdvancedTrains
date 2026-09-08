package dev.edudio.createadvancedtrains.train.client;

import dev.edudio.createadvancedtrains.train.TrainDebugData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import dev.edudio.createadvancedtrains.CreateAdvancedTrains;

@Mod.EventBusSubscriber(modid = CreateAdvancedTrains.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrainDebugOverlay {

    private TrainDebugOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();

        int tickToSeconds = 20;

        int x = 10;
        int y = 10;

        guiGraphics.drawString(
                minecraft.font,
                "Create: Advanced Trains",
                x,
                y,
                0xFFFFFF);

        y += 12;

        for (TrainDebugData train : TrainDebugClientData.getTrains()) {

            String line = String.format(
                    "Train %s | Speed %.3f [blocks/s] | Create %.3f [blocks/s] | ATO %.3f [blocks/s]",
                    train.trainId().toString().substring(0, 8),
                    train.speed() * tickToSeconds,
                    train.createTargetSpeed() * tickToSeconds,
                    train.atoTargetSpeed() * tickToSeconds);

            guiGraphics.drawString(
                    minecraft.font,
                    line,
                    x,
                    y,
                    0xFFFFFF);

            y += 12;
        }
    }
}