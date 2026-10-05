package com.lingdong.learning.student.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.application.OrganizationOperationalStatusService;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.domain.StudentOrganizationChangeType;
import com.lingdong.learning.student.domain.StudentStatus;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationChangeMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationSummaryRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** 在组织数据范围内维护学生机构关系生命周期。 */
@Service
public class StudentOrganizationLifecycleService {
    private static final String FEATURE_CODE = "STUDENT_ORGANIZATION_RELATIONSHIP";
    private static final String CLASS_TYPE = "CLASS";

    private final StudentMapper studentMapper;
    private final StudentOrganizationMapper relationshipMapper;
    private final StudentOrganizationChangeMapper changeMapper;
    private final OrganizationMapper organizationMapper;
    private final OrganizationDataScopeService dataScopeService;
    private final FeatureAccessService featureAccessService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public StudentOrganizationLifecycleService(
            StudentMapper studentMapper,
            StudentOrganizationMapper relationshipMapper,
            StudentOrganizationChangeMapper changeMapper,
            OrganizationMapper organizationMapper,
            OrganizationDataScopeService dataScopeService,
            FeatureAccessService featureAccessService,
            IdGenerator idGenerator,
            Clock clock
    ) {
        this.studentMapper = studentMapper;
        this.relationshipMapper = relationshipMapper;
        this.changeMapper = changeMapper;
        this.organizationMapper = organizationMapper;
        this.dataScopeService = dataScopeService;
        this.featureAccessService = featureAccessService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<StudentOrganizationRelationshipSummary> list(AuthenticatedUser currentUser) {
        requireUser(currentUser);
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        return relationshipMapper.findActiveSummariesByOrganizationAdministratorScope(dataScopeService.resolve(currentUser.userId()))
                .stream()
                .map(this::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StudentOrganizationClassOption> listClassOptions(AuthenticatedUser currentUser) {
        requireUser(currentUser);
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        return relationshipMapper.findEnabledClassOptionsByOrganizationAdministratorScope(
                        dataScopeService.resolve(currentUser.userId()))
                .stream()
                .map(row -> new StudentOrganizationClassOption(row.id(), row.name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentOrganizationRelationshipView find(AuthenticatedUser currentUser, Long studentId) {
        requireUser(currentUser);
        Long normalizedStudentId = requiredId(studentId, "学生标识");
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        Student student = studentMapper.findById(normalizedStudentId);
        if (student == null || student.status() != StudentStatus.ENABLED) {
            throw notFound();
        }
        Long enrollmentId = accessibleEnrollment(currentUser, normalizedStudentId);
        return view(normalizedStudentId, enrollmentId, "ACTIVE");
    }

    @Transactional
    public StudentOrganizationRelationshipView transferClass(
            AuthenticatedUser currentUser,
            Long studentId,
            TransferStudentClassCommand command
    ) {
        requireUser(currentUser);
        Objects.requireNonNull(command, "学生转班请求不能为空");
        Long normalizedStudentId = requiredId(studentId, "学生标识");
        Long classOrganizationId = requiredId(command.classOrganizationId(), "班级组织标识");
        String reason = requiredReason(command.reason());
        featureAccessService.requireEnabled(FEATURE_CODE, null);

        Student student = studentMapper.findByIdForUpdate(normalizedStudentId);
        if (student == null || student.status() != StudentStatus.ENABLED) {
            throw notFound();
        }
        Organization targetClass = organizationMapper.findById(classOrganizationId);
        if (targetClass == null || !dataScopeService.canAccess(currentUser.userId(), classOrganizationId)) {
            throw notFound();
        }
        if (!CLASS_TYPE.equals(targetClass.typeCode())) {
            throw new IllegalArgumentException("目标组织不是班级");
        }
        if (!OrganizationOperationalStatusService.isOperational(targetClass)) {
            throw new IllegalStateException("目标班级已停用");
        }

        List<Long> enrollmentIds = relationshipMapper.findActiveEnrollmentOrganizationIdsForClass(
                normalizedStudentId, classOrganizationId);
        if (enrollmentIds.isEmpty()) {
            throw new IllegalArgumentException("跨机构转学不能直接迁移原学生账号");
        }
        Long enrollmentId = enrollmentIds.get(0);
        if (!dataScopeService.canAccess(currentUser.userId(), enrollmentId)) {
            throw notFound();
        }

        List<Long> activeClassIds = relationshipMapper.findActiveClassOrganizationIds(normalizedStudentId);
        if (activeClassIds.contains(classOrganizationId)) {
            return view(normalizedStudentId, enrollmentId, "ACTIVE");
        }

        Long previousClassId = activeClassIds.isEmpty() ? null : activeClassIds.get(0);
        relationshipMapper.deactivateActiveClasses(normalizedStudentId);
        if (relationshipMapper.activateExistingClass(normalizedStudentId, classOrganizationId) == 0) {
            relationshipMapper.insertClass(idGenerator.nextId(), normalizedStudentId, classOrganizationId);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        StudentOrganizationChangeType changeType = previousClassId == null
                ? StudentOrganizationChangeType.CLASS_ASSIGN
                : StudentOrganizationChangeType.CLASS_TRANSFER;
        changeMapper.insert(idGenerator.nextId(), normalizedStudentId, changeType.name(),
                previousClassId, classOrganizationId, reason, currentUser.userId(), now);
        return view(normalizedStudentId, enrollmentId, "ACTIVE");
    }

    @Transactional
    public StudentOrganizationRelationshipView deactivate(
            AuthenticatedUser currentUser,
            Long studentId,
            DeactivateStudentOrganizationCommand command
    ) {
        requireUser(currentUser);
        Objects.requireNonNull(command, "学生机构关系停用请求不能为空");
        Long normalizedStudentId = requiredId(studentId, "学生标识");
        Long enrollmentId = requiredId(command.organizationId(), "入学组织标识");
        String reason = requiredReason(command.reason());
        featureAccessService.requireEnabled(FEATURE_CODE, null);

        Student student = studentMapper.findByIdForUpdate(normalizedStudentId);
        if (student == null || student.status() != StudentStatus.ENABLED
                || !dataScopeService.canAccess(currentUser.userId(), enrollmentId)
                || !relationshipMapper.findActiveEnrollmentOrganizationIds(normalizedStudentId)
                        .contains(enrollmentId)) {
            throw notFound();
        }

        relationshipMapper.deactivateActiveClassesInEnrollment(normalizedStudentId, enrollmentId);
        if (relationshipMapper.deactivateActiveEnrollment(normalizedStudentId, enrollmentId) != 1) {
            throw notFound();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        changeMapper.insert(idGenerator.nextId(), normalizedStudentId,
                StudentOrganizationChangeType.ENROLLMENT_DEACTIVATE.name(),
                enrollmentId, null, reason, currentUser.userId(), now);
        return view(normalizedStudentId, enrollmentId, "INACTIVE");
    }

    private StudentOrganizationRelationshipView view(Long studentId, Long enrollmentId, String status) {
        Long currentClassId = relationshipMapper.findActiveClassOrganizationIdsInEnrollment(
                studentId, enrollmentId).stream().findFirst().orElse(null);
        return new StudentOrganizationRelationshipView(studentId, enrollmentId, currentClassId,
                status, changeMapper.findByStudentId(studentId));
    }

    private StudentOrganizationRelationshipSummary summary(StudentOrganizationSummaryRow row) {
        return new StudentOrganizationRelationshipSummary(
                row.studentId(), row.studentName(), row.gradeCode(),
                row.enrollmentOrganizationId(), row.enrollmentOrganizationName(),
                row.currentClassOrganizationId(), row.currentClassOrganizationName());
    }

    private Long accessibleEnrollment(AuthenticatedUser currentUser, Long studentId) {
        return relationshipMapper.findActiveEnrollmentOrganizationIds(studentId).stream()
                .filter(id -> dataScopeService.canAccess(currentUser.userId(), id))
                .findFirst()
                .orElseThrow(this::notFound);
    }

    private void requireUser(AuthenticatedUser currentUser) {
        Objects.requireNonNull(currentUser, "当前登录用户不能为空");
    }

    private Long requiredId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private String requiredReason(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("变更原因不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > 200) {
            throw new IllegalArgumentException("变更原因不能超过200个字符");
        }
        return normalized;
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("学生或机构关系不存在或不可访问");
    }
}
