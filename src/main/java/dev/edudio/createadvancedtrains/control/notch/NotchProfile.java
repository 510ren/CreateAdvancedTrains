package dev.edudio.createadvancedtrains.control.notch;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Product P/N/B profile expressed in CAT units. The Phase 5A factory remains
 * an isolated service-brake-only measurement profile.
 */
public final class NotchProfile {

    private static final double MIN_PROFILE_SPEED_BLOCKS_PER_SECOND = 0.0;
    private static final double MAX_PROFILE_SPEED_BLOCKS_PER_SECOND = 160.0;
    private static final double PHASE_5B_DELTA = 1.0 / 5.0;
    private static final double PHASE_6_POWER_DELTA = 1.0 / 5.0;
    private static final double PHASE_6_NEUTRAL_MULTIPLIER = 1.0 / 6.0;

    private final Map<Notch, LinearTable> multiplierTables;
    private final DeltaFunction deltaFunction;

    private NotchProfile(
            Map<Notch, LinearTable> multiplierTables,
            DeltaFunction deltaFunction) {
        this.multiplierTables = new EnumMap<>(multiplierTables);
        this.deltaFunction = deltaFunction;
    }

    public static NotchProfile phase5ATemporaryProfile() {
        EnumMap<Notch, LinearTable> tables = new EnumMap<>(Notch.class);
        tables.put(Notch.B1, constantMultiplier(1.0));
        tables.put(Notch.B2, constantMultiplier(1.5));
        tables.put(Notch.B3, constantMultiplier(2.0));
        tables.put(Notch.B4, constantMultiplier(2.5));
        tables.put(Notch.B5, constantMultiplier(3.0));
        tables.put(Notch.B6, constantMultiplier(3.5));
        tables.put(Notch.B7, constantMultiplier(4.0));
        return new NotchProfile(tables, null);
    }

    /**
     * Returns the approved Phase 5B product profile. The Phase 5A measurement
     * profile deliberately remains a separate factory.
     */
    public static NotchProfile phase5BProductionProfile() {
        return b4CenteredProfile(speedBlocksPerSecond -> PHASE_5B_DELTA);
    }

    /**
     * Creates the B4-centred product profile using a speed-dependent delta
     * boundary. Phase 5B supplies a constant function; Phase 10 may replace the
     * function without changing callers or the B4 invariant.
     */
    public static NotchProfile b4CenteredProfile(DeltaFunction deltaFunction) {
        return new NotchProfile(
                new EnumMap<>(Notch.class),
                Objects.requireNonNull(deltaFunction, "deltaFunction"));
    }

    /**
     * Returns the target acceleration in blocks/s^2. Service braking is negative.
     */
    public double targetAcceleration(
            Notch notch,
            double currentSpeedBlocksPerSecond,
            double baseAccelerationBlocksPerSecondSquared) {
        requireFinite(currentSpeedBlocksPerSecond, "currentSpeedBlocksPerSecond");
        requireFinite(baseAccelerationBlocksPerSecondSquared, "baseAccelerationBlocksPerSecondSquared");

        if (baseAccelerationBlocksPerSecondSquared <= 0.0) {
            throw new IllegalArgumentException("baseAccelerationBlocksPerSecondSquared must be positive");
        }

        Objects.requireNonNull(notch, "notch");
        if (notch.isPower()) {
            return baseAccelerationBlocksPerSecondSquared
                    * (1.0 + (powerLevel(notch) - 3) * PHASE_6_POWER_DELTA);
        }
        if (notch.isNeutral()) {
            return -baseAccelerationBlocksPerSecondSquared * PHASE_6_NEUTRAL_MULTIPLIER;
        }
        return -brakingMagnitude(notch, currentSpeedBlocksPerSecond,
                baseAccelerationBlocksPerSecondSquared);
    }

    /**
     * Returns the positive service-brake magnitude in blocks/s^2.
     */
    public double brakingMagnitude(
            Notch notch,
            double currentSpeedBlocksPerSecond,
            double baseAccelerationBlocksPerSecondSquared) {
        Objects.requireNonNull(notch, "notch");
        requireFinite(currentSpeedBlocksPerSecond, "currentSpeedBlocksPerSecond");
        requireFinite(baseAccelerationBlocksPerSecondSquared, "baseAccelerationBlocksPerSecondSquared");

        if (baseAccelerationBlocksPerSecondSquared <= 0.0) {
            throw new IllegalArgumentException("baseAccelerationBlocksPerSecondSquared must be positive");
        }

        if (!notch.isServiceBrake()) {
            throw new IllegalArgumentException("Not a service-brake notch: " + notch);
        }

        return baseAccelerationBlocksPerSecondSquared
                * multiplier(notch, currentSpeedBlocksPerSecond);
    }

