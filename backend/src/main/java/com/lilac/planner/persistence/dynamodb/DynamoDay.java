package com.lilac.planner.persistence.dynamodb;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbVersionAttribute;

import java.util.ArrayList;
import java.util.List;

/** Day items are stored as one row per (userId, date) with tasks embedded. */
@DynamoDbBean
public class DynamoDay {

    private String userId;       // partition key
    private String date;         // sort key (ISO yyyy-MM-dd)
    private String id;
    private Long version;        // optimistic locking - managed by the Enhanced Client
    private List<DynamoTask> tasks = new ArrayList<>();
    private List<String> earnedStickers = new ArrayList<>();

    @DynamoDbPartitionKey
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    @DynamoDbSortKey
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    @DynamoDbVersionAttribute
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public List<DynamoTask> getTasks() { return tasks; }
    public void setTasks(List<DynamoTask> tasks) { this.tasks = tasks == null ? new ArrayList<>() : tasks; }

    public List<String> getEarnedStickers() { return earnedStickers; }
    public void setEarnedStickers(List<String> earnedStickers) {
        this.earnedStickers = earnedStickers == null ? new ArrayList<>() : earnedStickers;
    }
}
