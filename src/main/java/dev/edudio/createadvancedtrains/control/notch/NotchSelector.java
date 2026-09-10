package dev.edudio.createadvancedtrains.control.notch;

import static dev.edudio.createadvancedtrains.constants.UnitConstants.TICKS_PER_SECOND;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/** Pure response-aware Phase 6 P/N/B selector. */
public final class NotchSelector {

    public static final double SAFE_SPEED_FACTOR = 0.95;
    public static final double HYSTERESIS_BLOCKS_PER_SECOND = 0.5;
    public static final double NORMAL_BAND_OFFSET_BLOCKS_PER_SECOND = 1.0;
    public static final int PREDICTION_TICKS = 10;

    private static final Notch[] POWER_NOTCHES = {
            Notch.P1, Notch.P2, Notch.P3, Notch.P4, Notch.P5
    };

    private final NotchProfile profile;

    public NotchSelector(NotchProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    public NotchSelection select(Input input) {
        validate(input);

        double safeSpeed = input.resolvedSpeedLimitBlocksPerSecond() * SAFE_SPEED_FACTOR;
        double lower = safeSpeed - NORMAL_BAND_OFFSET_BLOCKS_PER_SECOND - HYSTERESIS_BLOCKS_PER_SECOND;
        double upper = safeSpeed + NORMAL_BAND_OFFSET_BLOCKS_PER_SECOND + HYSTERESIS_BLOCKS_PER_SECOND;
        double brakeLimit = input.brakingCurveLimitBlocksPerSecond().isPresent()
                ? Math.min(safeSpeed, input.brakingCurveLimitBlocksPerSecond().getAsDouble())
                : safeSpeed;
        boolean brakingDemand = input.currentSpeedBlocksPerSecond() > upper
                || input.brakingCurveLimitBlocksPerSecond().isPresent()
                        && input.currentSpeedBlocksPerSecond()
                                > input.brakingCurveLimitBlocksPerSecond().getAsDouble();

        Optional<Notch> current = input.currentRequestedNotch();
        if (current.isPresent() && current.get().isServiceBrake()) {
            return selectFromBrake(input, current.get(), brakeLimit);
        }
        if (brakingDemand) {
            double prediction = predict(input, Notch.B4);
            return new NotchSelection(Notch.B4, false, prediction, brakeLimit);
        }

        if (input.currentSpeedBlocksPerSecond() < lower) {
            if (current.isPresent() && current.get().isPower()) {
                Notch currentPower = current.get();
                double prediction = predict(input, currentPower);
                if (prediction < lower) {
                    Notch stronger = strongerPower(currentPower);
                    return selection(input, stronger, safeSpeed);
                }
                if (prediction > upper) {
                    Notch weaker = weakerPowerOrNeutral(currentPower);
                    return selection(input, weaker, safeSpeed);
                }
                return new NotchSelection(currentPower, false, prediction, safeSpeed);
            }
            Notch strongest = Notch.N;
            double strongestPrediction = predict(input, Notch.N);
            for (Notch candidate : POWER_NOTCHES) {
                double prediction = predict(input, candidate);
                if (prediction <= safeSpeed) {
                    strongest = candidate;
                    strongestPrediction = prediction;
                }
            }
            return new NotchSelection(strongest, false, strongestPrediction, safeSpeed);
        }

        if (current.isPresent() && current.get().isPower()) {
            double prediction = predict(input, current.get());
            if (prediction >= lower && prediction <= upper) {
                return new NotchSelection(current.get(), false, prediction, safeSpeed);
            }
            if (prediction < lower) {
                return selection(input, Notch.N, safeSpeed);
            }
            return selection(input, weakerPowerOrNeutral(current.get()), safeSpeed);
        }
        return selection(input, Notch.N, safeSpeed);
    }

    private NotchSelection selectFromBrake(Input input, Notch current, double brakeLimit) {
        double currentPrediction = predict(input, current);
        if (currentPrediction > brakeLimit + HYSTERESIS_BLOCKS_PER_SECOND) {
            if (current == Notch.B7) {
                return new NotchSelection(Notch.B7, true, currentPrediction, brakeLimit);
            }
            Notch stronger = strongerBrake(current);
            return selection(input, stronger, brakeLimit);
        }

        Notch weaker = weakerBrakeOrNeutral(current);
        double weakerPrediction = predict(input, weaker);
        if (weakerPrediction <= brakeLimit + HYSTERESIS_BLOCKS_PER_SECOND) {
            return new NotchSelection(weaker, false, weakerPrediction, brakeLimit);
        }
        return new NotchSelection(current, false, currentPrediction, brakeLimit);
    }

    private NotchSelection selection(Input input, Notch notch, double limit) {
        return new NotchSelection(notch, false, predict(input, notch), limit);
    }

    private double predict(Input input, Notch candidate) {
        double target = profile.targetAcceleration(
                candidate,
                input.currentSpeedBlocksPerSecond(),
                input.baseAccelerationBlocksPerSecondSquared());
        double speed = input.currentSpeedBlocksPerSecond();
        for (int tick = 0; tick < PREDICTION_TICKS; tick++) {
            double acceleration = NotchResponseModel.effectiveAccelerationAt(
                    input.currentEffectiveAccelerationBlocksPerSecondSquared(),
                    target,
                    tick);
            speed = Math.max(0.0, speed + acceleration / TICKS_PER_SECOND);
        }
        return speed;
    }

    private static Notch strongerPower(Notch notch) {
        return switch (notch) {
            case P1 -> Notch.P2;
            case P2 -> Notch.P3;
            case P3 -> Notch.P4;
            case P4, P5 -> Notch.P5;
            default -> throw new IllegalArgumentException("Not a power notch: " + notch);
        };
    }

    private static Notch weakerPowerOrNeutral(Notch notch) {
        return switch (notch) {
            case P5 -> Notch.P4;
            case P4 -> Notch.P3;
            case P3 -> Notch.P2;
            case P2 -> Notch.P1;
            case P1 -> Notch.N;
            default -> throw new IllegalArgumentException("Not a power notch: " + notch);
        };
    }

    private static Notch strongerBrake(Notch notch) {
        return switch (notch) {
            case B4 -> Notch.B5;
            case B5 -> Notch.B6;
            case B6, B7 -> Notch.B7;
            default -> throw new IllegalArgumentException("Phase 6 automatic brake must be B4 through B7");
        };
    }

    private static Notch weakerBrakeOrNeutral(Notch notch) {
        return switch (notch) {
            case B7 -> Notch.B6;
            case B6 -> Notch.B5;
            case B5 -> Notch.B4;
            case B4 -> Notch.N;
            default -> throw new IllegalArgumentException("Phase 6 automatic brake must be B4 through B7");
        };
    }

    private static void validate(Input input) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(input.currentRequestedNotch(), "currentRequestedNotch");
        Objects.requireNonNull(input.brakingCurveLimitBlocksPerSecond(), "brakingCurveLimitBlocksPerSecond");
        requireFiniteNonNegative(input.currentSpeedBlocksPerSecond(), "currentSpeedBlocksPerSecond");
        requireFiniteNonNegative(input.resolvedSpeedLimitBlocksPerSecond(), "resolvedSpeedLimitBlocksPerSecond");
        if (!Double.isFinite(input.currentEffectiveAccelerationBlocksPerSecondSquared())) {
            throw new IllegalArgumentException("currentEffectiveAccelerationBlocksPerSecondSquared must be finite");
        }
        if (!Double.isFinite(input.baseAccelerationBlocksPerSecondSquared())
                || input.baseAccelerationBlocksPerSecondSquared() <= 0.0) {
            throw new IllegalArgumentException("baseAccelerationBlocksPerSecondSquared must be finite and positive");
        }
        if (input.brakingCurveLimitBlocksPerSecond().isPresent()) {
            requireFiniteNonNegative(input.brakingCurveLimitBlocksPerSecond().getAsDouble(),
                    "brakingCurveLimitBlocksPerSecond");
        }
    }

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    public record Input(
            double currentSpeedBlocksPerSecond,
            double resolvedSpeedLimitBlocksPerSecond,
            OptionalDouble brakingCurveLimitBlocksPerSecond,
            Optional<Notch> currentRequestedNotch,
            double currentEffectiveAccelerationBlocksPerSecondSquared,
            double baseAccelerationBlocksPerSecondSquared) {
    }
}
