package com.lingdong.learning.permission.infrastructure.persistence;
import com.lingdong.learning.permission.domain.PermissionEffect;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper public interface UserPermissionMapper { PermissionEffect findEffect(@Param("userId") Long userId,@Param("permissionId") Long permissionId); int insert(@Param("id") Long id,@Param("userId") Long userId,@Param("permissionId") Long permissionId,@Param("effect") PermissionEffect effect); int update(@Param("userId") Long userId,@Param("permissionId") Long permissionId,@Param("effect") PermissionEffect effect); }
