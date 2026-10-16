package com.lingdong.learning.organization.infrastructure.persistence;

import com.lingdong.learning.organization.domain.OrganizationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Persistence boundary for configurable organization classifications.
 */
@Mapper
public interface OrganizationTypeMapper {
    OrganizationType findByCode(@Param("code") String code);

    boolean existsByCode(@Param("code") String code);

    boolean existsByName(@Param("name") String name);

    int insert(@Param("organizationType") OrganizationType organizationType);

    List<OrganizationType> findAll();
    OrganizationType lockById(@Param("id") Long id);
    OrganizationType findById(@Param("id") Long id);
    boolean existsByNameExceptId(@Param("name") String name, @Param("id") Long id);
    boolean hasOrganizations(@Param("code") String code);
    int updateDetails(@Param("id") Long id, @Param("name") String name, @Param("sortOrder") Integer sortOrder);
    int updateStatus(@Param("id") Long id, @Param("status") com.lingdong.learning.organization.domain.OrganizationStatus status);
    int deleteById(@Param("id") Long id);
}
