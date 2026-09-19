package dev.edudio.createadvancedtrains.speed;

import static dev.edudio.createadvancedtrains.constants.UnitConstants.TICKS_PER_SECOND;

import java.util.EnumMap;

/**
 * Manages speed restrictions applied to a train.
 *
 * <p>
 * All public speed values are expressed in blocks per second
 * (blocks/s).
 * </p>
 *
 * <p>
 * Create internally uses blocks per tick for Train.speed and
 * Train.targetSpeed. Conversion is performed only at the boundary
 * between Create: Advanced Trains and Create.
 * </p>
 */
public class SpeedLimitController {

    private final EnumMap<SpeedLimitSource, Double> limits = new EnumMap<>(SpeedLimitSource.class);

    /**
     * Sets a speed limit for the specified source.
     *
     * @param source     the source of the speed restriction
     * @param speedLimit speed limit in blocks/s
     */
    public void setLimit(
            SpeedLimitSource source,
            double speedLimit) {
        if (source == null) {
            throw new IllegalArgumentException(
                    "Speed limit source must not be null");
        }

        if (!Double.isFinite(speedLimit) || speedLimit < 0.0) {
            throw new IllegalArgumentException(
                    "Speed limit must be finite and >= 0");
        }

        limits.put(source, speedLimit);
    }

    /**
     * Removes the speed limit associated with the specified source.
     *
     * @param source the source to remove
     */
    public void removeLimit(SpeedLimitSource source) {
        limits.remove(source);
    }

    /**
     * Returns whether a speed limit is registered for the source.
     *
     * @param source the source to check
     * @return true if a limit is registered
     */
    public boolean hasLimit(SpeedLimitSource source) {
        return limits.containsKey(source);
    }

    /**
     * Returns the speed limit registered for the source.
     *
     * <p>
     * If no limit is registered, positive infinity is returned.
     * </p>
     *
     * @param source the source to query
     * @return speed limit in blocks/s, or positive infinity
     */
    public double getLimit(SpeedLimitSource source) {
        return limits.getOrDefault(
                source,
                Double.POSITIVE_INFINITY);
    }

    /**
     * Returns the effective speed limit.
     *
     * <p>
     * The effective limit is the lowest registered speed limit.
     * </p>
     *
     * @return effective speed limit in blocks/s
     */
    public double getEffectiveLimit() {
        double effectiveLimit = Double.POSITIVE_INFINITY;

        for (double limit : limits.values()) {
            effectiveLimit = Math.min(
                    effectiveLimit,
                    limit);
        }

        return effectiveLimit;
    }

    /**
     * Applies the effective speed limit to a Create target speed.
     *
     * <p>
     * The input and return value use Create's internal
     * blocks/tick unit. The registered speed limits themselves
     * use blocks/s.
     * </p>
     *
     * @param createTargetSpeed target speed in blocks/tick
     * @return limited target speed in blocks/tick
     */
    public double apply(double createTargetSpeed) {
        double effectiveLimitBlocksPerSecond = getEffectiveLimit();

        double createTargetSpeedBlocksPerSecond = createTargetSpeed * TICKS_PER_SECOND;

        double limitedSpeedBlocksPerSecond = Math.min(
                Math.abs(createTargetSpeedBlocksPerSecond),
                effectiveLimitBlocksPerSecond);

        double limitedSpeedBlocksPerTick = limitedSpeedBlocksPerSecond / TICKS_PER_SECOND;

        return Math.copySign(
                limitedSpeedBlocksPerTick,
                createTargetSpeed);
    }

    /**
     * Removes all registered speed limits.
     */
    public void clear() {
        limits.clear();
    }
}
