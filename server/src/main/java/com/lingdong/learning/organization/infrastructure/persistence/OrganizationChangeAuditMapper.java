package com.lingdong.learning.organization.infrastructure.persistence;

import com.lingdong.learning.organization.domain.OrganizationChangeAudit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 组织节点生命周期不可变审计持久化边界。 */
@Mapper
public interface OrganizationChangeAuditMapper {
    int insert(@Param("audit") OrganizationChangeAudit audit);
}
