package dev.edudio.createadvancedtrains.debug.hud.client;

import java.util.List;

import dev.edudio.createadvancedtrains.debug.hud.TrainStatusHudEntry;

/**
 * Latest immutable server snapshot used only by the client HUD.
 */
public final class TrainStatusHudClientState {

    private static volatile List<TrainStatusHudEntry> entries = List.of();

    /**
     * このクラスのインスタンスを初期化します。
     */
    private TrainStatusHudClientState() {
    }

    /**
     * Entriesを設定します。
     * @param newEntries 仕様書に個別説明がないため、現在の処理内容から推定した、{@code newEntries}として使用される入力値。
     */
    public static void setEntries(List<TrainStatusHudEntry> newEntries) {
        entries = List.copyOf(newEntries);
    }

    /**
     * 現在のEntriesを返します。
     * @return 処理によって得られた結果。
     */
    public static List<TrainStatusHudEntry> getEntries() {
        return entries;
    }

    /**
     * 保持している状態を消去します。
     */
    public static void clear() {
        entries = List.of();
    }
}
