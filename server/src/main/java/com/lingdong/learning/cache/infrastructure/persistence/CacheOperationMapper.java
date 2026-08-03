package com.lingdong.learning.cache.infrastructure.persistence;

import com.lingdong.learning.cache.domain.CacheOperation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence boundary for cache-management audit records. */
@Mapper
public interface CacheOperationMapper {
    CacheOperation findById(@Param("id") Long id);

    CacheOperation findByCode(@Param("code") String code);

    CacheOperation findByTaskId(@Param("taskId") Long taskId);

    int insert(@Param("operation") CacheOperation operation);

    int markSucceeded(@Param("id") Long id, @Param("executedBy") Long executedBy);

    int markFailed(
            @Param("id") Long id,
            @Param("executedBy") Long executedBy,
            @Param("failureMessage") String failureMessage
    );
}
