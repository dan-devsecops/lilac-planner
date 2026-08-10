package com.lilac.planner.persistence.dynamodb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.EnhancedGlobalSecondaryIndex;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.UpdateTimeToLiveRequest;

import java.net.URI;

@Profile("dynamodb")
@Configuration
public class DynamoDbConfig {

    private static final Logger log = LoggerFactory.getLogger(DynamoDbConfig.class);

    static final String USERS_TABLE = "lilac_users";
    static final String DAYS_TABLE  = "lilac_days";
    static final String AUTH_TOKENS_TABLE = "lilac_auth_tokens";
    static final String PUSH_SUBSCRIPTIONS_TABLE = "lilac_push_subscriptions";
    static final String ALARM_DISPATCH_TABLE = "lilac_alarm_dispatch";

    @Value("${planner.dynamodb.endpoint:}")
    private String endpoint;

    @Value("${planner.dynamodb.region:us-east-1}")
    private String region;

    @Value("${planner.dynamodb.access-key:dummy}")
    private String accessKey;

    @Value("${planner.dynamodb.secret-key:dummy}")
    private String secretKey;

    @Bean
    public DynamoDbClient dynamoDbClient() {
        var builder = DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)));
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient client) {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
    }

    @Bean
    public DynamoDbTable<DynamoUser> usersTable(DynamoDbEnhancedClient enhanced) {
        return enhanced.table(USERS_TABLE, TableSchema.fromBean(DynamoUser.class));
    }

    @Bean
    public DynamoDbTable<DynamoDay> daysTable(DynamoDbEnhancedClient enhanced) {
        return enhanced.table(DAYS_TABLE, TableSchema.fromBean(DynamoDay.class));
    }

    @Bean
    public DynamoDbTable<DynamoAuthToken> authTokensTable(DynamoDbEnhancedClient enhanced) {
        return enhanced.table(AUTH_TOKENS_TABLE, TableSchema.fromBean(DynamoAuthToken.class));
    }

    @Bean
    public DynamoDbTable<DynamoPushSubscription> pushSubscriptionsTable(DynamoDbEnhancedClient enhanced) {
        return enhanced.table(PUSH_SUBSCRIPTIONS_TABLE, TableSchema.fromBean(DynamoPushSubscription.class));
    }

    @Bean
    public DynamoDbTable<DynamoAlarmDispatch> alarmDispatchTable(DynamoDbEnhancedClient enhanced) {
        return enhanced.table(ALARM_DISPATCH_TABLE, TableSchema.fromBean(DynamoAlarmDispatch.class));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createTablesIfMissing() {
        DynamoDbEnhancedClient enhanced = dynamoDbEnhancedClient(dynamoDbClient());
        createIfMissing(enhanced.table(USERS_TABLE, TableSchema.fromBean(DynamoUser.class)),
                USERS_TABLE, "by_username", "by_email");
        createIfMissing(enhanced.table(DAYS_TABLE, TableSchema.fromBean(DynamoDay.class)),
                DAYS_TABLE);
        createIfMissing(enhanced.table(AUTH_TOKENS_TABLE, TableSchema.fromBean(DynamoAuthToken.class)),
                AUTH_TOKENS_TABLE, "by_user");
        createIfMissing(enhanced.table(PUSH_SUBSCRIPTIONS_TABLE, TableSchema.fromBean(DynamoPushSubscription.class)),
                PUSH_SUBSCRIPTIONS_TABLE);
        createIfMissing(enhanced.table(ALARM_DISPATCH_TABLE, TableSchema.fromBean(DynamoAlarmDispatch.class)),
                ALARM_DISPATCH_TABLE);
        enableTtl(dynamoDbClient(), ALARM_DISPATCH_TABLE, "ttl");
    }

    /**
     * Enabling TTL is a one-time table setting, not part of table creation itself - this is
     * safe to call on every startup: DynamoDB (and dynamodb-local) rejects a repeat enable on
     * an attribute that's already active with a {@link DynamoDbException} whose message
     * contains "already enabled", distinguished here from a genuine failure (bad permissions,
     * throttling, a typo'd attribute/table name) so the latter is never silently swallowed -
     * TTL not enabling is not fatal to startup (JPA/Neo4j's {@code AlarmDispatchCleanupService}
     * still purges these rows either way), but it should be visible in logs.
     */
    static void enableTtl(DynamoDbClient client, String tableName, String attributeName) {
        try {
            client.updateTimeToLive(UpdateTimeToLiveRequest.builder()
                    .tableName(tableName)
                    .timeToLiveSpecification(ttl -> ttl.attributeName(attributeName).enabled(true))
                    .build());
            log.info("DynamoDB: enabled TTL on {} (attribute {})", tableName, attributeName);
        } catch (DynamoDbException e) {
            if (e.getMessage() != null && e.getMessage().contains("already enabled")) {
                log.info("DynamoDB: TTL on {} already enabled", tableName);
            } else {
                log.warn("DynamoDB: could not enable TTL on {}: {}", tableName, e.getMessage());
            }
        } catch (Exception e) {
            log.warn("DynamoDB: could not enable TTL on {}: {}", tableName, e.getMessage());
        }
    }

    private static <T> void createIfMissing(DynamoDbTable<T> table, String name, String... gsiNames) {
        try {
            if (gsiNames.length > 0) {
                EnhancedGlobalSecondaryIndex[] indices = new EnhancedGlobalSecondaryIndex[gsiNames.length];
                for (int i = 0; i < gsiNames.length; i++) {
                    indices[i] = EnhancedGlobalSecondaryIndex.builder()
                            .indexName(gsiNames[i])
                            .projection(p -> p.projectionType(ProjectionType.ALL))
                            .build();
                }
                table.createTable(b -> b.globalSecondaryIndices(indices));
            } else {
                table.createTable();
            }
            log.info("DynamoDB: created table {}", name);
        } catch (ResourceInUseException existing) {
            log.info("DynamoDB: table {} already exists", name);
        } catch (Exception e) {
            log.warn("DynamoDB: could not create table {}: {}", name, e.getMessage());
        }
    }
}
