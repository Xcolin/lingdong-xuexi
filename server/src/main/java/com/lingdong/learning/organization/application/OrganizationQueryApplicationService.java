package com.lingdong.learning.organization.application;

import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.domain.OrganizationType;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationTypeMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 为组织管理接口提供稳定排序的只读查询边界。 */
@Service
public class OrganizationQueryApplicationService {
    private final OrganizationTypeMapper organizationTypeMapper;
    private final OrganizationMapper organizationMapper;

    public OrganizationQueryApplicationService(
            OrganizationTypeMapper organizationTypeMapper,
            OrganizationMapper organizationMapper
    ) {
        this.organizationTypeMapper = organizationTypeMapper;
        this.organizationMapper = organizationMapper;
    }

    public List<OrganizationType> listOrganizationTypes() {
        return organizationTypeMapper.findAll();
    }

    public List<Organization> listOrganizations() {
        return organizationMapper.findAll();
    }
}
