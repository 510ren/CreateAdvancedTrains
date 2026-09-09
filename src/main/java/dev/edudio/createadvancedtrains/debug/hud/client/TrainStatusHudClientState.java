package dev.edudio.createadvancedtrains.debug.hud.client;

import java.util.List;

import dev.edudio.createadvancedtrains.debug.hud.TrainStatusHudEntry;

/**
 * Latest immutable server snapshot used only by the client HUD.
 */
public final class TrainStatusHudClientState {

    private static volatile List<TrainStatusHudEntry> entries = List.of();

    private TrainStatusHudClientState() {
    }

    public static void setEntries(List<TrainStatusHudEntry> newEntries) {
        entries = List.copyOf(newEntries);
    }

    public static List<TrainStatusHudEntry> getEntries() {
        return entries;
    }

    public static void clear() {
        entries = List.of();
    }
}
