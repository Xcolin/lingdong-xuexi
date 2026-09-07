package com.lingdong.learning.teacher.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.organization.application.OrganizationOperationalStatusService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** 集中执行教师管理的客户端、角色、开关和组织范围校验。 */
@Service
public class TeacherManagementAccessService {
    public static final String FEATURE_CODE = "TEACHER_MANAGEMENT";
    private static final String ORGANIZATION_ADMIN_ROLE = "ORG_ADMIN";

    private final FeatureAccessService featureAccessService;
    private final OrganizationDataScopeService organizationDataScopeService;
    private final OrganizationMapper organizationMapper;

    public TeacherManagementAccessService(
            FeatureAccessService featureAccessService,
            OrganizationDataScopeService organizationDataScopeService,
            OrganizationMapper organizationMapper
    ) {
        this.featureAccessService = featureAccessService;
        this.organizationDataScopeService = organizationDataScopeService;
        this.organizationMapper = organizationMapper;
    }

    public void requireManager(AuthenticatedUser currentUser) {
        Objects.requireNonNull(currentUser, "当前登录用户不能为空");
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        if (currentUser.clientType() != AuthClientType.WEB
                && currentUser.clientType() != AuthClientType.MINIAPP) {
            throw new SystemOperationAccessDeniedException("当前客户端不支持教师管理");
        }
        if (!currentUser.roleCodes().contains(ORGANIZATION_ADMIN_ROLE)) {
            throw new SystemOperationAccessDeniedException("仅机构管理员可维护教师账号");
        }
    }

    public Organization requireManageableSchool(AuthenticatedUser currentUser, Long schoolId) {
        requireManager(currentUser);
        Organization school = schoolId == null ? null : organizationMapper.findById(schoolId);
        if (school == null
                || !"SCHOOL".equals(school.typeCode())
                || !organizationDataScopeService.canAccess(currentUser.userId(), school.id())) {
            throw notFound();
        }
        if (!OrganizationOperationalStatusService.isOperational(school)) {
            throw new IllegalStateException("学校已停用，不能维护教师账号");
        }
        return school;
    }

    public Organization requireManageableClass(
            AuthenticatedUser currentUser,
            Organization school,
            Long classOrganizationId
    ) {
        Organization classOrganization = classOrganizationId == null
                ? null : organizationMapper.findById(classOrganizationId);
        if (classOrganization == null
                || !"CLASS".equals(classOrganization.typeCode())
                || !classOrganization.path().startsWith(school.path())
                || !organizationDataScopeService.canAccess(currentUser.userId(), classOrganization.id())) {
            throw notFound();
        }
        if (!OrganizationOperationalStatusService.isOperational(classOrganization)) {
            throw new IllegalStateException("班级已停用，不能绑定教师");
        }
        return classOrganization;
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("教师、学校或班级不存在或不可访问");
    }
}
