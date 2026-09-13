package dev.edudio.createadvancedtrains.train.query;

/** Navigation停止対象を正規化できなかった理由を分類します。 */
public enum NavigationStopInvalidReason {
    NONE,
    NAVIGATION_UNAVAILABLE,
    DISTANCE_NOT_FINITE,
    GRAPH_UNAVAILABLE,
    CARRIAGES_UNAVAILABLE,
    LEADING_TRAVELLING_POINT_INVALID,
    DESTINATION_EDGE_POINT_INVALID
}
