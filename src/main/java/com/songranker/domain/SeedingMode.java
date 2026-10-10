package com.songranker.domain;

/** How tracks are ordered into bracket seed slots before byes/pairings are assigned. */
public enum SeedingMode {
    /** Shuffle tracks into a random order before seeding. */
    RANDOM,
    /** Keep the order the tracks were supplied in (e.g. album track order). */
    ORIGINAL_ORDER
}
