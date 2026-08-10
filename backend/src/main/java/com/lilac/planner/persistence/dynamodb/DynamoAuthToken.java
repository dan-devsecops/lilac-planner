package com.lilac.planner.persistence.dynamodb;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

@DynamoDbBean
public class DynamoAuthToken {

    private String tokenHash;
    private String type;
    private String userId;
    private String expiresAt;
    private String createdAt;

    @DynamoDbPartitionKey
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    @DynamoDbSecondaryPartitionKey(indexNames = "by_user")
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
