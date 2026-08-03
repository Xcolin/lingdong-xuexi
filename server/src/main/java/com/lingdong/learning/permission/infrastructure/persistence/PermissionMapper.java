package com.lingdong.learning.permission.infrastructure.persistence;

import com.lingdong.learning.permission.domain.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 权限目录的持久化边界，具体 SQL 统一维护在 MyBatis XML 中。 */
@Mapper
public interface PermissionMapper {
    Permission findById(@Param("id") Long id);

    Permission findByCode(@Param("code") String code);

    boolean existsByCode(@Param("code") String code);

    int insert(@Param("permission") Permission permission);

    List<Permission> findAll();
}
