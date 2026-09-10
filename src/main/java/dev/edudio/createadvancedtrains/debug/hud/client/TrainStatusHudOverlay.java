package dev.edudio.createadvancedtrains.debug.hud.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.edudio.createadvancedtrains.CreateAdvancedTrains;
import dev.edudio.createadvancedtrains.debug.hud.TrainStatusHudEntry;
import dev.edudio.createadvancedtrains.train.query.StopTargetDistance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Development HUD showing the server-observed state of every Create train.
 */
@Mod.EventBusSubscriber(modid = CreateAdvancedTrains.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrainStatusHudOverlay {

    private static final int LEFT = 6;
    private static final int TOP = 6;
    private static final int LINE_HEIGHT = 10;
    private static final int COLUMN_GAP = 12;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int HEADING_COLOR = 0x80D8FF;

    private TrainStatusHudOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        List<TrainStatusHudEntry> entries = TrainStatusHudClientState.getEntries();
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = minecraft.font;

        graphics.drawString(
                font,
                "CAT Train Debug [speed: blocks/s, acceleration: blocks/s^2, distance: blocks]",
                LEFT,
                TOP,
                HEADING_COLOR,
                true);

        if (entries.isEmpty()) {
            graphics.drawString(font, "No trains", LEFT, TOP + 12, TEXT_COLOR, true);
            return;
        }

        List<String> lines = new ArrayList<>(entries.size());
        int columnWidth = 0;
        for (TrainStatusHudEntry entry : entries) {
            String line = format(entry);
            lines.add(line);
            columnWidth = Math.max(columnWidth, font.width(line));
        }

        int rowsPerColumn = Math.max(
                1,
                (graphics.guiHeight() - TOP - 18) / LINE_HEIGHT);
        for (int index = 0; index < lines.size(); index++) {
            int column = index / rowsPerColumn;
            int row = index % rowsPerColumn;
            int x = LEFT + column * (columnWidth + COLUMN_GAP);
            int y = TOP + 14 + row * LINE_HEIGHT;
            graphics.drawString(font, lines.get(index), x, y, TEXT_COLOR, true);
        }
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        TrainStatusHudClientState.clear();
    }

    private static String format(TrainStatusHudEntry entry) {
        String notch = entry.commandedNotch() == entry.appliedNotch()
                ? entry.commandedNotch().name()
                : entry.commandedNotch().name() + ">" + entry.appliedNotch().name();
        if (entry.notchControlApplied()) {
            notch += "[active]";
        }

        return String.format(
                Locale.ROOT,
                "%s  V %s  A %s (base %s)  N %s  Create %s  ATO %s  D %s",
                entry.trainId().toString().substring(0, 8),
                number(entry.speedBlocksPerSecond()),
                number(entry.measuredAccelerationBlocksPerSecondSquared()),
                number(entry.createBaseAccelerationBlocksPerSecondSquared()),
                notch,
                number(entry.createTargetSpeedBlocksPerSecond()),
                number(entry.atoTargetSpeedBlocksPerSecond()),
                distance(entry.stopTargetDistance()));
    }

    private static String distance(StopTargetDistance distance) {
        return distance.distanceBlocks().isPresent()
                ? number(distance.distanceBlocks().getAsDouble())
                : "--";
    }

    private static String number(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.ROOT, "%+.3f", value)
                : "n/a";
    }
}
