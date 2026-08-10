package com.lilac.planner.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lilac.planner.domain.Platform;
import com.lilac.planner.model.PushSubscription;
import com.lilac.planner.service.PushPayload;
import com.lilac.planner.service.PushSendResult;
import com.lilac.planner.service.WebPushSender;
import com.sun.net.httpserver.HttpServer;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real {@code nl.martijndwars:web-push} VAPID signing/encryption path against a
 * loopback {@link HttpServer} standing in for a browser push service, rather than mocking the
 * library's internally-constructed Apache async HTTP client (it isn't injectable).
 */
class WebPushSenderUnitTest {

    private static String vapidPublicKey;
    private static String vapidPrivateKey;
    private static String clientP256dh;
    private static String clientAuth;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void generateKeys() throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        KeyPair vapidKeyPair = generateEcKeyPair();
        vapidPublicKey = base64Url(Utils.encode((ECPublicKey) vapidKeyPair.getPublic()));
        vapidPrivateKey = base64Url(Utils.encode((ECPrivateKey) vapidKeyPair.getPrivate()));

        KeyPair clientKeyPair = generateEcKeyPair();
        clientP256dh = base64Url(Utils.encode((ECPublicKey) clientKeyPair.getPublic()));

        byte[] authSecret = new byte[16];
        new SecureRandom().nextBytes(authSecret);
        clientAuth = base64Url(authSecret);
    }

    private static KeyPair generateEcKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("ECDH", "BC");
        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("prime256v1");
        generator.initialize(spec);
        return generator.generateKeyPair();
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static HttpServer startServer(int statusCode) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/push", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(statusCode, -1);
            exchange.close();
        });
        server.start();
        return server;
    }

    private PushSubscription subscription(int port) {
        PushSubscription subscription = new PushSubscription(
                "user-1", Platform.WEB, "http://localhost:" + port + "/push");
        subscription.setId("sub-1");
        subscription.setP256dh(clientP256dh);
        subscription.setAuth(clientAuth);
        return subscription;
    }

    @Test
    void configuredSender_successResponse_returnsSuccess() throws Exception {
        HttpServer server = startServer(201);
        try {
            WebPushSender sender = new WebPushSender(objectMapper, vapidPublicKey, vapidPrivateKey, "mailto:test@example.com");

            PushSendResult result = sender.send(subscription(server.getAddress().getPort()), new PushPayload("Title", "Body"));

            assertThat(result.isSuccess()).isTrue();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void configuredSender_goneResponse_returnsInvalidSubscription() throws Exception {
        HttpServer server = startServer(410);
        try {
            WebPushSender sender = new WebPushSender(objectMapper, vapidPublicKey, vapidPrivateKey, "mailto:test@example.com");

            PushSendResult result = sender.send(subscription(server.getAddress().getPort()), new PushPayload("Title", "Body"));

            assertThat(result.isInvalidSubscription()).isTrue();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void configuredSender_notFoundResponse_returnsInvalidSubscription() throws Exception {
        HttpServer server = startServer(404);
        try {
            WebPushSender sender = new WebPushSender(objectMapper, vapidPublicKey, vapidPrivateKey, "mailto:test@example.com");

            PushSendResult result = sender.send(subscription(server.getAddress().getPort()), new PushPayload("Title", "Body"));

            assertThat(result.isInvalidSubscription()).isTrue();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void configuredSender_serverErrorResponse_returnsTransientFailure() throws Exception {
        HttpServer server = startServer(500);
        try {
            WebPushSender sender = new WebPushSender(objectMapper, vapidPublicKey, vapidPrivateKey, "mailto:test@example.com");

            PushSendResult result = sender.send(subscription(server.getAddress().getPort()), new PushPayload("Title", "Body"));

            assertThat(result.status()).isEqualTo(PushSendResult.Status.TRANSIENT_FAILURE);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unconfiguredSender_returnsUnavailableWithoutAttemptingToSend() {
        WebPushSender sender = new WebPushSender(objectMapper, "", "", "");

        assertThat(sender.isConfigured()).isFalse();

        PushSendResult result = sender.send(subscription(1), new PushPayload("Title", "Body"));

        assertThat(result.status()).isEqualTo(PushSendResult.Status.UNAVAILABLE);
    }
}
