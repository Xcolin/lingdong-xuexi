package com.lingdong.learning.organization.infrastructure.persistence;

import com.lingdong.learning.organization.domain.Organization;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Persistence boundary for organization tree nodes.
 */
@Mapper
public interface OrganizationMapper {
    Organization findById(@Param("id") Long id);

    Organization findByCode(@Param("code") String code);

    boolean existsByCode(@Param("code") String code);

    boolean existsByParentScopeAndName(
            @Param("parentScopeKey") String parentScopeKey,
            @Param("name") String name
    );

    int insert(@Param("organization") Organization organization);
}
