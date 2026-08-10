package com.lilac.planner.service;

import com.lilac.planner.domain.Role;
import com.lilac.planner.persistence.PlannerStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds a native ADMIN account from configuration at startup - the same bootstrap pattern
 * Keycloak uses with {@code KEYCLOAK_ADMIN}/{@code KEYCLOAK_ADMIN_PASSWORD}. Active only when
 * {@code planner.auth.provider=native}.
 *
 * <p>Idempotent and conservative: it creates the admin only when credentials are configured AND
 * no ADMIN user exists yet AND the username/email are free. Otherwise it does nothing, so it is
 * safe to leave enabled across restarts. When no credentials are configured the first-user-becomes-
 * admin rule in {@link NativeAuthService} still applies (handy for local dev).</p>
 */
@Component
@ConditionalOnProperty(name = "planner.auth.provider", havingValue = "native")
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final PlannerStore store;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String email;

    public AdminBootstrap(PlannerStore store, PasswordEncoder passwordEncoder,
                          @Value("${planner.auth.native.admin.username:}") String username,
                          @Value("${planner.auth.native.admin.password:}") String password,
                          @Value("${planner.auth.native.admin.email:}") String email) {
        this.store = store;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
        this.email = email;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (isBlank(username) || isBlank(password)) {
            return; // no bootstrap configured - first registrant becomes admin instead
        }
        if (store.listUsers().stream().anyMatch(u -> u.hasRole(Role.ADMIN))) {
            log.debug("Bootstrap admin skipped: an ADMIN user already exists");
            return;
        }
        if (store.findUserByUsername(username).isPresent()) {
            log.warn("Bootstrap admin '{}' skipped: that username already exists", username);
            return;
        }
        String adminEmail = isBlank(email) ? username + "@local" : email;
        if (store.findUserByEmail(adminEmail).isPresent()) {
            log.warn("Bootstrap admin '{}' skipped: email '{}' already exists", username, adminEmail);
            return;
        }
        store.createNativeUser(username, adminEmail, username,
                passwordEncoder.encode(password), List.of(Role.ADMIN));
        log.info("Bootstrapped native ADMIN user '{}' from configuration", username);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
