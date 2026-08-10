package com.lilac.planner.unit;

import com.lilac.planner.config.RateLimitInterceptor;
import com.lilac.planner.config.RateLimitProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimitInterceptor - client IP resolution and bucket keying")
class RateLimitInterceptorUnitTest {

    private static final String LOGIN = "/api/v1/auth/login";

    private static RateLimitProperties props(List<String> trustedProxies) {
        RateLimitProperties props = new RateLimitProperties();
        props.setLoginPerMinute(3);
        props.setRegisterPerMinute(3);
        props.setForgotPerMinute(3);
        props.setTrustedProxies(trustedProxies);
        return props;
    }

    private static boolean request(RateLimitInterceptor interceptor, String remoteAddr,
                                   String xff) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", LOGIN);
        req.setRequestURI(LOGIN);
        req.setRemoteAddr(remoteAddr);
        if (xff != null) {
            req.addHeader("X-Forwarded-For", xff);
        }
        return interceptor.preHandle(req, new MockHttpServletResponse(), new Object());
    }

    @Test
    @DisplayName("spoofed X-Forwarded-For from the same remote address shares one bucket - limit still trips")
    void spoofedXffDoesNotMintFreshBuckets() throws Exception {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(props(List.of()));

        // 3 allowed requests, each with a different forged XFF
        assertThat(request(interceptor, "203.0.113.7", "1.1.1.1")).isTrue();
        assertThat(request(interceptor, "203.0.113.7", "2.2.2.2")).isTrue();
        assertThat(request(interceptor, "203.0.113.7", "3.3.3.3")).isTrue();

        // 4th request with yet another forged XFF must be rejected: same bucket
        assertThat(request(interceptor, "203.0.113.7", "4.4.4.4")).isFalse();
    }

    @Test
    @DisplayName("429 response is written when the limit trips")
    void rejectionWrites429() throws Exception {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(props(List.of()));
        for (int i = 0; i < 3; i++) {
            assertThat(request(interceptor, "203.0.113.7", null)).isTrue();
        }

        MockHttpServletRequest req = new MockHttpServletRequest("POST", LOGIN);
        req.setRequestURI(LOGIN);
        req.setRemoteAddr("203.0.113.7");
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(req, res, new Object())).isFalse();
        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getContentAsString()).contains("Too Many Requests");
    }

    @Test
    @DisplayName("X-Forwarded-For IS honoured when the remote address is a trusted proxy")
    void xffHonouredBehindTrustedProxy() throws Exception {
        RateLimitInterceptor interceptor =
                new RateLimitInterceptor(props(List.of("10.0.0.1")));

        // Exhaust the bucket for client 198.51.100.10 as seen by the trusted proxy
        for (int i = 0; i < 3; i++) {
            assertThat(request(interceptor, "10.0.0.1", "198.51.100.10")).isTrue();
        }
        assertThat(request(interceptor, "10.0.0.1", "198.51.100.10")).isFalse();

        // A different real client behind the same proxy gets its own bucket
        assertThat(request(interceptor, "10.0.0.1", "198.51.100.99")).isTrue();
    }

    @Test
    @DisplayName("behind a trusted proxy, only the LAST XFF entry (appended by our proxy) is used")
    void lastXffEntryWinsBehindTrustedProxy() throws Exception {
        RateLimitInterceptor interceptor =
                new RateLimitInterceptor(props(List.of("10.0.0.1")));

        // The client prepends junk; the trusted proxy appends the address it saw.
        // The forged leading entries must not change the bucket key.
        assertThat(request(interceptor, "10.0.0.1", "6.6.6.6, 198.51.100.10")).isTrue();
        assertThat(request(interceptor, "10.0.0.1", "7.7.7.7, 198.51.100.10")).isTrue();
        assertThat(request(interceptor, "10.0.0.1", "8.8.8.8, 198.51.100.10")).isTrue();
        assertThat(request(interceptor, "10.0.0.1", "9.9.9.9, 198.51.100.10")).isFalse();
    }

    @Test
    @DisplayName("requests from an untrusted address ignore XFF even when trusted proxies are configured")
    void untrustedRemoteAddrIgnoresXff() throws Exception {
        RateLimitInterceptor interceptor =
                new RateLimitInterceptor(props(List.of("10.0.0.1")));

        assertThat(request(interceptor, "203.0.113.7", "1.1.1.1")).isTrue();
        assertThat(request(interceptor, "203.0.113.7", "2.2.2.2")).isTrue();
        assertThat(request(interceptor, "203.0.113.7", "3.3.3.3")).isTrue();
        assertThat(request(interceptor, "203.0.113.7", "4.4.4.4")).isFalse();
    }

    @Test
    @DisplayName("non-rate-limited paths pass through untouched")
    void otherPathsPassThrough() throws Exception {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(props(List.of()));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        req.setRequestURI("/api/v1/auth/me");
        req.setRemoteAddr("203.0.113.7");
        for (int i = 0; i < 10; i++) {
            assertThat(interceptor.preHandle(req, new MockHttpServletResponse(), new Object()))
                    .isTrue();
        }
    }
}
