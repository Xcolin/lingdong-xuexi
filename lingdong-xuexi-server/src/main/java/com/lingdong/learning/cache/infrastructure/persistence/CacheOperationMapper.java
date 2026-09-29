package com.lingdong.learning.cache.infrastructure.persistence;

import com.lingdong.learning.cache.application.CacheReviewQueueItem;
import com.lingdong.learning.cache.domain.CacheOperation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 缓存管理操作台账的持久化边界。 */
@Mapper
public interface CacheOperationMapper {
    CacheOperation findById(@Param("id") Long id);

    CacheOperation findByCode(@Param("code") String code);

    CacheOperation findByTaskId(@Param("taskId") Long taskId);

    List<CacheOperation> findRecent(@Param("limit") int limit);

    List<CacheReviewQueueItem> findPendingReviews();

    int insert(@Param("operation") CacheOperation operation);

    int markSucceeded(@Param("id") Long id, @Param("executedBy") Long executedBy);

    int markRejected(@Param("id") Long id);

    int markFailed(
            @Param("id") Long id,
            @Param("executedBy") Long executedBy,
            @Param("failureMessage") String failureMessage
    );
}
