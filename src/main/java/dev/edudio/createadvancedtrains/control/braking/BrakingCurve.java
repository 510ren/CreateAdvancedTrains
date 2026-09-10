package dev.edudio.createadvancedtrains.control.braking;

import static dev.edudio.createadvancedtrains.constants.UnitConstants.TICKS_PER_SECOND;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.OptionalDouble;

import dev.edudio.createadvancedtrains.control.notch.Notch;
import dev.edudio.createadvancedtrains.control.notch.NotchProfile;
import dev.edudio.createadvancedtrains.control.notch.NotchResponseModel;

/**
 * Pure Phase 5B B7 stopping-distance and reverse-speed calculation.
 */
public final class BrakingCurve {

    public static final double PHASE_5B_SAFETY_MARGIN_BLOCKS = 10.0;
    public static final double STOP_SPEED_THRESHOLD_BLOCKS_PER_SECOND = 0.001;
    public static final int INTERNAL_SCALE = 4;

    private static final double TIME_STEP_SECONDS = 1.0 / TICKS_PER_SECOND;
    private static final long SPEED_SCALE = 10_000L;
    private static final Notch[] SERVICE_BRAKE_NOTCHES = {
            Notch.B1, Notch.B2, Notch.B3, Notch.B4,
            Notch.B5, Notch.B6, Notch.B7
    };

    private final NotchProfile profile;
    private final SpeedDependentFactor performanceCorrection;
    private final SpeedDependentFactor safetyMargin;

    public BrakingCurve(
            NotchProfile profile,
            SpeedDependentFactor performanceCorrection,
            SpeedDependentFactor safetyMargin) {
        this.profile = Objects.requireNonNull(profile, "profile");
        this.performanceCorrection = Objects.requireNonNull(performanceCorrection, "performanceCorrection");
        this.safetyMargin = Objects.requireNonNull(safetyMargin, "safetyMargin");
    }

    public static BrakingCurve phase5B() {
        return new BrakingCurve(
                NotchProfile.phase5BProductionProfile(),
                speedBlocksPerSecond -> 1.0,
                speedBlocksPerSecond -> PHASE_5B_SAFETY_MARGIN_BLOCKS);
    }

    public double kappa(double speedBlocksPerSecond) {
        requireFinite(speedBlocksPerSecond, "speedBlocksPerSecond");
        return performanceCorrection.valueAt(Math.abs(speedBlocksPerSecond));
    }

    public double safetyMarginBlocks(double speedBlocksPerSecond) {
        requireFinite(speedBlocksPerSecond, "speedBlocksPerSecond");
        return safetyMargin.valueAt(Math.abs(speedBlocksPerSecond));
    }

    public StoppingDistancePrediction predictStoppingDistance(
            double initialSpeedBlocksPerSecond,
            double transitionStartAccelerationBlocksPerSecondSquared,
            double baseAccelerationBlocksPerSecondSquared) {
        BrakingCurveFailure validationFailure = validatePredictionInputs(
                initialSpeedBlocksPerSecond,
                transitionStartAccelerationBlocksPerSecondSquared,
                baseAccelerationBlocksPerSecondSquared);
        if (validationFailure != BrakingCurveFailure.NONE) {
            return StoppingDistancePrediction.failure(validationFailure, 0);
        }

        double speed = roundInternal(initialSpeedBlocksPerSecond);
        if (speed < STOP_SPEED_THRESHOLD_BLOCKS_PER_SECOND) {
            return StoppingDistancePrediction.success(0.0, 0);
        }

        ProfileSample initialProfile = sampleProfile(speed, baseAccelerationBlocksPerSecondSquared);
        if (!initialProfile.valid()) {
            return StoppingDistancePrediction.failure(BrakingCurveFailure.INVALID_PROFILE, 0);
        }

        double safeB7Magnitude = roundInternal(initialProfile.b7Magnitude() * initialProfile.kappa());
        if (!Double.isFinite(safeB7Magnitude) || safeB7Magnitude <= 0.0) {
            return StoppingDistancePrediction.failure(BrakingCurveFailure.INVALID_PROFILE, 0);
        }

        double baselineTicks = Math.ceil(
                initialSpeedBlocksPerSecond / safeB7Magnitude / TIME_STEP_SECONDS);
        double maximumTicksValue = baselineTicks
                + NotchResponseModel.TRANSITION_TICKS
                + 10.0;
        if (!Double.isFinite(maximumTicksValue) || maximumTicksValue > Integer.MAX_VALUE) {
            return StoppingDistancePrediction.failure(BrakingCurveFailure.ITERATION_LIMIT_REACHED, 0);
        }
        int maximumTicks = (int) maximumTicksValue;

        double distance = 0.0;
        double startAcceleration = roundInternal(transitionStartAccelerationBlocksPerSecondSquared);
        for (int tick = 0; tick < maximumTicks; tick++) {
            ProfileSample sample = sampleProfile(speed, baseAccelerationBlocksPerSecondSquared);
            if (!sample.valid()) {
                return StoppingDistancePrediction.failure(BrakingCurveFailure.INVALID_PROFILE, tick);
            }

            double targetAcceleration = roundInternal(-sample.kappa() * sample.b7Magnitude());
            double effectiveAcceleration = roundInternal(
                    NotchResponseModel.effectiveAccelerationAt(
                            startAcceleration,
                            targetAcceleration,
                            tick));
            double nextSpeed = roundInternal(Math.max(
                    0.0,
                    speed + effectiveAcceleration * TIME_STEP_SECONDS));
            double distanceStep = roundInternal(
                    (speed + nextSpeed) * 0.5 * TIME_STEP_SECONDS);
            distance = roundInternal(distance + distanceStep);
            speed = nextSpeed;

            if (speed < STOP_SPEED_THRESHOLD_BLOCKS_PER_SECOND) {
                return StoppingDistancePrediction.success(distance, tick + 1);
            }
        }

        return StoppingDistancePrediction.failure(
                BrakingCurveFailure.ITERATION_LIMIT_REACHED,
                maximumTicks);
    }

