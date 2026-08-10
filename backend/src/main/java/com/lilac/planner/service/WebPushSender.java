package com.lilac.planner.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lilac.planner.model.PushSubscription;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.concurrent.ExecutionException;

/**
 * Sends VAPID-signed Web Push notifications (browser {@code PushSubscription} endpoints) via the
 * {@code nl.martijndwars:web-push} library. No-ops when {@code PLANNER_VAPID_PUBLIC_KEY} /
 * {@code PLANNER_VAPID_PRIVATE_KEY} are unset, so the backend still starts cleanly without push
 * configured (REQ-NF-004) - mirroring {@link MailService}'s "not configured" handling.
 */
@Service
public class WebPushSender {

    private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final ObjectMapper objectMapper;
    private final PushService pushService;

    public WebPushSender(
            ObjectMapper objectMapper,
            @Value("${planner.push.vapid.public-key:}") String publicKey,
            @Value("${planner.push.vapid.private-key:}") String privateKey,
            @Value("${planner.push.vapid.subject:}") String subject) {
        this.objectMapper = objectMapper;
        this.pushService = buildPushService(publicKey, privateKey, subject);
    }

    private static PushService buildPushService(String publicKey, String privateKey, String subject) {
        if (publicKey == null || publicKey.isBlank() || privateKey == null || privateKey.isBlank()) {
            return null;
        }
        try {
            return new PushService(publicKey, privateKey, subject == null || subject.isBlank() ? null : subject);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // IllegalArgumentException: Utils.loadPublicKey/loadPrivateKey base64url-decode the
            // configured keys and throw this (unchecked) for malformed input.
            throw new IllegalStateException("PLANNER_VAPID_PUBLIC_KEY/PLANNER_VAPID_PRIVATE_KEY are set but not a valid VAPID key pair", e);
        }
    }

    public boolean isConfigured() {
        return pushService != null;
    }

    public PushSendResult send(PushSubscription subscription, PushPayload payload) {
        if (pushService == null) {
            log.debug("Web push not configured (VAPID keys unset); skipping subscription {}", subscription.getId());
            return PushSendResult.unavailable();
        }
        try {
            Subscription webPushSubscription = new Subscription(
                    subscription.getToken(),
                    new Subscription.Keys(subscription.getP256dh(), subscription.getAuth()));
            String body = objectMapper.writeValueAsString(payload);
            Notification notification = new Notification(webPushSubscription, body);

            HttpResponse response = pushService.send(notification);
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode == 404 || statusCode == 410) {
                log.info("Web push subscription {} is no longer valid (HTTP {})", subscription.getId(), statusCode);
                return PushSendResult.invalidSubscription();
            }
            if (statusCode >= 200 && statusCode < 300) {
                return PushSendResult.success();
            }
            log.warn("Web push to subscription {} failed with HTTP {}", subscription.getId(), statusCode);
            return PushSendResult.transientFailure("HTTP " + statusCode);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PushSendResult.transientFailure("Interrupted");
        } catch (GeneralSecurityException | IOException | JoseException | ExecutionException e) {
            log.warn("Web push to subscription {} failed: {}", subscription.getId(), e.toString());
            return PushSendResult.transientFailure(describe(e));
        }
    }

    private static String describe(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }
}