    public double multiplier(Notch notch, double currentSpeedBlocksPerSecond) {
        requireFinite(currentSpeedBlocksPerSecond, "currentSpeedBlocksPerSecond");

        Objects.requireNonNull(notch, "notch");
        if (!notch.isServiceBrake()) {
            throw new IllegalArgumentException("Not a service-brake notch: " + notch);
        }

        double speed = Math.abs(currentSpeedBlocksPerSecond);
        if (deltaFunction != null) {
            double delta = delta(speed);
            return 1.0 + (serviceBrakeLevel(notch) - 4) * delta;
        }

        LinearTable table = multiplierTables.get(notch);
        if (table == null) {
            throw new IllegalArgumentException("No profile table for notch " + notch);
        }
        return table.interpolate(speed);
    }

    /**
     * Returns delta(u) for a B4-centred product profile.
     */
    public double delta(double currentSpeedBlocksPerSecond) {
        requireFinite(currentSpeedBlocksPerSecond, "currentSpeedBlocksPerSecond");
        if (deltaFunction == null) {
            throw new IllegalStateException("This profile does not use a B4-centred delta function");
        }

        double delta = deltaFunction.valueAt(Math.abs(currentSpeedBlocksPerSecond));
        requireFinite(delta, "delta");
        return delta;
    }

    public OptionalDouble configuredDelta(double currentSpeedBlocksPerSecond) {
        if (deltaFunction == null) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(delta(currentSpeedBlocksPerSecond));
    }

    public boolean isB4Centered() {
        return deltaFunction != null;
    }

    private static LinearTable constantMultiplier(double multiplier) {
        return new LinearTable(
                new double[] {
                        MIN_PROFILE_SPEED_BLOCKS_PER_SECOND,
                        MAX_PROFILE_SPEED_BLOCKS_PER_SECOND
                },
                new double[] { multiplier, multiplier });
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static int serviceBrakeLevel(Notch notch) {
        return switch (notch) {
            case B1 -> 1;
            case B2 -> 2;
            case B3 -> 3;
            case B4 -> 4;
            case B5 -> 5;
            case B6 -> 6;
            case B7 -> 7;
            case P1, P2, P3, P4, P5, N ->
                throw new IllegalArgumentException(notch + " has no service-brake level");
        };
    }

    private static int powerLevel(Notch notch) {
        return switch (notch) {
            case P1 -> 1;
            case P2 -> 2;
            case P3 -> 3;
            case P4 -> 4;
            case P5 -> 5;
            default -> throw new IllegalArgumentException(notch + " has no power level");
        };
    }

    @FunctionalInterface
    public interface DeltaFunction {
        double valueAt(double speedBlocksPerSecond);
    }

    /**
     * Immutable, clamped, piecewise-linear lookup table.
     */
    private static final class LinearTable {

        private final double[] speeds;
        private final double[] values;

        private LinearTable(double[] speeds, double[] values) {
            if (speeds.length != values.length || speeds.length < 2) {
                throw new IllegalArgumentException("A profile table needs at least two matching points");
            }

            this.speeds = speeds.clone();
            this.values = values.clone();

            for (int index = 0; index < this.speeds.length; index++) {
                requireFinite(this.speeds[index], "speed point");
                requireFinite(this.values[index], "profile value");
                if (index > 0 && this.speeds[index] <= this.speeds[index - 1]) {
                    throw new IllegalArgumentException("Profile speed points must be strictly increasing");
                }
            }
        }

        private double interpolate(double speed) {
            if (speed <= speeds[0]) {
                return values[0];
            }

            int lastIndex = speeds.length - 1;
            if (speed >= speeds[lastIndex]) {
                return values[lastIndex];
            }

            for (int upperIndex = 1; upperIndex < speeds.length; upperIndex++) {
                if (speed > speeds[upperIndex]) {
                    continue;
                }

                int lowerIndex = upperIndex - 1;
                double interval = speeds[upperIndex] - speeds[lowerIndex];
                double fraction = (speed - speeds[lowerIndex]) / interval;
                return values[lowerIndex]
                        + (values[upperIndex] - values[lowerIndex]) * fraction;
            }

            return values[lastIndex];
        }
    }
}
