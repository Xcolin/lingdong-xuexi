package com.lingdong.learning.user.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Persistence boundary for grants of roles to users in a global or organizational scope.
 */
@Mapper
public interface UserRoleMapper {
    boolean hasRoleCode(@Param("userId") Long userId, @Param("roleCode") String roleCode);
    boolean hasPermissionViaRole(@Param("userId") Long userId, @Param("permissionId") Long permissionId);
    boolean exists(
            @Param("userId") Long userId,
            @Param("roleId") Long roleId,
            @Param("organizationScopeKey") String organizationScopeKey
    );

    int insert(
            @Param("userId") Long userId,
            @Param("roleId") Long roleId,
            @Param("organizationId") Long organizationId,
            @Param("organizationScopeKey") String organizationScopeKey
    );
}
