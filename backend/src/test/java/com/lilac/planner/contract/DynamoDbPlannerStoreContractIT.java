package com.lilac.planner.contract;

import com.lilac.planner.persistence.PlannerStore;
import com.lilac.planner.persistence.dynamodb.DynamoAlarmDispatch;
import com.lilac.planner.persistence.dynamodb.DynamoAuthToken;
import com.lilac.planner.persistence.dynamodb.DynamoDay;
import com.lilac.planner.persistence.dynamodb.DynamoDbPlannerStore;
import com.lilac.planner.persistence.dynamodb.DynamoPushSubscription;
import com.lilac.planner.persistence.dynamodb.DynamoUser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.EnhancedGlobalSecondaryIndex;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;

import java.net.URI;

/**
 * Runs the {@link PlannerStoreContractTest} against the DynamoDB adapter on
 * a real dynamodb-local started by Testcontainers. No Spring context - the
 * adapter is constructed directly, mirroring the table setup in
 * {@code DynamoDbConfig}. Skipped automatically when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("PlannerStore contract - DynamoDB (Testcontainers)")
class DynamoDbPlannerStoreContractIT extends PlannerStoreContractTest {

    @Container
    static GenericContainer<?> dynamo =
            new GenericContainer<>("amazon/dynamodb-local:2.5.2").withExposedPorts(8000);

    static DynamoDbPlannerStore store;

    @BeforeAll
    static void createTables() {
        DynamoDbClient client = DynamoDbClient.builder()
                .endpointOverride(URI.create("http://" + dynamo.getHost() + ":" + dynamo.getMappedPort(8000)))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("dummy", "dummy")))
                .build();
        DynamoDbEnhancedClient enhanced = DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();

        DynamoDbTable<DynamoUser> users = enhanced.table("lilac_users", TableSchema.fromBean(DynamoUser.class));
        DynamoDbTable<DynamoDay> days = enhanced.table("lilac_days", TableSchema.fromBean(DynamoDay.class));
        DynamoDbTable<DynamoAuthToken> authTokens =
                enhanced.table("lilac_auth_tokens", TableSchema.fromBean(DynamoAuthToken.class));
        DynamoDbTable<DynamoPushSubscription> pushSubscriptions =
                enhanced.table("lilac_push_subscriptions", TableSchema.fromBean(DynamoPushSubscription.class));
        DynamoDbTable<DynamoAlarmDispatch> alarmDispatches =
                enhanced.table("lilac_alarm_dispatch", TableSchema.fromBean(DynamoAlarmDispatch.class));

        // Mirrors DynamoDbConfig.createTablesIfMissing
        users.createTable(b -> b.globalSecondaryIndices(
                gsi("by_username"), gsi("by_email")));
        days.createTable();
        authTokens.createTable(b -> b.globalSecondaryIndices(gsi("by_user")));
        pushSubscriptions.createTable();
        alarmDispatches.createTable();

        store = new DynamoDbPlannerStore(enhanced, users, days, authTokens, pushSubscriptions, alarmDispatches);
    }

    private static EnhancedGlobalSecondaryIndex gsi(String name) {
        return EnhancedGlobalSecondaryIndex.builder()
                .indexName(name)
                .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                .build();
    }

    @Override
    protected PlannerStore store() {
        return store;
    }
}
