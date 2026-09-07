package com.lingdong.learning.permission.infrastructure.persistence;

import com.lingdong.learning.permission.domain.PermissionEffect;
import com.lingdong.learning.permission.application.PermissionAssignment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RolePermissionMapper {
    boolean exists(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

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
