package com.lilac.planner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "planner.rate-limit")
public class RateLimitProperties {

    private int loginPerMinute = 5;
    private int registerPerMinute = 3;
    private int forgotPerMinute = 3;

    /**
     * IPs of reverse proxies whose {@code X-Forwarded-For} header may be trusted.
     * Empty (the default) means the header is ignored and the TCP peer address is
     * always used - X-Forwarded-For is client-controlled and trivially spoofable.
     */
    private List<String> trustedProxies = List.of();

    public int getLoginPerMinute() { return loginPerMinute; }
    public void setLoginPerMinute(int loginPerMinute) { this.loginPerMinute = loginPerMinute; }

    public int getRegisterPerMinute() { return registerPerMinute; }
    public void setRegisterPerMinute(int registerPerMinute) { this.registerPerMinute = registerPerMinute; }

    public int getForgotPerMinute() { return forgotPerMinute; }
    public void setForgotPerMinute(int forgotPerMinute) { this.forgotPerMinute = forgotPerMinute; }

    public List<String> getTrustedProxies() { return trustedProxies; }
    public void setTrustedProxies(List<String> trustedProxies) { this.trustedProxies = trustedProxies; }
}
