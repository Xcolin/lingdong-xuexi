package com.lingdong.learning.permission.infrastructure.persistence;

import com.lingdong.learning.permission.domain.PermissionEffect;
import com.lingdong.learning.permission.application.PermissionAssignment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RolePermissionMapper {
    Long lockRole(@Param("roleId") Long roleId);
    List<PermissionAssignment> lockByRoleId(@Param("roleId") Long roleId);
    boolean exists(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    java.util.List<com.lingdong.learning.permission.application.PermissionAssignment> findInheritedByUserId(@Param("userId") Long userId);

    java.util.List<com.lingdong.learning.permission.application.PermissionAssignment> lockInheritedByUserId(@Param("userId") Long userId);

    PermissionEffect findEffect(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    boolean hasEffectForUser(
            @Param("userId") Long userId,
            @Param("permissionId") Long permissionId,
            @Param("effect") PermissionEffect effect
    );

    List<PermissionAssignment> findByRoleId(@Param("roleId") Long roleId);

    int insert(
            @Param("id") Long id,
            @Param("roleId") Long roleId,
            @Param("permissionId") Long permissionId,
            @Param("effect") PermissionEffect effect
    );

    int updateEffect(
            @Param("roleId") Long roleId,
            @Param("permissionId") Long permissionId,
            @Param("effect") PermissionEffect effect
    );

    int delete(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);
}
