package dev.edudio.createadvancedtrains.speed;

/**
 * Speed limit sources used by Create: Advanced Trains.
 *
 * <p>
 * All speed limit values associated with these sources
 * are expressed in blocks per second (blocks/s).
 * </p>
 */
public enum SpeedLimitSource {

    /**
     * Permanent line speed limit.
     */
    LINE,

    /**
     * Temporary speed restriction.
     */
    TEMPORARY,

    /**
     * Speed restriction imposed by a signal.
     */
    SIGNAL,

    /**
     * Speed restriction associated with a station.
     */
    STATION,

    /**
     * ATC speed restriction.
     */
    ATC,

    /**
     * ATS speed restriction.
     */
    ATS,

    /**
     * Temporary limit used for testing and development.
     */
    TEST
}