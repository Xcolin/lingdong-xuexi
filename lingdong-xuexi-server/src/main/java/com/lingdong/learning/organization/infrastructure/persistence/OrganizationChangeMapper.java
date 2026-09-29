package com.lingdong.learning.organization.infrastructure.persistence;

import com.lingdong.learning.organization.domain.OrganizationChange;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 高风险组织变更快照持久化边界。 */
@Mapper
public interface OrganizationChangeMapper {
    int insert(@Param("change") OrganizationChange change);

    OrganizationChange findByTaskId(@Param("taskId") Long taskId);

    String findRequestedStatus(@Param("taskId") Long taskId);

    List<OrganizationChange> findAll();

    boolean existsActiveByOrganizationId(@Param("organizationId") Long organizationId);

    int markApplied(@Param("id") Long id);

    int markFailed(@Param("id") Long id, @Param("failureReason") String failureReason);
}
