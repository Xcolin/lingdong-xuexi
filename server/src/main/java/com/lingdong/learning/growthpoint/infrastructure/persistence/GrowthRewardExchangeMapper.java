package com.lingdong.learning.growthpoint.infrastructure.persistence;

import com.lingdong.learning.growthpoint.domain.GrowthRewardExchange;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 奖励兑换快照、查询和条件状态迁移边界。 */
@Mapper
public interface GrowthRewardExchangeMapper {
    int insert(@Param("exchange") GrowthRewardExchange exchange);

    boolean existsActive(
            @Param("studentId") Long studentId,
            @Param("rewardId") Long rewardId
    );

    GrowthRewardExchange findByIdForUpdate(@Param("id") Long id);

    List<GrowthRewardExchange> findByStudentId(
            @Param("studentId") Long studentId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countByStudentId(@Param("studentId") Long studentId);

    int approve(
            @Param("id") Long id,
            @Param("expectedVersion") int expectedVersion,
            @Param("reviewedBy") Long reviewedBy,
            @Param("reviewedAt") LocalDateTime reviewedAt
    );

    int reject(
            @Param("id") Long id,
            @Param("expectedVersion") int expectedVersion,
            @Param("reviewedBy") Long reviewedBy,
            @Param("reviewedAt") LocalDateTime reviewedAt,
            @Param("rejectReason") String rejectReason
    );

    int verify(
            @Param("id") Long id,
            @Param("expectedVersion") int expectedVersion,
            @Param("verifiedBy") Long verifiedBy,
            @Param("verifiedAt") LocalDateTime verifiedAt
    );

    List<Long> findOverduePendingApprovalIds(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    int autoRejectOverdue(
            @Param("id") Long id,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int expirePendingByRewardId(
            @Param("rewardId") Long rewardId,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
