package com.lingdong.learning.datascope.infrastructure.persistence;

import com.lingdong.learning.organization.infrastructure.persistence.OrganizationAdminSummaryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 维护组织管理员与其直接管理组织之间的持久化关系。 */
@Mapper
public interface OrganizationAdminMapper {
    List<Long> findOrganizationIds(@Param("userId") Long userId);

    Long lockRelation(@Param("userId") Long userId, @Param("organizationId") Long organizationId);

    boolean exists(@Param("userId") Long userId, @Param("organizationId") Long organizationId);

    boolean existsEnabledManagedOrganization(@Param("userId") Long userId);

    List<OrganizationAdminSummaryRow> findEnabledManagedOrganizationSummaries(@Param("userId") Long userId);

    int insert(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("organizationId") Long organizationId
    );
}
