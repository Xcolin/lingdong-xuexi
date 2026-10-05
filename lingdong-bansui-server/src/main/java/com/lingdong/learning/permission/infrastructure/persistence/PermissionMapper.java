package com.lingdong.learning.permission.infrastructure.persistence;

import com.lingdong.learning.permission.domain.Permission;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.permission.domain.PermissionStatus;
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

    /** 菜单树同步：按主键更新名称与状态。 */
    int updateNameAndStatus(@Param("id") Long id, @Param("name") String name, @Param("status") PermissionStatus status);

    /** 菜单编码变更联动：按主键重命名权限编码，保留既有授权关系。 */
    int updateCode(@Param("id") Long id, @Param("code") String code);

    /** 菜单同步完整资源元数据；保留共享权限客户端及既有授权关系。 */
    @org.apache.ibatis.annotations.Update("UPDATE sys_permission SET permission_name=#{name}, status=#{status}, resource_type=#{resourceType}, parent_id=#{parentId} WHERE id=#{id}")
    int updateMenuMetadata(@Param("id") Long id, @Param("name") String name,
            @Param("status") PermissionStatus status,
            @Param("resourceType") com.lingdong.learning.permission.domain.PermissionResourceType resourceType,
            @Param("parentId") Long parentId);

    List<Permission> findAll();

    List<String> findAllowedCodes(
            @Param("userId") Long userId,
            @Param("client") PermissionClient client
    );
}
