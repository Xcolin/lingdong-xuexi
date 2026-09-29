package com.lingdong.learning.auth.infrastructure.persistence;

import com.lingdong.learning.auth.application.ParentWechatBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/** 家长微信一对一绑定的持久化边界。 */
@Mapper
public interface ParentWechatBindingMapper {
    ParentWechatBinding findActiveByIdentity(@Param("appId") String appId, @Param("openId") String openId);

    ParentWechatBinding findByUserId(@Param("userId") Long userId);

    int insert(@Param("binding") ParentWechatBinding binding);

    int touchLastLogin(@Param("id") Long id, @Param("lastLoginAt") LocalDateTime lastLoginAt);

    int deleteByUserId(@Param("userId") Long userId);
}
