package com.lilac.planner.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lilac.planner.model.PushSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends push notifications to Expo push tokens via {@code POST https://exp.host/--/api/v2/push/send}.
 * Optionally authenticates with {@code EXPO_ACCESS_TOKEN} when configured; Expo does not require an
 * access token for the push endpoint itself, so this sender is always considered "configured".
 */
@Service
public class ExpoPushSender {

    private static final URI EXPO_PUSH_ENDPOINT = URI.create("https://exp.host/--/api/v2/push/send");

    private static final Logger log = LoggerFactory.getLogger(ExpoPushSender.class);

    private final ObjectMapper objectMapper;
    private final String accessToken;

    private HttpClient httpClient = HttpClient.newHttpClient();
    private URI endpoint = EXPO_PUSH_ENDPOINT;

    public ExpoPushSender(ObjectMapper objectMapper, @Value("${planner.push.expo.access-token:}") String accessToken) {
        this.objectMapper = objectMapper;
        this.accessToken = accessToken;
    }

    public PushSendResult send(PushSubscription subscription, PushPayload payload) {
        try {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("to", subscription.getToken());
            message.put("title", payload.title());
            message.put("body", payload.body());
            if (!payload.data().isEmpty()) {
                message.put("data", payload.data());
            }
            String requestBody = objectMapper.writeValueAsString(List.of(message));

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(endpoint)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody));
            if (accessToken != null && !accessToken.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + accessToken);
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();

            // Expo signals an unregistered device via a per-ticket "DeviceNotRegistered" error inside
            // a 200 response (see interpretTicket), not via a transport-level status code - a raw 404
            // here means something is wrong with the request/endpoint itself, not the subscription.
            if (statusCode < 200 || statusCode >= 300) {
                log.warn("Expo push to {} failed with HTTP {}", subscription.getId(), statusCode);
                return PushSendResult.transientFailure("HTTP " + statusCode);
            }

            return interpretTicket(subscription, response.body());
        } catch (IOException e) {
            log.warn("Expo push to {} failed: {}", subscription.getId(), e.toString());
            return PushSendResult.transientFailure(describe(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PushSendResult.transientFailure("Interrupted");
        }
    }

    private PushSendResult interpretTicket(PushSubscription subscription, String responseBody) {
        JsonNode ticket;
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            ticket = firstTicket(root.path("data"));
        } catch (IOException e) {
            log.warn("Expo push to {} returned an unparseable response: {}", subscription.getId(), e.toString());
            return PushSendResult.transientFailure("Malformed Expo response");
        }
        if (ticket == null || ticket.isMissingNode()) {
            return PushSendResult.transientFailure("Malformed Expo response: no ticket");
        }

        String status = ticket.path("status").asText("");
        if ("ok".equals(status)) {
            return PushSendResult.success();
        }

        String errorCode = ticket.path("details").path("error").asText("");
        if ("DeviceNotRegistered".equals(errorCode)) {
            log.info("Expo push subscription {} is no longer registered", subscription.getId());
            return PushSendResult.invalidSubscription();
        }

        String message = ticket.path("message").asText("Expo push error");
        log.warn("Expo push to {} returned an error ticket: {}", subscription.getId(), message);
        return PushSendResult.transientFailure(message);
    }

    private static String describe(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }

    private static JsonNode firstTicket(JsonNode data) {
        if (data.isArray()) {
            return data.isEmpty() ? null : data.get(0);
        }
        if (data.isObject()) {
            return data;
        }
        return null;
    }
}
