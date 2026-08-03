package com.lingdong.learning.auth.infrastructure.persistence;

import com.lingdong.learning.auth.domain.DeviceSessionRecord;
import com.lingdong.learning.auth.domain.DeviceSessionStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 设备会话 MyBatis 持久化边界，所有 SQL 均位于 XML 映射文件。 */
@Mapper
public interface DeviceSessionMapper {
    int insert(@Param("session") DeviceSessionRecord session);
    DeviceSessionRecord findById(@Param("id") Long id);
    DeviceSessionRecord findActiveByAccessTokenHash(@Param("accessTokenHash") String accessTokenHash);
    DeviceSessionRecord findActiveByRefreshTokenHash(@Param("refreshTokenHash") String refreshTokenHash);
    List<DeviceSessionRecord> findActiveByUserId(@Param("userId") Long userId);
    int rotateTokens(
            @Param("id") Long id,
            @Param("expectedRefreshTokenHash") String expectedRefreshTokenHash,
            @Param("accessTokenHash") String accessTokenHash,
            @Param("refreshTokenHash") String refreshTokenHash,
            @Param("accessExpiresAt") LocalDateTime accessExpiresAt,
            @Param("refreshExpiresAt") LocalDateTime refreshExpiresAt,
            @Param("occurredAt") LocalDateTime occurredAt
    );
    int updateStatusIfActive(
            @Param("id") Long id,
            @Param("status") DeviceSessionStatus status,
            @Param("occurredAt") LocalDateTime occurredAt
    );
    int revokeAllActiveByUserId(@Param("userId") Long userId, @Param("occurredAt") LocalDateTime occurredAt);
}
