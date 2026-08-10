package com.lilac.planner.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lilac.planner.domain.Platform;
import com.lilac.planner.model.PushSubscription;
import com.lilac.planner.service.ExpoPushSender;
import com.lilac.planner.service.PushPayload;
import com.lilac.planner.service.PushSendResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpoPushSenderUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private HttpClient mockClientReturning(int statusCode, String body) throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        return client;
    }

    private ExpoPushSender sender(HttpClient httpClient, String accessToken) {
        ExpoPushSender sender = new ExpoPushSender(objectMapper, accessToken);
        ReflectionTestUtils.setField(sender, "httpClient", httpClient);
        return sender;
    }

    private PushSubscription subscription() {
        PushSubscription subscription = new PushSubscription("user-1", Platform.EXPO, "ExponentPushToken[abc]");
        subscription.setId("sub-1");
        return subscription;
    }

    @Test
    void okTicket_returnsSuccess() throws Exception {
        HttpClient client = mockClientReturning(200, "{\"data\":[{\"status\":\"ok\",\"id\":\"receipt-1\"}]}");

        PushSendResult result = sender(client, "").send(subscription(), new PushPayload("Title", "Body"));

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void deviceNotRegisteredTicket_returnsInvalidSubscription() throws Exception {
        String body = "{\"data\":[{\"status\":\"error\",\"message\":\"not registered\","
                + "\"details\":{\"error\":\"DeviceNotRegistered\"}}]}";
        HttpClient client = mockClientReturning(200, body);

        PushSendResult result = sender(client, "").send(subscription(), new PushPayload("Title", "Body"));

        assertThat(result.isInvalidSubscription()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {410, 404, 500})
    void httpErrorStatus_returnsTransientFailure(int statusCode) throws Exception {
        // Unlike Web Push, Expo doesn't use transport-level 404/410 to mean "device unregistered" -
        // that signal only ever arrives as a per-ticket DeviceNotRegistered error in a 200 response.
        HttpClient client = mockClientReturning(statusCode, "");

        PushSendResult result = sender(client, "").send(subscription(), new PushPayload("Title", "Body"));

        assertThat(result.status()).isEqualTo(PushSendResult.Status.TRANSIENT_FAILURE);
    }

    @Test
    void otherErrorTicket_returnsTransientFailure() throws Exception {
        String body = "{\"data\":[{\"status\":\"error\",\"message\":\"rate limited\","
                + "\"details\":{\"error\":\"MessageRateExceeded\"}}]}";
        HttpClient client = mockClientReturning(200, body);

        PushSendResult result = sender(client, "").send(subscription(), new PushPayload("Title", "Body"));

        assertThat(result.status()).isEqualTo(PushSendResult.Status.TRANSIENT_FAILURE);
        assertThat(result.detail()).isEqualTo("rate limited");
    }

    @Test
    void requestBody_isWellFormedAndCarriesConfiguredAccessToken() throws Exception {
        CapturedRequest captured = new CapturedRequest();
        HttpServer server = startCapturingServer(captured, 200, "{\"data\":[{\"status\":\"ok\"}]}");
        try {
            ExpoPushSender sender = new ExpoPushSender(objectMapper, "secret-token");
            ReflectionTestUtils.setField(sender, "endpoint",
                    URI.create("http://localhost:" + server.getAddress().getPort() + "/--/api/v2/push/send"));

            PushSendResult result = sender.send(subscription(), new PushPayload("Title", "Body", Map.of("taskId", "t-1")));

            assertThat(result.isSuccess()).isTrue();
            assertThat(captured.method).isEqualTo("POST");
            assertThat(captured.authorizationHeader).isEqualTo("Bearer secret-token");
            assertThat(captured.body)
                    .contains("\"to\":\"ExponentPushToken[abc]\"")
                    .contains("\"title\":\"Title\"")
                    .contains("\"body\":\"Body\"")
                    .contains("\"taskId\":\"t-1\"");
        } finally {
            server.stop(0);
        }
    }

    private static final class CapturedRequest {
        volatile String method;
        volatile String authorizationHeader;
        volatile String body;
    }

    private HttpServer startCapturingServer(CapturedRequest captured, int responseStatus, String responseBody) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/--/api/v2/push/send", exchange -> {
            captured.method = exchange.getRequestMethod();
            captured.authorizationHeader = exchange.getRequestHeaders().getFirst("Authorization");
            captured.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    @Test
    void noAccessTokenConfigured_omitsAuthorizationHeader() throws Exception {
        HttpClient client = mockClientReturning(200, "{\"data\":[{\"status\":\"ok\"}]}");

        sender(client, "").send(subscription(), new PushPayload("Title", "Body"));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(captor.capture(), any(HttpResponse.BodyHandler.class));

        assertThat(captor.getValue().headers().firstValue("Authorization")).isEmpty();
    }
}
