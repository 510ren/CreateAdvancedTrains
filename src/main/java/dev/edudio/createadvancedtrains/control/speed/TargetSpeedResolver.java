package dev.edudio.createadvancedtrains.control.speed;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

import dev.edudio.createadvancedtrains.train.query.NavigationStopState;

/** Pure Phase 6 integration of independent speed ceilings in CAT units. */
public final class TargetSpeedResolver {

    public TargetSpeedResolution resolve(Input input) {
        validate(input);

        NativeTargetZeroClassification zeroClassification = classifyNativeZero(
                input.nativeTargetSpeedBlocksPerSecond(),
                input.navigationStopState(),
                input.hasGlobalStationDestination(),
                input.waitingForSignal(),
                input.manualTick());
        List<TargetSpeedCandidate> candidates = new ArrayList<>();
        candidates.add(new TargetSpeedCandidate(
                TargetSpeedCandidateSource.CREATE_NATIVE,
                OptionalDouble.of(Math.abs(input.nativeTargetSpeedBlocksPerSecond())),
                true,
                nativeDiagnostic(zeroClassification)));
        candidates.add(new TargetSpeedCandidate(
                TargetSpeedCandidateSource.CREATE_MAX_SPEED,
                OptionalDouble.of(input.createSpeedCeilingBlocksPerSecond()),
                true,
                "Create maxSpeed ceiling"));
        candidates.add(optionalCandidate(
                TargetSpeedCandidateSource.CAT_SPEED_LIMIT,
                input.catSpeedLimitBlocksPerSecond(),
                "No enabled CAT speed-limit candidate"));
        candidates.add(brakingCurveCandidate(
                input.brakingCurveLimitBlocksPerSecond(),
                input.hasGlobalStationDestination()));

        double minimum = Double.POSITIVE_INFINITY;
        for (TargetSpeedCandidate candidate : candidates) {
            if (candidate.included() && candidate.magnitudeBlocksPerSecond().isPresent()) {
                minimum = Math.min(minimum, candidate.magnitudeBlocksPerSecond().getAsDouble());
            }
        }
        if (!Double.isFinite(minimum)) {
            return new TargetSpeedResolution(
                    OptionalDouble.empty(), OptionalDouble.empty(), EnumSet.noneOf(TargetSpeedCandidateSource.class),
                    candidates, zeroClassification, "No valid target-speed candidate");
        }

        EnumSet<TargetSpeedCandidateSource> adopted = EnumSet.noneOf(TargetSpeedCandidateSource.class);
        for (TargetSpeedCandidate candidate : candidates) {
            if (candidate.included() && candidate.magnitudeBlocksPerSecond().isPresent()
                    && Double.compare(candidate.magnitudeBlocksPerSecond().getAsDouble(), minimum) == 0) {
                adopted.add(candidate.source());
            }
        }
        double signed = minimum == 0.0 ? 0.0 : Math.copySign(minimum, input.directionSign());
        String diagnostic;
        if (input.hasGlobalStationDestination()) {
            diagnostic = "GlobalStation destination: BrakingCurve retained for diagnostics and excluded from resolution";
        } else if (zeroClassification == NativeTargetZeroClassification.UNCLASSIFIED) {
            diagnostic = "Native zero had no recognized Create source and was retained as a safe candidate";
        } else {
            diagnostic = "Resolved minimum valid speed magnitude";
        }
        return new TargetSpeedResolution(
                OptionalDouble.of(minimum), OptionalDouble.of(signed), adopted, candidates,
                zeroClassification,
                diagnostic);
    }

    private static TargetSpeedCandidate brakingCurveCandidate(
            OptionalDouble value,
            boolean hasGlobalStationDestination) {
        if (value.isEmpty()) {
            return new TargetSpeedCandidate(
                    TargetSpeedCandidateSource.BRAKING_CURVE,
                    OptionalDouble.empty(),
                    false,
                    "No usable BrakingCurve candidate");
        }
        if (hasGlobalStationDestination) {
            return new TargetSpeedCandidate(
                    TargetSpeedCandidateSource.BRAKING_CURVE,
                    value,
                    false,
                    "Diagnostic only for GlobalStation destination");
        }
        return new TargetSpeedCandidate(
                TargetSpeedCandidateSource.BRAKING_CURVE,
                value,
                true,
                "Valid candidate");
    }

