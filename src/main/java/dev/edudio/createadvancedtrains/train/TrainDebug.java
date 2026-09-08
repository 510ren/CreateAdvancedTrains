package dev.edudio.createadvancedtrains.train;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.GlobalRailwayManager;
import com.simibubi.create.content.trains.entity.Train;

import net.minecraft.server.level.ServerLevel;

public final class TrainDebug {

    private TrainDebug() {
    }

    public static void logTrains(ServerLevel level) {
        GlobalRailwayManager railwayManager = Create.RAILWAYS.sided(level);

        if (railwayManager == null) {
            return;
        }

        for (Train train : railwayManager.trains.values()) {
            double speed = train.speed;
            double targetSpeed = train.targetSpeed;

            System.out.println(
                "[Create: Advanced Trains] "
                + "Train=" + train.id
                + " Speed=" + speed
                + " TargetSpeed=" + targetSpeed
            );
        }
    }
}