package com.lingdong.learning.organization.infrastructure.persistence;

import com.lingdong.learning.organization.domain.OrganizationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Persistence boundary for configurable organization classifications.
 */
@Mapper
public interface OrganizationTypeMapper {
    OrganizationType findByCode(@Param("code") String code);

    boolean existsByCode(@Param("code") String code);

    boolean existsByName(@Param("name") String name);

    int insert(@Param("organizationType") OrganizationType organizationType);
}
