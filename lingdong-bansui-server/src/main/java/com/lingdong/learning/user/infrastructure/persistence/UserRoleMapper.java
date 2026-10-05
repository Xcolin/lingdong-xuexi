package com.lingdong.learning.user.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Persistence boundary for grants of roles to users in a global or organizational scope.
 */
@Mapper
public interface UserRoleMapper {
    List<com.lingdong.learning.user.application.RoleUserAssignment> findByRoleId(@Param("roleId") Long roleId);

    boolean hasRoleCode(@Param("userId") Long userId, @Param("roleCode") String roleCode);
    List<String> findEnabledRoleCodesByUserId(@Param("userId") Long userId);
    boolean hasPermissionViaRole(@Param("userId") Long userId, @Param("permissionId") Long permissionId);

    boolean hasAnyEnabledRole(@Param("userId") Long userId);
    Long lockRelation(@Param("userId") Long userId, @Param("roleId") Long roleId, @Param("organizationScopeKey") String organizationScopeKey);

    boolean exists(
            @Param("userId") Long userId,
            @Param("roleId") Long roleId,
            @Param("organizationScopeKey") String organizationScopeKey
    );

    int insert(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("roleId") Long roleId,
            @Param("organizationId") Long organizationId,
            @Param("organizationScopeKey") String organizationScopeKey
    );
}
