package com.lilac.planner.controller;

import com.lilac.planner.dto.AppVersionInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, unauthenticated app-version gate. Mobile apps cannot be force-updated once shipped, so
 * this is the kill switch: the app compares its own version against
 * {@code minSupportedAppVersion} on launch and shows a forced-upgrade screen if it falls below it.
 * Public across all auth providers (see {@code SecurityConfig}) - a locked-out user must still be
 * able to learn that an update is required.
 */
@RestController
@RequestMapping("/api/v1/meta")
public class MetaController {

    private final String minSupportedAppVersion;
    private final String latestAppVersion;

    public MetaController(
            @Value("${planner.mobile.min-supported-app-version}") String minSupportedAppVersion,
            @Value("${planner.mobile.latest-app-version}") String latestAppVersion) {
        this.minSupportedAppVersion = minSupportedAppVersion;
        this.latestAppVersion = latestAppVersion;
    }

    @GetMapping
    public AppVersionInfo get() {
        return new AppVersionInfo(minSupportedAppVersion, latestAppVersion);
    }
}
