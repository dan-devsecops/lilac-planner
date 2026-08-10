package com.lilac.planner.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

/**
 * Bounds user-supplied dates to a sane window. {@code LocalDate} parses ISO dates
 * up to year ±999999999; unchecked, those exhaust storage via create-on-GET days,
 * overflow the SQL {@code DATE} column past 9999-12-31, and break DynamoDB's
 * string-sorted range queries. Violations surface as 400 problem-detail responses
 * via {@link ResponseStatusException} (rendered by {@link ApiExceptionHandler}'s
 * base class).
 */
final class DateWindow {

    static final LocalDate MIN = LocalDate.of(1900, 1, 1);
    static final LocalDate MAX = LocalDate.of(2100, 12, 31);

    /** Longest statistics range we will aggregate, in years. */
    static final long MAX_RANGE_YEARS = 5;

    private DateWindow() {}

    /** Rejects dates outside {@link #MIN}..{@link #MAX} with a 400. */
    static LocalDate require(LocalDate date, String paramName) {
        if (date.isBefore(MIN) || date.isAfter(MAX)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Parameter '" + paramName + "' must be between " + MIN + " and " + MAX);
        }
        return date;
    }

    /** Rejects inverted ranges and ranges longer than {@link #MAX_RANGE_YEARS} years with a 400. */
    static void requireRange(LocalDate from, LocalDate to) {
        require(from, "from");
        require(to, "to");
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Parameter 'from' must not be after 'to'");
        }
        if (from.plusYears(MAX_RANGE_YEARS).isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Date range must not exceed " + MAX_RANGE_YEARS + " years");
        }
    }
}
