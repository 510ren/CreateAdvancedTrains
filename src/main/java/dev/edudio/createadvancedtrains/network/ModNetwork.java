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

    /**
     * このクラスのインスタンスを初期化します。
     */
    private ModNetwork() {
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の処理内容から推定すると、{@code register}に対応する処理を実行します。
     */
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
