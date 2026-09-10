package dev.edudio.createadvancedtrains.control.notch;

import static dev.edudio.createadvancedtrains.constants.UnitConstants.TICKS_PER_SECOND;

import java.util.Objects;
import java.util.Optional;

/** Owns the response state and the minimal Create target/modifier boundary. */
public final class NotchController {

    private final NotchProfile profile;
    private final NotchResponseModel responseModel;
    private NotchResponseModel.Response lastResponse;

    public NotchController(NotchProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
        this.responseModel = new NotchResponseModel();
        this.lastResponse = NotchResponseModel.neutralResponse();
    }

    public NotchResponseModel.Response advance(
            Notch requestedNotch,
            double currentSpeedBlocksPerSecond,
            double baseAccelerationBlocksPerSecondSquared) {
        double targetAcceleration = profile.targetAcceleration(
                requestedNotch,
                currentSpeedBlocksPerSecond,
                baseAccelerationBlocksPerSecondSquared);
        lastResponse = responseModel.step(requestedNotch, targetAcceleration);
        return lastResponse;
    }

    public NotchControlResult applyBoundary(
            double signedResolvedSpeedBlocksPerSecond,
            double currentSpeedBlocksPerTick,
            double baseAccelerationBlocksPerSecondSquared,
            double effectiveAccelerationBlocksPerSecondSquared) {
        if (!Double.isFinite(signedResolvedSpeedBlocksPerSecond)
                || !Double.isFinite(currentSpeedBlocksPerTick)
                || !Double.isFinite(baseAccelerationBlocksPerSecondSquared)
                || baseAccelerationBlocksPerSecondSquared <= 0.0
                || !Double.isFinite(effectiveAccelerationBlocksPerSecondSquared)) {
            throw new IllegalArgumentException("Create boundary values are invalid");
        }
        double targetBlocksPerTick;
        if (effectiveAccelerationBlocksPerSecondSquared > 0.0) {
            targetBlocksPerTick = signedResolvedSpeedBlocksPerSecond / TICKS_PER_SECOND;
        } else if (effectiveAccelerationBlocksPerSecondSquared < 0.0) {
            targetBlocksPerTick = 0.0;
        } else {
            targetBlocksPerTick = currentSpeedBlocksPerTick;
        }
        double modifier = Math.abs(effectiveAccelerationBlocksPerSecondSquared)
                / baseAccelerationBlocksPerSecondSquared;
        if (!Double.isFinite(modifier) || modifier > Float.MAX_VALUE) {
            throw new IllegalArgumentException("Calculated acceleration modifier is invalid");
        }
        return new NotchControlResult(
                targetBlocksPerTick,
                (float) modifier,
                lastResponse,
                false);
    }

    public void suspend() {
        responseModel.reset();
        lastResponse = NotchResponseModel.neutralResponse();
    }

    public double effectiveAcceleration() {
        return responseModel.effectiveAcceleration();
    }

    public Optional<Notch> requestedNotch() {
        return Optional.of(lastResponse.commandedNotch());
    }

    public NotchResponseModel.Response lastResponse() {
        return lastResponse;
    }
}
