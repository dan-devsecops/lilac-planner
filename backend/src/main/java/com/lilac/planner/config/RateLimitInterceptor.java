package com.lilac.planner.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-IP rate limiter for native-auth endpoints. Active only when
 * {@code planner.auth.provider=native}; ignored for keycloak and none.
 *
 * <p>Buckets are in-memory - appropriate for single-instance deployments.
 * For multi-instance deployments, replace the bucket map with a
 * distributed cache-backed Bucket4j proxy (Redis, Hazelcast, etc.).</p>
 *
 * <p>The map is a synchronized LRU capped at {@link #MAX_BUCKETS} entries so an
 * attacker rotating source addresses cannot grow it without bound.</p>
 */
@Component
@ConditionalOnProperty(name = "planner.auth.provider", havingValue = "native")
public class RateLimitInterceptor implements HandlerInterceptor {

    /** Upper bound on distinct (path, client-IP) buckets kept in memory. */
    static final int MAX_BUCKETS = 10_000;

    private final Map<String, Bucket> buckets = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
                    return size() > MAX_BUCKETS;
                }
            });

    private final RateLimitProperties props;

    public RateLimitInterceptor(RateLimitProperties props) {
        this.props = props;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler)
            throws Exception {
        String path = req.getRequestURI();
        Bandwidth limit = limitFor(path);
        if (limit == null) return true;

        String key = path + ":" + resolveClientIp(req);
        Bucket bucket = buckets.computeIfAbsent(key, k -> Bucket.builder().addLimit(limit).build());

        if (bucket.tryConsume(1)) return true;

        res.setStatus(429);
        res.setContentType("application/problem+json");
        res.getWriter().write("""
                {"status":429,"title":"Too Many Requests",\
                "detail":"Rate limit exceeded - please wait before retrying."}""");
        return false;
    }

    private Bandwidth limitFor(String path) {
        return switch (path) {
            case "/api/v1/auth/login" -> perMinute(props.getLoginPerMinute());
            case "/api/v1/auth/register" -> perMinute(props.getRegisterPerMinute());
            case "/api/v1/auth/forgot-password" -> perMinute(props.getForgotPerMinute());
            default -> null;
        };
    }

    private static Bandwidth perMinute(int tokens) {
        return Bandwidth.builder()
                .capacity(tokens)
                .refillIntervally(tokens, Duration.ofMinutes(1))
                .build();
    }

    /**
     * Resolves the client IP used to key the rate-limit bucket.
     *
     * <p>{@code X-Forwarded-For} is a plain request header the client fully controls,
     * so trusting it unconditionally lets an attacker mint a fresh bucket per request
     * and bypass the limiter entirely. It is therefore ignored unless the request
     * actually arrived from a proxy listed in
     * {@code planner.rate-limit.trusted-proxies}; otherwise the TCP peer address
     * ({@link HttpServletRequest#getRemoteAddr()}) is used.</p>
     *
     * <p>When the header is honoured, the <em>last</em> entry of the comma-separated
     * list is used: each proxy appends the address of the peer that connected to it,
     * so the last value is the one appended by our own trusted proxy - the address it
     * actually observed. Earlier entries come from untrusted hops (or the client
     * itself) and can carry arbitrary attacker-chosen values.</p>
     */
    private String resolveClientIp(HttpServletRequest req) {
        String remoteAddr = req.getRemoteAddr();
        if (!props.getTrustedProxies().contains(remoteAddr)) {
            return remoteAddr;
        }
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return remoteAddr;
        }
        String[] hops = forwarded.split(",");
        return hops[hops.length - 1].trim();
    }
}