    private static TargetSpeedCandidate optionalCandidate(
            TargetSpeedCandidateSource source,
            OptionalDouble value,
            String absentDiagnostic) {
        return value.isPresent()
                ? new TargetSpeedCandidate(source, value, true, "Valid candidate")
                : new TargetSpeedCandidate(source, OptionalDouble.empty(), false, absentDiagnostic);
    }

    public static NativeTargetZeroClassification classifyNativeZero(
            double nativeTargetSpeedBlocksPerSecond,
            NavigationStopState navigationStopState,
            boolean hasNavigationDestination,
            boolean waitingForSignal,
            boolean manualTick) {
        if (nativeTargetSpeedBlocksPerSecond != 0.0) {
            return NativeTargetZeroClassification.NOT_ZERO;
        }
        if (waitingForSignal) {
            return NativeTargetZeroClassification.SIGNAL_STOP;
        }
        if (hasNavigationDestination
                && (navigationStopState == NavigationStopState.AHEAD
                        || navigationStopState == NavigationStopState.AT_DESTINATION_OR_ARRIVAL_PENDING)) {
            return NativeTargetZeroClassification.DESTINATION_STOP;
        }
        if (!hasNavigationDestination && manualTick) {
            return NativeTargetZeroClassification.CREATE_MANUAL_INPUT;
        }
        return NativeTargetZeroClassification.UNCLASSIFIED;
    }

    private static String nativeDiagnostic(NativeTargetZeroClassification classification) {
        return switch (classification) {
            case NOT_ZERO -> "Non-zero Create native target";
            case SIGNAL_STOP -> "Create signal stop zero retained";
            case DESTINATION_STOP -> "GlobalStation destination stop zero retained";
            case CREATE_MANUAL_INPUT -> "Create manual-input zero retained";
            case UNCLASSIFIED -> "Unclassified Create zero retained conservatively";
        };
    }

    private static void validate(Input input) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        requireFinite(input.nativeTargetSpeedBlocksPerSecond(), "nativeTargetSpeedBlocksPerSecond");
        requireFinite(input.createSpeedCeilingBlocksPerSecond(), "createSpeedCeilingBlocksPerSecond");
        requireFinite(input.directionSign(), "directionSign");
        if (input.createSpeedCeilingBlocksPerSecond() < 0.0) {
            throw new IllegalArgumentException("createSpeedCeilingBlocksPerSecond must be non-negative");
        }
        if (input.directionSign() != -1.0 && input.directionSign() != 1.0) {
            throw new IllegalArgumentException("directionSign must be -1 or 1");
        }
        validateOptional(input.catSpeedLimitBlocksPerSecond(), "catSpeedLimitBlocksPerSecond");
        validateOptional(input.brakingCurveLimitBlocksPerSecond(), "brakingCurveLimitBlocksPerSecond");
        Objects.requireNonNull(input.navigationStopState(), "navigationStopState");
    }

    private static void validateOptional(OptionalDouble value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        if (value.isPresent() && (!Double.isFinite(value.getAsDouble()) || value.getAsDouble() < 0.0)) {
            throw new IllegalArgumentException(name + " must contain a finite non-negative value");
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    public record Input(
            double nativeTargetSpeedBlocksPerSecond,
            double createSpeedCeilingBlocksPerSecond,
            OptionalDouble catSpeedLimitBlocksPerSecond,
            OptionalDouble brakingCurveLimitBlocksPerSecond,
            double directionSign,
            NavigationStopState navigationStopState,
            boolean hasGlobalStationDestination,
            boolean waitingForSignal,
            boolean manualTick) {
    }
}
