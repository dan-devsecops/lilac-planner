package com.lilac.planner.unit;

import com.lilac.planner.persistence.dynamodb.DynamoDbConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.UpdateTimeToLiveRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("DynamoDbConfig.enableTtl - one-time table TTL setup")
class DynamoDbConfigUnitTest {

    @Mock DynamoDbClient client;

    @Test
    @DisplayName("issues an UpdateTimeToLive request for the given table and attribute")
    void enableTtl_issuesRequest() {
        invokeEnableTtl();

        ArgumentCaptor<UpdateTimeToLiveRequest> captor = ArgumentCaptor.forClass(UpdateTimeToLiveRequest.class);
        verify(client).updateTimeToLive(captor.capture());
        assertThat(captor.getValue().tableName()).isEqualTo("lilac_alarm_dispatch");
        assertThat(captor.getValue().timeToLiveSpecification().attributeName()).isEqualTo("ttl");
        assertThat(captor.getValue().timeToLiveSpecification().enabled()).isTrue();
    }

    @Test
    @DisplayName("a repeat 'already enabled' response is treated as success, not an error")
    void enableTtl_alreadyEnabled_isQuiet() {
        doThrow(DynamoDbException.builder().message("TimeToLive is already enabled").build())
                .when(client).updateTimeToLive(any(UpdateTimeToLiveRequest.class));

        assertThatCode(this::invokeEnableTtl).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a genuine failure (bad permissions, throttling, etc.) is swallowed but never mistaken for 'already enabled'")
    void enableTtl_genuineFailure_doesNotCrashStartup() {
        doThrow(DynamoDbException.builder().message("User is not authorized").build())
                .when(client).updateTimeToLive(any(UpdateTimeToLiveRequest.class));

        // Startup must never fail just because TTL couldn't be enabled - the JPA/Neo4j
        // AlarmDispatchCleanupService purge path still covers cleanup regardless.
        assertThatCode(this::invokeEnableTtl).doesNotThrowAnyException();
    }

    private void invokeEnableTtl() {
        ReflectionTestUtils.invokeMethod(DynamoDbConfig.class, "enableTtl", client, "lilac_alarm_dispatch", "ttl");
    }
}
