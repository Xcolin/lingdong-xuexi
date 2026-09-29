package com.lingdong.learning.growthpoint.infrastructure.persistence;

import com.lingdong.learning.growthpoint.domain.GrowthReward;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 家庭奖励的查询和带版本更新边界。 */
@Mapper
public interface GrowthRewardMapper {
    int insert(@Param("reward") GrowthReward reward);

    List<GrowthReward> findManagedByStudentId(
            @Param("studentId") Long studentId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countManagedByStudentId(@Param("studentId") Long studentId);

    List<GrowthReward> findAvailableByStudentId(
            @Param("studentId") Long studentId,
            @Param("now") LocalDateTime now,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countAvailableByStudentId(
            @Param("studentId") Long studentId,
            @Param("now") LocalDateTime now
    );

    GrowthReward findByIdForUpdate(@Param("id") Long id);

    int update(@Param("reward") GrowthReward reward, @Param("expectedVersion") int expectedVersion);

    int softDelete(
            @Param("id") Long id,
            @Param("expectedVersion") int expectedVersion,
            @Param("deletedAt") LocalDateTime deletedAt
    );

    List<Long> findExpiredOnlineIds(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    int offlineExpired(
            @Param("id") Long id,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