    public BrakingCurveResult calculate(BrakingCurveInput input) {
        Objects.requireNonNull(input, "input");

        BrakingCurveFailure inputFailure = validateCurveInput(input);
        if (inputFailure != BrakingCurveFailure.NONE) {
            return BrakingCurveResult.invalid(inputFailure);
        }

        double margin = safetyMarginBlocks(input.currentSpeedBlocksPerSecond());
        if (!Double.isFinite(margin) || margin < 0.0) {
            return BrakingCurveResult.invalid(BrakingCurveFailure.INVALID_PROFILE);
        }
        double usableDistance = roundInternal(
                input.forwardRemainingDistanceBlocks() - margin);

        StoppingDistancePrediction currentPrediction = predictStoppingDistance(
                input.currentSpeedBlocksPerSecond(),
                input.transitionStartAccelerationBlocksPerSecondSquared(),
                input.baseAccelerationBlocksPerSecondSquared());
        if (!currentPrediction.isSuccessful()) {
            return BrakingCurveResult.invalid(currentPrediction.failure());
        }

        double predictedDistance = currentPrediction.stoppingDistanceBlocks().getAsDouble();
        double predictedOvershoot = roundInternal(Math.max(
                0.0,
                predictedDistance - usableDistance));

        if (usableDistance < 0.0) {
            return successfulResult(
                    BrakingCurveStatus.INSUFFICIENT_DISTANCE,
                    0.0,
                    predictedDistance,
                    usableDistance,
                    predictedOvershoot);
        }

        long ceilingUnits = floorPositiveToFourDecimals(input.speedCeilingBlocksPerSecond());
        long lowestCandidateUnits = 0L;
        long highestCandidateUnits = ceilingUnits;
        long bestCandidateUnits = 0L;

        while (lowestCandidateUnits <= highestCandidateUnits) {
            long candidateUnits = lowestCandidateUnits
                    + (highestCandidateUnits - lowestCandidateUnits) / 2L;
            double candidateSpeed = candidateUnits / (double) SPEED_SCALE;
            StoppingDistancePrediction candidatePrediction = predictStoppingDistance(
                    candidateSpeed,
                    input.transitionStartAccelerationBlocksPerSecondSquared(),
                    input.baseAccelerationBlocksPerSecondSquared());
            if (!candidatePrediction.isSuccessful()) {
                return BrakingCurveResult.invalid(candidatePrediction.failure());
            }

            if (candidatePrediction.stoppingDistanceBlocks().getAsDouble() <= usableDistance) {
                bestCandidateUnits = candidateUnits;
                lowestCandidateUnits = candidateUnits + 1L;
            } else {
                highestCandidateUnits = candidateUnits - 1L;
            }
        }

        double maximumPermittedSpeed = bestCandidateUnits / (double) SPEED_SCALE;
        BrakingCurveStatus status = predictedOvershoot > 0.0
                ? BrakingCurveStatus.INSUFFICIENT_DISTANCE
                : BrakingCurveStatus.AVAILABLE;
        return successfulResult(
                status,
                maximumPermittedSpeed,
                predictedDistance,
                usableDistance,
                predictedOvershoot);
    }

