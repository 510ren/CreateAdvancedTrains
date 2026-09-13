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

    /**
     * このクラスのインスタンスを初期化します。
     */
    private TrainStatusHudOverlay() {
    }

    /**
     * RenderGuiイベントを処理します。
     * @param event Forgeから通知されたイベント。
     */
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

    /**
     * ClientLogoutイベントを処理します。
     * @param event Forgeから通知されたイベント。
     */
    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        TrainStatusHudClientState.clear();
    }

    /**
     * 仕様書に独立した関数契約がないため、入力値を{@code format}が示す形式へ整えます。
     * @param entry 仕様書に個別説明がないため、現在の処理内容から推定した、{@code entry}として使用される入力値。
     * @return 処理によって得られた結果。
     */
    private static String format(TrainStatusHudEntry entry) {
        return String.format(
                Locale.ROOT,
                "%s  V %s  A %s (base %s)  N %s  Create %s  ATO %s  D %s",
                entry.trainId().toString().substring(0, 8),
                number(entry.speedBlocksPerSecond()),
                number(entry.measuredAccelerationBlocksPerSecondSquared()),
                number(entry.createBaseAccelerationBlocksPerSecondSquared()),
                entry.currentNotch().name(),
                number(entry.createTargetSpeedBlocksPerSecond()),
                number(entry.atoTargetSpeedBlocksPerSecond()),
                distance(entry.stopTargetDistance()));
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code distance}としてまとめられている処理を実行します。
     * @param distance 仕様書に個別説明がないため、{@code distance}が示す距離または位置。単位は呼出元の境界定義に従います。
     * @return 処理によって得られた結果。
     */
    private static String distance(StopTargetDistance distance) {
        return distance.distanceBlocks().isPresent()
                ? number(distance.distanceBlocks().getAsDouble())
                : "--";
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code number}としてまとめられている処理を実行します。
     * @param value 処理対象の値。
     * @return 処理によって得られた結果。
     */
    private static String number(double value) {
        return Double.isFinite(value)
                ? String.format(Locale.ROOT, "%+.3f", value)
                : "n/a";
    }
}
