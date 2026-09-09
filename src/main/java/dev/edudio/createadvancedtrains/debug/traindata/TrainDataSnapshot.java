package dev.edudio.createadvancedtrains.debug.traindata;

import java.util.UUID;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.station.GlobalStation;

import net.createmod.catnip.data.Couple;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Immutable, read-only values sampled from a Create train.
 */
public record TrainDataSnapshot(
        UUID trainId,
        long serverTick,
        long levelGameTime,
        double speedBlocksPerTick,
        double speedBlocksPerSecond,
        double targetSpeedBlocksPerTick,
        double targetSpeedBlocksPerSecond,
        double accelerationBlocksPerTickSquared,
        double accelerationBlocksPerSecondSquared,
        NavigationSnapshot navigation) {

    private static final double TICKS_PER_SECOND = 20.0;
    private static final double TICKS_PER_SECOND_SQUARED = TICKS_PER_SECOND * TICKS_PER_SECOND;

    public static TrainDataSnapshot capture(
            Train train,
            long serverTick,
            long levelGameTime) {
        double speedBlocksPerTick = train.speed;
        double targetSpeedBlocksPerTick = train.targetSpeed;
        double accelerationBlocksPerTickSquared = train.acceleration();

        return new TrainDataSnapshot(
                train.id,
                serverTick,
                levelGameTime,
                speedBlocksPerTick,
                speedBlocksPerTick * TICKS_PER_SECOND,
                targetSpeedBlocksPerTick,
                targetSpeedBlocksPerTick * TICKS_PER_SECOND,
                accelerationBlocksPerTickSquared,
                accelerationBlocksPerTickSquared * TICKS_PER_SECOND_SQUARED,
                captureNavigation(train));
    }

    private static NavigationSnapshot captureNavigation(Train train) {
        if (train.navigation == null) {
            return new NavigationSnapshot(
                    "Create Navigation leading TravellingPoint",
                    null,
                    null,
                    null,
                    "navigation_unavailable");
        }

        GlobalStation destination = train.navigation.destination;
        if (destination == null) {
            return new NavigationSnapshot(
                    "Create Navigation leading TravellingPoint",
                    null,
                    null,
                    null,
                    "no_destination");
        }

        LeadingTravellingPointSnapshot leadingTravellingPoint = captureLeadingTravellingPoint(train);
        DestinationSnapshot destinationSnapshot = captureDestination(train.graph, destination);

        String destinationAvailability = destinationSnapshot.edgePoint() == null
                ? "destination_edge_location_unavailable"
                : "available";

        return new NavigationSnapshot(
                "Create Navigation leading TravellingPoint",
                leadingTravellingPoint,
                destinationSnapshot,
                train.navigation.distanceToDestination,
                destinationAvailability);
    }

    private static LeadingTravellingPointSnapshot captureLeadingTravellingPoint(Train train) {
        if (train.carriages.isEmpty()) {
            return null;
        }

        TravellingPoint leadingPoint;
        try {
            leadingPoint = train.navigation.destinationBehindTrain
                    ? train.carriages.get(train.carriages.size() - 1).getTrailingPoint()
                    : train.carriages.get(0).getLeadingPoint();
        } catch (RuntimeException ignored) {
            return null;
        }

        RailPositionSnapshot railPosition = captureTravellingPoint(train.graph, leadingPoint);
        return railPosition == null ? null : new LeadingTravellingPointSnapshot(railPosition);
    }

    private static RailPositionSnapshot captureTravellingPoint(
            TrackGraph graph,
            TravellingPoint point) {
        if (point == null || point.node1 == null || point.node2 == null) {
            return null;
        }

        TrackNodeLocationSnapshot node1 = captureNodeLocation(point.node1.getLocation());
        TrackNodeLocationSnapshot node2 = captureNodeLocation(point.node2.getLocation());
        WorldPositionSnapshot worldPosition = null;

        if (point.edge != null && point.edge.getLength() > 0.0) {
            try {
                worldPosition = captureWorldPosition(
                        point.node1.getLocation().getDimension(),
                        point.getPosition(graph));
            } catch (RuntimeException ignored) {
                // The structured rail position remains useful if a world position cannot be resolved.
            }
        }

        return new RailPositionSnapshot(
                graphId(graph),
                node1,
                node2,
                point.position,
                point.upsideDown,
                worldPosition);
    }

    private static DestinationSnapshot captureDestination(
            TrackGraph graph,
            GlobalStation destination) {
        RailEdgePointSnapshot edgePoint = captureEdgePoint(graph, destination);

        return new DestinationSnapshot(
                "GlobalStation",
                destination.id == null ? null : destination.id.toString(),
                edgePoint);
    }

    private static RailEdgePointSnapshot captureEdgePoint(
            TrackGraph graph,
            GlobalStation destination) {
        Couple<TrackNodeLocation> edgeLocation = destination.edgeLocation;
        if (edgeLocation == null
                || edgeLocation.getFirst() == null
                || edgeLocation.getSecond() == null) {
            return null;
        }

        TrackNodeLocationSnapshot endpoint1 = captureNodeLocation(edgeLocation.getFirst());
        TrackNodeLocationSnapshot endpoint2 = captureNodeLocation(edgeLocation.getSecond());

        Double resolvedPositionOnEdgeBlocks = null;
        WorldPositionSnapshot worldPosition = null;

        if (graph != null) {
            try {
                TrackNode node1 = graph.locateNode(edgeLocation.getFirst());
                TrackNode node2 = graph.locateNode(edgeLocation.getSecond());

                if (node1 != null && node2 != null) {
                    TrackEdge edge = graph.getConnection(Couple.create(node1, node2));

                    if (edge != null && edge.getLength() > 0.0) {
                        double positionOnEdge = destination.getLocationOn(edge);
                        Vec3 position = edge.getPosition(graph, positionOnEdge / edge.getLength());

                        resolvedPositionOnEdgeBlocks = positionOnEdge;
                        worldPosition = captureWorldPosition(
                                edgeLocation.getFirst().getDimension(),
                                position);
                    }
                }
            } catch (RuntimeException ignored) {
                // Keep the raw GlobalStation edge point even if graph resolution is unavailable.
            }
        }

        return new RailEdgePointSnapshot(
                graphId(graph),
                endpoint1,
                endpoint2,
                destination.position,
                resolvedPositionOnEdgeBlocks,
                worldPosition);
    }

    private static TrackNodeLocationSnapshot captureNodeLocation(TrackNodeLocation location) {
        if (location == null) {
            return null;
        }

        Vec3 worldPosition = location.getLocation();

        return new TrackNodeLocationSnapshot(
                dimensionId(location.getDimension()),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.yOffsetPixels,
                worldPosition.x,
                worldPosition.y,
                worldPosition.z);
    }

    private static WorldPositionSnapshot captureWorldPosition(
            ResourceKey<Level> dimension,
            Vec3 position) {
        if (position == null
                || !Double.isFinite(position.x)
                || !Double.isFinite(position.y)
                || !Double.isFinite(position.z)) {
            return null;
        }

        return new WorldPositionSnapshot(
                dimensionId(dimension),
                position.x,
                position.y,
                position.z);
    }

    private static String graphId(TrackGraph graph) {
        return graph == null || graph.id == null ? null : graph.id.toString();
    }

    private static String dimensionId(ResourceKey<Level> dimension) {
        return dimension == null ? null : dimension.location().toString();
    }

    public record NavigationSnapshot(
            String leadingPointReference,
            LeadingTravellingPointSnapshot leadingTravellingPoint,
            DestinationSnapshot destination,
            Double distanceToDestinationBlocks,
            String destinationAvailability) {
    }

    public record LeadingTravellingPointSnapshot(
            RailPositionSnapshot railPosition) {
    }

    public record DestinationSnapshot(
            String type,
            String stationId,
            RailEdgePointSnapshot edgePoint) {
    }

    public record RailPositionSnapshot(
            String graphId,
            TrackNodeLocationSnapshot node1,
            TrackNodeLocationSnapshot node2,
            double positionOnEdgeBlocks,
            boolean upsideDown,
            WorldPositionSnapshot worldPosition) {
    }

    public record RailEdgePointSnapshot(
            String graphId,
            TrackNodeLocationSnapshot endpoint1,
            TrackNodeLocationSnapshot endpoint2,
            double storedPositionBlocks,
            Double resolvedPositionOnEdgeBlocks,
            WorldPositionSnapshot worldPosition) {
    }

    public record TrackNodeLocationSnapshot(
            String dimension,
            int networkX,
            int networkY,
            int networkZ,
            int yOffsetPixels,
            double worldX,
            double worldY,
            double worldZ) {
    }

    public record WorldPositionSnapshot(
            String dimension,
            double x,
            double y,
            double z) {
    }
}
