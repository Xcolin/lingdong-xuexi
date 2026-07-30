package com.lingdong.learning.permission.infrastructure.persistence;
import com.lingdong.learning.permission.domain.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper public interface PermissionMapper { Permission findById(@Param("id") Long id); Permission findByCode(@Param("code") String code); boolean existsByCode(@Param("code") String code); int insert(@Param("permission") Permission permission); }
