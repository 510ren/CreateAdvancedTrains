package dev.edudio.createadvancedtrains.control.notch;

import java.util.EnumMap;
import java.util.Map;

/**
 * Speed-dependent service-brake profile expressed in CAT units.
 */
public final class NotchProfile {

    private static final double MIN_PROFILE_SPEED_BLOCKS_PER_SECOND = 0.0;
    private static final double MAX_PROFILE_SPEED_BLOCKS_PER_SECOND = 160.0;

    private final Map<Notch, LinearTable> multiplierTables;

    private NotchProfile(Map<Notch, LinearTable> multiplierTables) {
        this.multiplierTables = new EnumMap<>(multiplierTables);
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
        return new NotchProfile(tables);
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

        if (notch == Notch.COAST) {
            return 0.0;
        }

        LinearTable table = multiplierTables.get(notch);
        if (table == null) {
            throw new IllegalArgumentException("No profile table for notch " + notch);
        }

        double speed = Math.abs(currentSpeedBlocksPerSecond);
        return -baseAccelerationBlocksPerSecondSquared * table.interpolate(speed);
    }

    public double multiplier(Notch notch, double currentSpeedBlocksPerSecond) {
        requireFinite(currentSpeedBlocksPerSecond, "currentSpeedBlocksPerSecond");

        if (notch == Notch.COAST) {
            return 0.0;
        }

        LinearTable table = multiplierTables.get(notch);
        if (table == null) {
            throw new IllegalArgumentException("No profile table for notch " + notch);
        }
        return table.interpolate(Math.abs(currentSpeedBlocksPerSecond));
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
