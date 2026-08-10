package com.lilac.planner.util;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;

import java.util.UUID;

public final class Uuids {

    private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

    private Uuids() {}

    /** Returns a UUID v7 as a {@link UUID} object (used by JPA entities). */
    public static UUID uuidV7() {
        return GENERATOR.generate();
    }

    /** Returns a UUID v7 as a lowercase hyphenated string (used by Neo4j, DynamoDB, PlannerService). */
    public static String v7() {
        return GENERATOR.generate().toString();
    }
}
