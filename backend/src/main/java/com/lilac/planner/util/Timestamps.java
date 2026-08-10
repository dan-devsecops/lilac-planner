package com.lilac.planner.util;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class Timestamps {

    private Timestamps() {}

    /**
     * {@code Instant.now()}, truncated to microseconds.
     *
     * <p>{@code Instant.now()}'s native resolution is platform/JDK-dependent - Linux's
     * {@code clock_gettime(CLOCK_REALTIME)} commonly yields genuine nanosecond precision,
     * while other platforms effectively cap out at microseconds. SQL {@code TIMESTAMP}
     * columns (H2 included) store at microsecond precision, so an untruncated value
     * round-trips lossily: comparing a freshly-created in-memory {@code Instant} against
     * the same value re-read from the database is exact on some platforms and off by a
     * few hundred nanoseconds on others. Truncating at creation time keeps every copy -
     * in memory or round-tripped - identical everywhere.
     */
    public static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
