package dev.edudio.createadvancedtrains.network;

import dev.edudio.createadvancedtrains.CreateAdvancedTrains;
import dev.edudio.createadvancedtrains.debug.hud.TrainStatusHudPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(CreateAdvancedTrains.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private ModNetwork() {
    }

    public static void register() {
        int messageId = 0;

        CHANNEL.registerMessage(
                messageId,
                TrainStatusHudPacket.class,
                TrainStatusHudPacket::encode,
                TrainStatusHudPacket::decode,
                TrainStatusHudPacket::handle);
    }
}