    private BrakingCurveResult successfulResult(
            BrakingCurveStatus status,
            double maximumPermittedSpeed,
            double predictedDistance,
            double usableDistance,
            double predictedOvershoot) {
        return new BrakingCurveResult(
                status,
                OptionalDouble.of(maximumPermittedSpeed),
                OptionalDouble.of(predictedDistance),
                OptionalDouble.of(usableDistance),
                OptionalDouble.of(predictedOvershoot),
                BrakingCurveFailure.NONE);
    }

    private BrakingCurveFailure validateCurveInput(BrakingCurveInput input) {
        if (!Double.isFinite(input.forwardRemainingDistanceBlocks())
                || !Double.isFinite(input.currentSpeedBlocksPerSecond())
                || !Double.isFinite(input.transitionStartAccelerationBlocksPerSecondSquared())
                || !Double.isFinite(input.baseAccelerationBlocksPerSecondSquared())
                || !Double.isFinite(input.speedCeilingBlocksPerSecond())) {
            return BrakingCurveFailure.NON_FINITE_INPUT;
        }
        if (input.forwardRemainingDistanceBlocks() <= 0.0) {
            return BrakingCurveFailure.NON_POSITIVE_FORWARD_DISTANCE;
        }
        if (input.currentSpeedBlocksPerSecond() < 0.0) {
            return BrakingCurveFailure.NEGATIVE_SPEED;
        }
        if (input.speedCeilingBlocksPerSecond() < 0.0
                || input.speedCeilingBlocksPerSecond() > Long.MAX_VALUE / (double) SPEED_SCALE) {
            return BrakingCurveFailure.INVALID_SPEED_CEILING;
        }
        if (input.baseAccelerationBlocksPerSecondSquared() <= 0.0) {
            return BrakingCurveFailure.INVALID_BASE_ACCELERATION;
        }
        return BrakingCurveFailure.NONE;
    }

    private BrakingCurveFailure validatePredictionInputs(
            double speed,
            double startAcceleration,
            double baseAcceleration) {
        if (!Double.isFinite(speed)
                || !Double.isFinite(startAcceleration)
                || !Double.isFinite(baseAcceleration)) {
            return BrakingCurveFailure.NON_FINITE_INPUT;
        }
        if (speed < 0.0) {
            return BrakingCurveFailure.NEGATIVE_SPEED;
        }
        if (baseAcceleration <= 0.0) {
            return BrakingCurveFailure.INVALID_BASE_ACCELERATION;
        }
        return BrakingCurveFailure.NONE;
    }

    private ProfileSample sampleProfile(double speed, double baseAcceleration) {
        try {
            OptionalDouble configuredDelta = profile.configuredDelta(speed);
            if (configuredDelta.isEmpty()
                    || configuredDelta.getAsDouble() <= 0.0
                    || configuredDelta.getAsDouble() >= 1.0 / 3.0) {
                return ProfileSample.invalid();
            }

            double previousMagnitude = 0.0;
            double b7Magnitude = 0.0;
            for (Notch notch : SERVICE_BRAKE_NOTCHES) {
                double magnitude = profile.brakingMagnitude(notch, speed, baseAcceleration);
                if (!Double.isFinite(magnitude) || magnitude <= previousMagnitude) {
                    return ProfileSample.invalid();
                }
                previousMagnitude = magnitude;
                if (notch == Notch.B7) {
                    b7Magnitude = magnitude;
                }
            }

            double correction = kappa(speed);
            if (!Double.isFinite(correction) || correction <= 0.0 || correction > 1.0) {
                return ProfileSample.invalid();
            }
            return new ProfileSample(true, b7Magnitude, correction);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ProfileSample.invalid();
        }
    }

    private static long floorPositiveToFourDecimals(double value) {
        return BigDecimal.valueOf(value)
                .movePointRight(INTERNAL_SCALE)
                .setScale(0, RoundingMode.FLOOR)
                .longValueExact();
    }

    private static double roundInternal(double value) {
        if (!Double.isFinite(value)) {
            return value;
        }
        double scaled = value * SPEED_SCALE;
        if (!Double.isFinite(scaled)) {
            return scaled;
        }
        double roundedMagnitude = Math.floor(Math.abs(scaled) + 0.5);
        return Math.copySign(roundedMagnitude / SPEED_SCALE, value);
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private record ProfileSample(boolean valid, double b7Magnitude, double kappa) {

        private static ProfileSample invalid() {
            return new ProfileSample(false, 0.0, 0.0);
        }
    }
}
