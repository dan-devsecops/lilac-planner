package com.lilac.planner.persistence.dynamodb;

import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbVersionAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * Dedup marker for (userId, date, taskId), keyed by userId partition + a
 * {@code date#taskId} sort key. {@code version} is always left {@code null} on
 * write so the enhanced client's VersionedRecordExtension conditions the put on
 * attribute_not_exists - the same insert-only-if-absent mechanism
 * {@code DynamoDbPlannerStore.getOrCreateDay} already relies on for
 * {@link DynamoDay}. A repeat insert for the same key throws
 * {@code ConditionalCheckFailedException} instead of overwriting.
 *
 * <p>{@code ttl} is an epoch-second attribute registered as this table's native DynamoDB
 * TTL attribute (see {@code DynamoDbConfig}) - items past their TTL are reclaimed
 * automatically by DynamoDB itself, so this adapter self-cleans without needing the
 * scheduled {@code deleteExpiredAlarmDispatches} sweep the JPA/Neo4j adapters rely on.
 * {@code deleteExpiredAlarmDispatches} is still implemented here (scan + delete) as a
 * belt-and-suspenders path, since DynamoDB's TTL reclamation is best-effort and can lag
 * by hours.</p>
 */
@DynamoDbBean
public class DynamoAlarmDispatch {

    private String userId;
    private String dateTaskKey;
    private String date;
    private String taskId;
    private Long version;
    private Long ttl;

    @DynamoDbPartitionKey
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    @DynamoDbSortKey
    public String getDateTaskKey() { return dateTaskKey; }
    public void setDateTaskKey(String dateTaskKey) { this.dateTaskKey = dateTaskKey; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    @DynamoDbVersionAttribute
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    /** Epoch-second expiry DynamoDB's native TTL sweeper uses to reclaim this item. */
    @DynamoDbAttribute("ttl")
    public Long getTtl() { return ttl; }
    public void setTtl(Long ttl) { this.ttl = ttl; }
}
