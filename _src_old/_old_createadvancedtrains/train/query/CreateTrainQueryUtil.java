package dev.edudio.createadvancedtrains.train.query;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import com.simibubi.create.content.trains.entity.Navigation;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.station.GlobalStation;

import dev.edudio.createadvancedtrains.train.query.StopTargetDistance.Source;
import dev.edudio.createadvancedtrains.train.query.StopTargetDistance.UnavailableReason;

/**
 * Server-side, read-only boundary for observations derived from Create trains.
 */
public final class CreateTrainQueryUtil {

    /**
     * このクラスのインスタンスを初期化します。
     */
    private CreateTrainQueryUtil() {
    }

    /**
     * Normalizes only Create's active Navigation destination. It never uses the
     * manual-station fallback whose signed-distance convention differs.
     * @param train 対象となるCreate列車。
     * @return 処理によって得られた結果。
     */
    public static NormalizedNavigationStop queryNormalizedNavigationStop(Train train) {
        if (train == null || train.navigation == null) {
            return NormalizedNavigationStop.invalid(
                    NavigationStopInvalidReason.NAVIGATION_UNAVAILABLE);
        }

        Navigation navigation = train.navigation;
        if (navigation.destination == null) {
            return NormalizedNavigationStop.noActiveDestination();
        }

        double distance = navigation.distanceToDestination;
        if (!Double.isFinite(distance)) {
            return NormalizedNavigationStop.invalid(
                    NavigationStopInvalidReason.DISTANCE_NOT_FINITE);
        }
        if (train.graph == null) {
            return NormalizedNavigationStop.invalid(
                    NavigationStopInvalidReason.GRAPH_UNAVAILABLE);
        }
        if (train.carriages == null || train.carriages.isEmpty()) {
            return NormalizedNavigationStop.invalid(
                    NavigationStopInvalidReason.CARRIAGES_UNAVAILABLE);
        }

        TravellingPoint leadingPoint = navigation.destinationBehindTrain
                ? train.carriages.get(train.carriages.size() - 1).getTrailingPoint()
                : train.carriages.get(0).getLeadingPoint();
        if (leadingPoint == null
                || leadingPoint.node1 == null
                || leadingPoint.node2 == null
                || leadingPoint.edge == null
                || !Double.isFinite(leadingPoint.position)) {
            return NormalizedNavigationStop.invalid(
                    NavigationStopInvalidReason.LEADING_TRAVELLING_POINT_INVALID);
        }
        if (navigation.destination.id == null
                || navigation.destination.edgeLocation == null
                || navigation.destination.edgeLocation.getFirst() == null
                || navigation.destination.edgeLocation.getSecond() == null
                || !Double.isFinite(navigation.destination.position)) {
            return NormalizedNavigationStop.invalid(
                    NavigationStopInvalidReason.DESTINATION_EDGE_POINT_INVALID);
        }

        NavigationTravelDirection direction = navigation.destinationBehindTrain
                ? NavigationTravelDirection.BACKWARD
                : NavigationTravelDirection.FORWARD;
        if (distance > 0.0) {
            return NormalizedNavigationStop.ahead(distance, direction);
        }
        if (distance == 0.0) {
            return NormalizedNavigationStop.arrivalPending(direction);
        }
        return NormalizedNavigationStop.pastDestination(distance, direction);
    }

    /**
     * Returns the signed distance in blocks to the next stop target without
     * starting navigation or mutating any Create train or graph state.
     * @param train 対象となるCreate列車。
     * @return 処理によって得られた結果。
     */
    public static StopTargetDistance queryNextStopDistance(Train train) {
        if (train == null || train.navigation == null) {
            return StopTargetDistance.unavailable(UnavailableReason.NAVIGATION_UNAVAILABLE);
        }

        Navigation navigation = train.navigation;
        if (navigation.destination != null) {
            if (!Double.isFinite(navigation.distanceToDestination)) {
                return StopTargetDistance.unavailable(UnavailableReason.NAVIGATION_DISTANCE_NOT_FINITE);
            }
            return StopTargetDistance.available(navigation.distanceToDestination, Source.NAVIGATION);
        }

        if (train.graph == null) {
            return StopTargetDistance.unavailable(UnavailableReason.GRAPH_UNAVAILABLE);
        }
        if (train.carriages == null || train.carriages.isEmpty()) {
            return StopTargetDistance.unavailable(UnavailableReason.CARRIAGES_UNAVAILABLE);
        }

        Boolean forward = resolveManualDirection(train);
        if (forward == null) {
            return StopTargetDistance.unavailable(UnavailableReason.MANUAL_DIRECTION_UNAVAILABLE);
        }

        GlobalStation station = navigation.findNearestApproachable(forward);
        if (station == null) {
            return StopTargetDistance.unavailable(UnavailableReason.NO_APPROACHABLE_STATION);
        }

        ArrayList<GlobalStation> destinations = new ArrayList<>(1);
        destinations.add(station);
        AtomicReference<Double> distance = new AtomicReference<>();
        navigation.search(Double.MAX_VALUE, forward, destinations,
                (distanceToEdgeEnd, cost, reachedVia, currentEntry, reachedStation) -> {
                    if (reachedStation != station) {
                        return false;
                    }

                    TrackEdge edge = currentEntry.getSecond();
                    double stationPositionFromEdgeEnd = edge.getLength() - station.getLocationOn(edge);
                    double unsignedDistance = distanceToEdgeEnd - stationPositionFromEdgeEnd;
                    distance.set(forward ? unsignedDistance : -unsignedDistance);
                    return true;
                });

        Double resolvedDistance = distance.get();
        if (resolvedDistance == null || !Double.isFinite(resolvedDistance)) {
            return StopTargetDistance.unavailable(UnavailableReason.STATION_DISTANCE_NOT_FOUND);
        }
        return StopTargetDistance.available(resolvedDistance, Source.MANUAL_CREATE_EQUIVALENT);
    }

    /**
     * 仕様書に独立した関数契約がないため、入力を評価し、{@code resolveManualDirection}が示す解決結果を返します。
     * @param train 対象となるCreate列車。
     * @return 条件を満たす場合はtrue、それ以外はfalse。
     */
    private static Boolean resolveManualDirection(Train train) {
        if (!train.doubleEnded) {
            return true;
        }

        double directedSpeed = train.targetSpeed != 0 ? train.targetSpeed : train.speed;
        if (!Double.isFinite(directedSpeed) || directedSpeed == 0) {
            return null;
        }
        return directedSpeed > 0;
    }
}
