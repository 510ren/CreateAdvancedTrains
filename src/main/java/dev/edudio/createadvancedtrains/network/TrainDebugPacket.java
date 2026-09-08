package dev.edudio.createadvancedtrains.network;

import dev.edudio.createadvancedtrains.train.TrainDebugData;
import dev.edudio.createadvancedtrains.train.client.TrainDebugClientData;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class TrainDebugPacket {

    private final List<TrainDebugData> trains;

    public TrainDebugPacket(List<TrainDebugData> trains) {
        this.trains = trains;
    }

    public static void encode(
            TrainDebugPacket packet,
            FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.trains.size());

        for (TrainDebugData train : packet.trains) {
            buffer.writeUUID(train.trainId());
            buffer.writeDouble(train.speed());
            buffer.writeDouble(train.createTargetSpeed());
            buffer.writeDouble(train.atoTargetSpeed());
        }
    }

    public static TrainDebugPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();

        List<TrainDebugData> trains = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            UUID trainId = buffer.readUUID();

            double speed = buffer.readDouble();
            double createTargetSpeed = buffer.readDouble();
            double atoTargetSpeed = buffer.readDouble();

            trains.add(new TrainDebugData(
                    trainId,
                    speed,
                    createTargetSpeed,
                    atoTargetSpeed));
        }

        return new TrainDebugPacket(trains);
    }

    public static void handle(
            TrainDebugPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            TrainDebugClientData.setTrains(packet.trains);
        });

        context.setPacketHandled(true);
    }
}