package com.lingdong.learning.permission.infrastructure.persistence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper public interface RolePermissionMapper { boolean exists(@Param("roleId") Long roleId,@Param("permissionId") Long permissionId); int insert(@Param("id") Long id,@Param("roleId") Long roleId,@Param("permissionId") Long permissionId); }
