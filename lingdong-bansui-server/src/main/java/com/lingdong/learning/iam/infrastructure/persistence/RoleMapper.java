package com.lingdong.learning.iam.infrastructure.persistence;

import com.lingdong.learning.iam.domain.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Persistence boundary for the role aggregate. SQL stays in the XML mapper.
 */
@Mapper
public interface RoleMapper {
    Role findById(@Param("id") Long id);

    boolean existsByCode(@Param("code") String code);

    int insert(@Param("role") Role role);

    Role findByCode(@Param("code") String code);

    List<Role> findAll();
    Role lockById(@Param("id") Long id);
    int updateDetails(@Param("id") Long id, @Param("name") String name, @Param("description") String description);
    int updateStatus(@Param("id") Long id, @Param("status") com.lingdong.learning.iam.domain.RoleStatus status);
    boolean hasUserAssignments(@Param("id") Long id);
    int revokeAssociatedSessions(@Param("id") Long id);
    int deletePermissions(@Param("id") Long id);
    int deleteDataScopes(@Param("id") Long id);
    int deleteById(@Param("id") Long id);
}
