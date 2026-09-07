package com.lingdong.learning.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 家长账号最终注销的批量与关系恢复重试配置。 */
@ConfigurationProperties(prefix = "lingdong.parent-account-finalization")
public class ParentAccountFinalizationProperties {
    private int batchSize = 100;
    private Duration relationshipRetryDelay = Duration.ofDays(1);

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Duration getRelationshipRetryDelay() {
        return relationshipRetryDelay;
    }

    public void setRelationshipRetryDelay(Duration relationshipRetryDelay) {
        this.relationshipRetryDelay = relationshipRetryDelay;
    }
}
