package dev.edudio.createadvancedtrains.train.error;

import java.util.Objects;
import java.util.Optional;

public record CatErrorRecoveryResult(
        Status status,
        Optional<String> reason) {

    public CatErrorRecoveryResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reason, "reason");
    }

    public enum Status {
        RECOVERED,
        REJECTED,
        NO_ERROR
    }
}
