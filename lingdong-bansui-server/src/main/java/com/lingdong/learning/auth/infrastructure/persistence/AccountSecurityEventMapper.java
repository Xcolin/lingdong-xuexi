package com.lingdong.learning.auth.infrastructure.persistence;

import com.lingdong.learning.auth.domain.AccountSecurityEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 账号安全事件 MyBatis 持久化边界。 */
@Mapper
public interface AccountSecurityEventMapper {
    int insertIfAbsent(@Param("event") AccountSecurityEvent event);

    List<AccountSecurityEvent> findRecentByUser(
            @Param("userId") Long userId,
            @Param("unreadOnly") boolean unreadOnly,
            @Param("limit") int limit
    );

    AccountSecurityEvent findByIdAndUserId(
            @Param("id") Long id,
            @Param("userId") Long userId
    );

    int markReadIfUnread(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("readAt") LocalDateTime readAt
    );

    int markAllRead(
            @Param("userId") Long userId,
            @Param("readAt") LocalDateTime readAt
    );
}
