package dev.edudio.createadvancedtrains.utils;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nonnull;

import net.minecraft.network.chat.Component;

public class SendMessageToClient {
    /**
     * 仕様書に独立した関数契約がないため、現在の処理内容から推定すると、{@code send}に対応する処理を実行します。
     * @param message 送信または処理するメッセージ。
     */
    public static void send(@Nonnull String message) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal(message), false);
        }
    }
}
