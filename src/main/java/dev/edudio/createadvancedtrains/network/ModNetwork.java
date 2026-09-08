package dev.edudio.createadvancedtrains.network;

import dev.edudio.createadvancedtrains.CreateAdvancedTrains;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CreateAdvancedTrains.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int packetId = 0;

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(
                packetId++,
                TrainDebugPacket.class,
                TrainDebugPacket::encode,
                TrainDebugPacket::decode,
                TrainDebugPacket::handle);
    }
}