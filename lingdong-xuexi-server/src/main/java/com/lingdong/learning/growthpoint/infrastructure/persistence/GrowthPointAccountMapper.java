package com.lingdong.learning.growthpoint.infrastructure.persistence;

import com.lingdong.learning.growthpoint.domain.GrowthPointAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/** 积分账户创建、锁定和余额更新边界。 */
@Mapper
public interface GrowthPointAccountMapper {
    int insertInitial(@Param("studentId") Long studentId);

    GrowthPointAccount findByStudentIdForUpdate(@Param("studentId") Long studentId);

    int addTaskReward(
            @Param("id") Long id,
            @Param("points") long points,
            @Param("expectedVersion") int expectedVersion,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int applyCorrection(
            @Param("id") Long id,
            @Param("totalDeduction") long totalDeduction,
            @Param("availableDeduction") long availableDeduction,
            @Param("expectedVersion") int expectedVersion,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int redeem(
            @Param("id") Long id,
            @Param("points") long points,
            @Param("expectedVersion") int expectedVersion,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int clearAvailablePoints(
            @Param("id") Long id,
            @Param("points") long points,
            @Param("expectedVersion") int expectedVersion,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
