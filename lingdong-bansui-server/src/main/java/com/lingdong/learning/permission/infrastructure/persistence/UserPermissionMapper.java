package com.lingdong.learning.permission.infrastructure.persistence;
import com.lingdong.learning.permission.application.PermissionAssignment;
import com.lingdong.learning.permission.domain.PermissionEffect;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserPermissionMapper {
    PermissionEffect findEffect(@Param("userId") Long userId, @Param("permissionId") Long permissionId);

    List<PermissionAssignment> findByUserId(@Param("userId") Long userId);

    List<PermissionAssignment> lockByUserId(@Param("userId") Long userId);

    int insert(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("permissionId") Long permissionId,
            @Param("effect") PermissionEffect effect
    );

    int update(
            @Param("userId") Long userId,
            @Param("permissionId") Long permissionId,
            @Param("effect") PermissionEffect effect
    );

    int delete(@Param("userId") Long userId, @Param("permissionId") Long permissionId);
}
