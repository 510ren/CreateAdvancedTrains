package dev.edudio.createadvancedtrains.debug.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import dev.edudio.createadvancedtrains.control.notch.Notch;
import dev.edudio.createadvancedtrains.debug.hud.client.TrainStatusHudClientState;
import dev.edudio.createadvancedtrains.train.query.StopTargetDistance;
import dev.edudio.createadvancedtrains.train.query.StopTargetDistance.Source;
import dev.edudio.createadvancedtrains.train.query.StopTargetDistance.UnavailableReason;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server-to-client snapshot for the development train status HUD.
 */
public final class TrainStatusHudPacket {

    private static final int MAX_TRAINS_PER_PACKET = 4096;

    private final List<TrainStatusHudEntry> entries;

    /**
     * このクラスのインスタンスを初期化します。
     * @param entries 仕様書に個別説明がないため、現在の処理内容から推定した、{@code entries}として使用される入力値。
     */
    public TrainStatusHudPacket(List<TrainStatusHudEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    /**
     * 値をネットワーク送信用にエンコードします。
     * @param packet 仕様書に個別説明がないため、現在の処理内容から推定した、{@code packet}として使用される入力値。
     * @param buffer シリアライズまたはデシリアライズに使用するネットワークバッファ。
     */
    public static void encode(TrainStatusHudPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entries.size());
        for (TrainStatusHudEntry entry : packet.entries) {
            buffer.writeUUID(entry.trainId());
            buffer.writeDouble(entry.speedBlocksPerSecond());
            buffer.writeEnum(entry.currentNotch());
            buffer.writeDouble(entry.createTargetSpeedBlocksPerSecond());
            buffer.writeDouble(entry.atoTargetSpeedBlocksPerSecond());
            buffer.writeDouble(entry.measuredAccelerationBlocksPerSecondSquared());
            buffer.writeDouble(entry.createBaseAccelerationBlocksPerSecondSquared());
            StopTargetDistance stopTargetDistance = entry.stopTargetDistance();
            buffer.writeBoolean(stopTargetDistance.isAvailable());
            if (stopTargetDistance.isAvailable()) {
                buffer.writeDouble(stopTargetDistance.distanceBlocks().getAsDouble());
            }
            buffer.writeEnum(stopTargetDistance.source());
            buffer.writeEnum(stopTargetDistance.unavailableReason());
        }
    }

    /**
     * ネットワーク値をデコードします。
     * @param buffer シリアライズまたはデシリアライズに使用するネットワークバッファ。
     * @return 処理によって得られた結果。
     */
    public static TrainStatusHudPacket decode(FriendlyByteBuf buffer) {
        int entryCount = buffer.readVarInt();
        if (entryCount < 0 || entryCount > MAX_TRAINS_PER_PACKET) {
            throw new IllegalArgumentException("Invalid train HUD entry count: " + entryCount);
        }

        List<TrainStatusHudEntry> entries = new ArrayList<>(entryCount);
        for (int index = 0; index < entryCount; index++) {
            entries.add(new TrainStatusHudEntry(
                    buffer.readUUID(),
                    buffer.readDouble(),
                    buffer.readEnum(Notch.class),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    decodeStopTargetDistance(buffer)));
        }
        return new TrainStatusHudPacket(entries);
    }

    /**
     * 仕様書に独立した関数契約がないため、入力表現を解析し、{@code decodeStopTargetDistance}が示す値へ変換します。
     * @param buffer シリアライズまたはデシリアライズに使用するネットワークバッファ。
     * @return 処理によって得られた結果。
     */
    private static StopTargetDistance decodeStopTargetDistance(FriendlyByteBuf buffer) {
        boolean available = buffer.readBoolean();
        double distanceBlocks = available ? buffer.readDouble() : Double.NaN;
        Source source = buffer.readEnum(Source.class);
        UnavailableReason reason = buffer.readEnum(UnavailableReason.class);
        return available
                ? StopTargetDistance.available(distanceBlocks, source)
                : StopTargetDistance.unavailable(reason);
    }

    /**
     * 受信した処理要求を適用します。
     * @param packet 仕様書に個別説明がないため、現在の処理内容から推定した、{@code packet}として使用される入力値。
     * @param contextSupplier 受信処理コンテキストを供給する関数。
     */
    public static void handle(
            TrainStatusHudPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
            context.enqueueWork(() -> TrainStatusHudClientState.setEntries(packet.entries));
        }
        context.setPacketHandled(true);
    }
}
