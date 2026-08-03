package com.lingdong.learning.student.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.domain.OrganizationStatus;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.domain.StudentStatus;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** 在组织数据范围内维护学生唯一的活动班级关系。 */
@Service
public class StudentClassAssignmentService {
    private static final String FEATURE_CODE = "LEARNING_TASK_MANAGEMENT";
    private static final String CLASS_ORGANIZATION_TYPE = "CLASS";

    private final StudentMapper studentMapper;
    private final StudentOrganizationMapper studentOrganizationMapper;
    private final OrganizationMapper organizationMapper;
    private final OrganizationDataScopeService organizationDataScopeService;
    private final FeatureAccessService featureAccessService;
    private final IdGenerator idGenerator;

    public StudentClassAssignmentService(
            StudentMapper studentMapper,
            StudentOrganizationMapper studentOrganizationMapper,
            OrganizationMapper organizationMapper,
            OrganizationDataScopeService organizationDataScopeService,
            FeatureAccessService featureAccessService,
            IdGenerator idGenerator
    ) {
        this.studentMapper = studentMapper;
        this.studentOrganizationMapper = studentOrganizationMapper;
        this.organizationMapper = organizationMapper;
        this.organizationDataScopeService = organizationDataScopeService;
        this.featureAccessService = featureAccessService;
        this.idGenerator = idGenerator;
    }

    /**
     * 锁定学生主记录后切换班级，避免尚未分班时的并发请求生成多条活动关系。
     */
    @Transactional
    public StudentClassAssignment assign(
            AuthenticatedUser currentUser, Long studentId, AssignStudentClassCommand command
    ) {
        Objects.requireNonNull(currentUser, "当前登录用户不能为空");
        Objects.requireNonNull(command, "学生班级配置请求不能为空");
        Long classOrganizationId = requiredId(command.classOrganizationId(), "班级组织标识");
        Long normalizedStudentId = requiredId(studentId, "学生标识");

        featureAccessService.requireEnabled(FEATURE_CODE, null);
        Student student = studentMapper.findByIdForUpdate(normalizedStudentId);
        if (student == null || student.status() != StudentStatus.ENABLED) {
            throw notFound();
        }

        Organization classOrganization = organizationMapper.findById(classOrganizationId);
        if (classOrganization == null
                || !organizationDataScopeService.canAccess(currentUser.userId(), classOrganizationId)) {
            throw notFound();
        }
        if (!CLASS_ORGANIZATION_TYPE.equals(classOrganization.typeCode())) {
            throw new IllegalArgumentException("目标组织不是班级");
        }
        if (classOrganization.status() != OrganizationStatus.ENABLED) {
            throw new IllegalStateException("目标班级已停用");
        }
        if (!studentOrganizationMapper.existsActiveEnrollmentInClassAncestors(
                normalizedStudentId, classOrganizationId)) {
            throw notFound();
        }

        List<Long> activeClassIds = studentOrganizationMapper.findActiveClassOrganizationIds(normalizedStudentId);
        if (activeClassIds.size() == 1 && classOrganizationId.equals(activeClassIds.get(0))) {
            return activeAssignment(normalizedStudentId, classOrganizationId);
        }

        studentOrganizationMapper.deactivateActiveClasses(normalizedStudentId);
        int activated = studentOrganizationMapper.activateExistingClass(
                normalizedStudentId, classOrganizationId);
        if (activated == 0) {
            studentOrganizationMapper.insertClass(
                    idGenerator.nextId(), normalizedStudentId, classOrganizationId);
        }
        return activeAssignment(normalizedStudentId, classOrganizationId);
    }

    private StudentClassAssignment activeAssignment(Long studentId, Long classOrganizationId) {
        return new StudentClassAssignment(studentId, classOrganizationId, "ACTIVE");
    }

    private Long requiredId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("学生或班级不存在或不可访问");
    }
}
