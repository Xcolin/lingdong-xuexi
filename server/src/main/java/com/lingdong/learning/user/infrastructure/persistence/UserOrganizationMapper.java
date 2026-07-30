package com.lingdong.learning.user.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * Persistence boundary for explicit user-to-organization membership.
 */
@Mapper
public interface UserOrganizationMapper {
    boolean exists(@Param("userId") Long userId, @Param("organizationId") Long organizationId);

    int insert(@Param("userId") Long userId, @Param("organizationId") Long organizationId);
    List<Long> findOrganizationIds(@Param("userId") Long userId);
}
