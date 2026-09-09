package dev.edudio.createadvancedtrains.utils;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nonnull;

import net.minecraft.network.chat.Component;

public class SendMessageToClient {
    public static void send(@Nonnull String message) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal(message), false);
        }
    }
}
