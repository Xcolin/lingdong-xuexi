package com.lingdong.learning.user.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * Persistence boundary for explicit user-to-organization membership.
 */
@Mapper
public interface UserOrganizationMapper {
    record RoleIdentity(@com.fasterxml.jackson.annotation.JsonIgnore Long userId, String roleCode, String roleName, String organizationId, String organizationName) { }
    List<RoleIdentity> findRoleIdentities(@Param("userIds") List<Long> userIds, @Param("organizationIds") List<Long> organizationIds);
    boolean isEnabledSystemAdmin(@Param("userId") Long userId);
    List<Long> findDirectoryOrganizationIds(@Param("userId") Long userId);
    List<OrganizationName> findScopedNamesByUserIds(@Param("userIds") List<Long> userIds, @Param("organizationIds") List<Long> organizationIds);
    record OrganizationName(Long userId, String name) { }
    List<OrganizationName> findNamesByUserIds(@Param("userIds") List<Long> userIds);
    Long lockRelation(@Param("userId") Long userId, @Param("organizationId") Long organizationId);

    boolean exists(@Param("userId") Long userId, @Param("organizationId") Long organizationId);

    int insert(@Param("id") Long id, @Param("userId") Long userId, @Param("organizationId") Long organizationId);
    int delete(@Param("userId") Long userId, @Param("organizationId") Long organizationId);
    List<Long> findOrganizationIds(@Param("userId") Long userId);
}
