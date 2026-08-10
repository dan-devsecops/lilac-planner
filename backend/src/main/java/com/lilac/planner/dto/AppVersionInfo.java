package com.lilac.planner.dto;

/** Response body for {@code GET /api/v1/meta} - lets the mobile app decide whether to
 *  show a forced-upgrade screen and whether an update is merely available. */
public record AppVersionInfo(String minSupportedAppVersion, String latestAppVersion) {}
