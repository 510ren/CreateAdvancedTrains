package dev.edudio.createadvancedtrains.debug.traindata;

import static dev.edudio.createadvancedtrains.constants.UnitConstants.TICKS_PER_SECOND;
import static dev.edudio.createadvancedtrains.constants.UnitConstants.TICKS_PER_SECOND_SQUARED;

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
 * @param trainId 対象列車を識別するUUID。
 * @param serverTick 処理対象となるserver tick。
 * @param levelGameTime 仕様書に個別説明がないため、現在の処理内容から推定した、{@code levelGameTime}として使用される入力値。
 * @param speedBlocksPerTick 仕様書に個別説明がないため、{@code speedBlocksPerTick}が示すCreate境界の速度。単位はblocks/tick。
 * @param speedBlocksPerSecond 仕様書に個別説明がないため、{@code speedBlocksPerSecond}が示す速度。単位はblocks/s。
 * @param targetSpeedBlocksPerTick 仕様書に個別説明がないため、{@code targetSpeedBlocksPerTick}が示すCreate境界の速度。単位はblocks/tick。
 * @param targetSpeedBlocksPerSecond 仕様書に個別説明がないため、{@code targetSpeedBlocksPerSecond}が示す速度。単位はblocks/s。
 * @param accelerationBlocksPerTickSquared 仕様書に個別説明がないため、{@code accelerationBlocksPerTickSquared}が示すCreate境界の加速度。単位はblocks/tick^2。
 * @param accelerationBlocksPerSecondSquared 仕様書に個別説明がないため、{@code accelerationBlocksPerSecondSquared}が示す加速度。単位はblocks/s^2。
 * @param navigation 仕様書に個別説明がないため、{@code navigation}が示すNavigationまたは目的地情報。
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

    /**
     * 現在状態を読み取り専用スナップショットとして取得します。
     * @param train 対象となるCreate列車。
     * @param serverTick 処理対象となるserver tick。
     * @param levelGameTime 仕様書に個別説明がないため、現在の処理内容から推定した、{@code levelGameTime}として使用される入力値。
     * @return 処理によって得られた結果。
     */
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

    /**
     * 仕様書に独立した関数契約がないため、現在状態を読み取り、{@code captureNavigation}が示すスナップショットを生成します。
     * @param train 対象となるCreate列車。
     * @return 処理によって得られた結果。
     */
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

    /**
     * 仕様書に独立した関数契約がないため、現在状態を読み取り、{@code captureLeadingTravellingPoint}が示すスナップショットを生成します。
     * @param train 対象となるCreate列車。
     * @return 処理または計算によって得られた数値。
     */
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

    /**
     * 仕様書に独立した関数契約がないため、現在状態を読み取り、{@code captureTravellingPoint}が示すスナップショットを生成します。
     * @param graph 仕様書に個別説明がないため、現在の処理内容から推定した、{@code graph}として使用される入力値。
     * @param point 仕様書に個別説明がないため、現在の処理内容から推定した、{@code point}として使用される入力値。
     * @return 処理によって得られた結果。
     */
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

    /**
     * 仕様書に独立した関数契約がないため、現在状態を読み取り、{@code captureDestination}が示すスナップショットを生成します。
     * @param graph 仕様書に個別説明がないため、現在の処理内容から推定した、{@code graph}として使用される入力値。
     * @param destination 仕様書に個別説明がないため、{@code destination}が示すNavigationまたは目的地情報。
     * @return 処理によって得られた結果。
     */
    private static DestinationSnapshot captureDestination(
            TrackGraph graph,
            GlobalStation destination) {
        RailEdgePointSnapshot edgePoint = captureEdgePoint(graph, destination);

        return new DestinationSnapshot(
                "GlobalStation",
                destination.id == null ? null : destination.id.toString(),
                edgePoint);
    }

    /**
     * 仕様書に独立した関数契約がないため、現在状態を読み取り、{@code captureEdgePoint}が示すスナップショットを生成します。
     * @param graph 仕様書に個別説明がないため、現在の処理内容から推定した、{@code graph}として使用される入力値。
     * @param destination 仕様書に個別説明がないため、{@code destination}が示すNavigationまたは目的地情報。
     * @return 処理または計算によって得られた数値。
     */
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

    /**
     * 仕様書に独立した関数契約がないため、現在状態を読み取り、{@code captureNodeLocation}が示すスナップショットを生成します。
     * @param location 仕様書に個別説明がないため、現在の処理内容から推定した、{@code location}として使用される入力値。
     * @return 処理によって得られた結果。
     */
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

    /**
     * 仕様書に独立した関数契約がないため、現在状態を読み取り、{@code captureWorldPosition}が示すスナップショットを生成します。
     * @param dimension 仕様書に個別説明がないため、現在の処理内容から推定した、{@code dimension}として使用される入力値。
     * @param position 仕様書に個別説明がないため、{@code position}が示す距離または位置。単位は呼出元の境界定義に従います。
     * @return 処理によって得られた結果。
     */
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

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code graphId}としてまとめられている処理を実行します。
     * @param graph 仕様書に個別説明がないため、現在の処理内容から推定した、{@code graph}として使用される入力値。
     * @return 処理によって得られた結果。
     */
    private static String graphId(TrackGraph graph) {
        return graph == null || graph.id == null ? null : graph.id.toString();
    }

    /**
     * 仕様書に独立した関数契約がないため、現在の実装で{@code dimensionId}としてまとめられている処理を実行します。
     * @param dimension 仕様書に個別説明がないため、現在の処理内容から推定した、{@code dimension}として使用される入力値。
     * @return 処理によって得られた結果。
     */
    private static String dimensionId(ResourceKey<Level> dimension) {
        return dimension == null ? null : dimension.location().toString();
    }

    /**
     * 仕様書に独立した型契約がないため、現在の利用箇所から推定した不変データを保持します。
     * @param leadingPointReference 仕様書に個別説明がないため、現在の処理内容から推定した、{@code leadingPointReference}として使用される入力値。
     * @param leadingTravellingPoint 仕様書に個別説明がないため、現在の処理内容から推定した、{@code leadingTravellingPoint}として使用される入力値。
     * @param destination 仕様書に個別説明がないため、{@code destination}が示すNavigationまたは目的地情報。
     * @param distanceToDestinationBlocks 仕様書に個別説明がないため、{@code distanceToDestinationBlocks}が示す距離または位置量。単位はblocks。
     * @param destinationAvailability 仕様書に個別説明がないため、{@code destinationAvailability}が示すNavigationまたは目的地情報。
     */
    public record NavigationSnapshot(
            String leadingPointReference,
            LeadingTravellingPointSnapshot leadingTravellingPoint,
            DestinationSnapshot destination,
            Double distanceToDestinationBlocks,
            String destinationAvailability) {
    }

    /**
     * 仕様書に独立した型契約がないため、現在の利用箇所から推定した不変データを保持します。
     * @param railPosition 仕様書に個別説明がないため、{@code railPosition}が示す距離または位置。単位は呼出元の境界定義に従います。
     */
    public record LeadingTravellingPointSnapshot(
            RailPositionSnapshot railPosition) {
    }

    /**
     * 仕様書に独立した型契約がないため、現在の利用箇所から推定した不変データを保持します。
     * @param type 仕様書に個別説明がないため、現在の処理内容から推定した、{@code type}として使用される入力値。
     * @param stationId 仕様書に個別説明がないため、{@code stationId}が示す対象識別子。
     * @param edgePoint 仕様書に個別説明がないため、現在の処理内容から推定した、{@code edgePoint}として使用される入力値。
     */
    public record DestinationSnapshot(
            String type,
            String stationId,
            RailEdgePointSnapshot edgePoint) {
    }

    /**
     * 仕様書に独立した型契約がないため、現在の利用箇所から推定した不変データを保持します。
     * @param graphId 仕様書に個別説明がないため、{@code graphId}が示す対象識別子。
     * @param node1 仕様書に個別説明がないため、現在の処理内容から推定した、{@code node1}として使用される入力値。
     * @param node2 仕様書に個別説明がないため、現在の処理内容から推定した、{@code node2}として使用される入力値。
     * @param positionOnEdgeBlocks 仕様書に個別説明がないため、{@code positionOnEdgeBlocks}が示す距離または位置量。単位はblocks。
     * @param upsideDown 仕様書に個別説明がないため、{@code upsideDown}が示す条件の有効・無効を表す値。
     * @param worldPosition 仕様書に個別説明がないため、{@code worldPosition}が示す距離または位置。単位は呼出元の境界定義に従います。
     */
    public record RailPositionSnapshot(
            String graphId,
            TrackNodeLocationSnapshot node1,
            TrackNodeLocationSnapshot node2,
            double positionOnEdgeBlocks,
            boolean upsideDown,
            WorldPositionSnapshot worldPosition) {
    }

    /**
     * 仕様書に独立した型契約がないため、現在の利用箇所から推定した不変データを保持します。
     * @param graphId 仕様書に個別説明がないため、{@code graphId}が示す対象識別子。
     * @param endpoint1 仕様書に個別説明がないため、現在の処理内容から推定した、{@code endpoint1}として使用される入力値。
     * @param endpoint2 仕様書に個別説明がないため、現在の処理内容から推定した、{@code endpoint2}として使用される入力値。
     * @param storedPositionBlocks 仕様書に個別説明がないため、{@code storedPositionBlocks}が示す距離または位置量。単位はblocks。
     * @param resolvedPositionOnEdgeBlocks 仕様書に個別説明がないため、{@code resolvedPositionOnEdgeBlocks}が示す距離または位置量。単位はblocks。
     * @param worldPosition 仕様書に個別説明がないため、{@code worldPosition}が示す距離または位置。単位は呼出元の境界定義に従います。
     */
    public record RailEdgePointSnapshot(
            String graphId,
            TrackNodeLocationSnapshot endpoint1,
            TrackNodeLocationSnapshot endpoint2,
            double storedPositionBlocks,
            Double resolvedPositionOnEdgeBlocks,
            WorldPositionSnapshot worldPosition) {
    }

    /**
     * 仕様書に独立した型契約がないため、現在の利用箇所から推定した不変データを保持します。
     * @param dimension 仕様書に個別説明がないため、現在の処理内容から推定した、{@code dimension}として使用される入力値。
     * @param networkX 仕様書に個別説明がないため、現在の処理内容から推定した、{@code networkX}として使用される入力値。
     * @param networkY 仕様書に個別説明がないため、現在の処理内容から推定した、{@code networkY}として使用される入力値。
     * @param networkZ 仕様書に個別説明がないため、現在の処理内容から推定した、{@code networkZ}として使用される入力値。
     * @param yOffsetPixels 仕様書に個別説明がないため、現在の処理内容から推定した、{@code yOffsetPixels}として使用される入力値。
     * @param worldX 仕様書に個別説明がないため、現在の処理内容から推定した、{@code worldX}として使用される入力値。
     * @param worldY 仕様書に個別説明がないため、現在の処理内容から推定した、{@code worldY}として使用される入力値。
     * @param worldZ 仕様書に個別説明がないため、現在の処理内容から推定した、{@code worldZ}として使用される入力値。
     */
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

    /**
     * 仕様書に独立した型契約がないため、現在の利用箇所から推定した不変データを保持します。
     * @param dimension 仕様書に個別説明がないため、現在の処理内容から推定した、{@code dimension}として使用される入力値。
     * @param x 仕様書に個別説明がないため、現在の処理内容から推定した、{@code x}として使用される入力値。
     * @param y 仕様書に個別説明がないため、現在の処理内容から推定した、{@code y}として使用される入力値。
     * @param z 仕様書に個別説明がないため、現在の処理内容から推定した、{@code z}として使用される入力値。
     */
    public record WorldPositionSnapshot(
            String dimension,
            double x,
            double y,
            double z) {
    }
}
