package dev.edudio.createadvancedtrains.debug.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import dev.edudio.createadvancedtrains.control.notch.Notch;
import dev.edudio.createadvancedtrains.debug.hud.client.TrainStatusHudClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server-to-client snapshot for the development train status HUD.
 */
public final class TrainStatusHudPacket {

    private static final int MAX_TRAINS_PER_PACKET = 4096;

    private final List<TrainStatusHudEntry> entries;

    public TrainStatusHudPacket(List<TrainStatusHudEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    public static void encode(TrainStatusHudPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entries.size());
        for (TrainStatusHudEntry entry : packet.entries) {
            buffer.writeUUID(entry.trainId());
            buffer.writeDouble(entry.speedBlocksPerSecond());
            buffer.writeEnum(entry.commandedNotch());
            buffer.writeEnum(entry.appliedNotch());
            buffer.writeBoolean(entry.notchControlApplied());
            buffer.writeDouble(entry.createTargetSpeedBlocksPerSecond());
            buffer.writeDouble(entry.atoTargetSpeedBlocksPerSecond());
            buffer.writeDouble(entry.measuredAccelerationBlocksPerSecondSquared());
            buffer.writeDouble(entry.createBaseAccelerationBlocksPerSecondSquared());
            buffer.writeDouble(entry.distanceToDestinationBlocks());
        }
    }

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
                    buffer.readEnum(Notch.class),
                    buffer.readBoolean(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble()));
        }
        return new TrainStatusHudPacket(entries);
    }

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
