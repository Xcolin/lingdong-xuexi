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
    @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.user.infrastructure.persistence.UserOrganizationMapper memberships;
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
        var rows=relationshipMapper.findActiveSummariesByOrganizationAdministratorScope(dataScopeService.resolve(currentUser.userId()));
        java.util.Map<String,java.util.List<StudentOrganizationSummaryRow>> grouped=new java.util.LinkedHashMap<>();
        rows.forEach(row->grouped.computeIfAbsent(row.studentId()+":"+row.enrollmentOrganizationId(),key->new java.util.ArrayList<>()).add(row));
        return grouped.values().stream().map(group -> {
            var first=group.get(0);var institution=organizationMapper.findById(first.enrollmentOrganizationId());
            var classIds=group.stream().map(StudentOrganizationSummaryRow::currentClassOrganizationId).filter(Objects::nonNull).distinct().toList();
            var names=group.stream().filter(row->row.currentClassOrganizationId()!=null).map(StudentOrganizationSummaryRow::currentClassOrganizationName).filter(Objects::nonNull).toList();
            return new StudentOrganizationRelationshipSummary(first.studentId(),first.studentName(),first.gradeCode(),first.enrollmentOrganizationId(),first.enrollmentOrganizationName(),classIds.size()==1?classIds.get(0):null,names.size()==1?names.get(0):null,institution==null?null:institution.typeCode(),classIds,names,first.studentStatus(),first.studentNumber());
        }).toList();    }

    @Transactional(readOnly = true)
    public List<StudentOrganizationClassOption> listClassOptions(AuthenticatedUser currentUser) {return listClassOptions(currentUser,null);}
    @Transactional(readOnly=true)
    public List<StudentOrganizationClassOption> listClassOptions(AuthenticatedUser currentUser,Long schoolId) {
        requireUser(currentUser);
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        var all=organizationMapper.findAll(); var byId=new java.util.HashMap<Long,Organization>(); all.forEach(o -> byId.put(o.id(),o));
        var order=com.lingdong.learning.organization.application.OrganizationTreeOrder.comparator(all);
        var paths=new com.lingdong.learning.organization.application.OrganizationNamePathService(organizationMapper).namePathsById();
        return relationshipMapper.findEnabledClassOptionsByOrganizationAdministratorScope(dataScopeService.resolve(currentUser.userId())).stream()
          .sorted((a,b) -> byId.get(a.id())==null || byId.get(b.id())==null ? a.id().compareTo(b.id()) : order.compare(byId.get(a.id()),byId.get(b.id())))
          .map(row -> {Organization school=com.lingdong.learning.organization.application.OrganizationHierarchy.schoolOf(byId.get(row.id()),organizationMapper);return new StudentOrganizationClassOption(row.id(),paths.getOrDefault(row.id(),row.name()),school==null?null:school.id());})
          .filter(option->schoolId==null||schoolId.equals(option.schoolOrganizationId())).toList();
    }

    @Transactional(readOnly = true)
    public StudentOrganizationRelationshipView find(AuthenticatedUser currentUser, Long studentId) { return find(currentUser,studentId,null); }
    public StudentOrganizationRelationshipView find(AuthenticatedUser currentUser, Long studentId, Long institutionId) {
        requireUser(currentUser);
        Long normalizedStudentId = requiredId(studentId, "学生标识");
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        Student student = studentMapper.findById(normalizedStudentId);
        if (student == null || student.status() != StudentStatus.ENABLED) {
            throw notFound();
        }
        Long enrollmentId = accessibleEnrollment(currentUser, normalizedStudentId, institutionId);
        return view(currentUser,normalizedStudentId, enrollmentId, "ACTIVE");
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
        if (!relationshipMapper.existsActiveEnrollmentInScope(normalizedStudentId, enrollmentId, dataScopeService.resolve(currentUser.userId()))) {
            throw notFound();
        }

        List<Long> activeClassIds = relationshipMapper.findActiveClassOrganizationIdsInEnrollment(normalizedStudentId, enrollmentId);
        if(command.requireUnassigned() && !activeClassIds.isEmpty()
                && !(activeClassIds.size()==1 && activeClassIds.contains(classOrganizationId)))
            throw new IllegalStateException("成长小主人已分班，请刷新后使用转班功能");
        if (activeClassIds.contains(classOrganizationId)) {
            return view(currentUser,normalizedStudentId, enrollmentId, "ACTIVE");
        }

        Long previousClassId = activeClassIds.isEmpty() ? null : activeClassIds.get(0);
        Organization institution = organizationMapper.findById(enrollmentId);
        if (institution != null && "SCHOOL".equals(institution.typeCode())) relationshipMapper.deactivateActiveClassesInEnrollment(normalizedStudentId, enrollmentId);
        if (relationshipMapper.activateExistingClass(normalizedStudentId, classOrganizationId) == 0) {
            relationshipMapper.insertClass(idGenerator.nextId(), normalizedStudentId, classOrganizationId);
        }
        // 历史学生无关联账号（student_user_id 为空），不维护小程序端会员组织关系
        if (memberships != null && student.studentUserId() != null) {
            if (institution != null && "SCHOOL".equals(institution.typeCode())) activeClassIds.forEach(id -> memberships.delete(student.studentUserId(),id));
            if (!memberships.exists(student.studentUserId(),classOrganizationId)) memberships.insert(idGenerator.nextId(),student.studentUserId(),classOrganizationId);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        StudentOrganizationChangeType changeType = previousClassId == null
                ? StudentOrganizationChangeType.CLASS_ASSIGN
                : StudentOrganizationChangeType.CLASS_TRANSFER;
        changeMapper.insert(idGenerator.nextId(), normalizedStudentId, changeType.name(),
                previousClassId, classOrganizationId, reason, currentUser.userId(), now);
        return view(currentUser,normalizedStudentId, enrollmentId, "ACTIVE");
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
                || !relationshipMapper.existsActiveEnrollmentInScope(normalizedStudentId, enrollmentId, dataScopeService.resolve(currentUser.userId()))
                || !relationshipMapper.findActiveEnrollmentOrganizationIds(normalizedStudentId)
                        .contains(enrollmentId)) {
            throw notFound();
        }

        List<Long> exitingClasses=relationshipMapper.findActiveClassOrganizationIdsInEnrollment(normalizedStudentId,enrollmentId);
        relationshipMapper.deactivateActiveClassesInEnrollment(normalizedStudentId, enrollmentId);
        relationshipMapper.removeInstitutionMemberships(student.studentUserId(),enrollmentId);
        if (relationshipMapper.deactivateActiveEnrollment(normalizedStudentId, enrollmentId) != 1) {
            throw notFound();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        changeMapper.insert(idGenerator.nextId(), normalizedStudentId,
                StudentOrganizationChangeType.ENROLLMENT_DEACTIVATE.name(),
                enrollmentId, null, reason, currentUser.userId(), now);
        return view(currentUser,normalizedStudentId, enrollmentId, "INACTIVE");
    }

    @Transactional
    public StudentOrganizationRelationshipView deactivateClass(AuthenticatedUser actor,Long studentId,Long classId,String reason) {
        requireUser(actor);requiredId(classId,"班级标识");requiredReason(reason);featureAccessService.requireEnabled(FEATURE_CODE,null);
        Student student=studentMapper.findByIdForUpdate(studentId);
        Organization classroom=organizationMapper.findById(classId);
        if(student==null||student.status()!=StudentStatus.ENABLED||classroom==null||!"CLASS".equals(classroom.typeCode())
            ||!dataScopeService.canAccess(actor.userId(),classId)||!relationshipMapper.existsActiveClass(studentId,classId))throw notFound();
        Organization institution=com.lingdong.learning.organization.application.OrganizationHierarchy.schoolOf(classroom,organizationMapper);
        if(institution==null)throw notFound();
        relationshipMapper.deactivateClass(studentId,classId);
        if(memberships!=null)memberships.delete(student.studentUserId(),classId);
        changeMapper.insert(idGenerator.nextId(),studentId,"CLASS_DEACTIVATE",classId,null,reason,actor.userId(),LocalDateTime.now(clock));
        return view(actor,studentId,institution.id(),"ACTIVE");
    }
    private StudentOrganizationRelationshipView view(AuthenticatedUser actor,Long studentId, Long enrollmentId, String status) {
        List<Long> classIds=relationshipMapper.findActiveClassOrganizationIdsInEnrollment(studentId,enrollmentId).stream().filter(id->dataScopeService.canAccess(actor.userId(),id)).toList();
        Long currentClassId=classIds.size()==1?classIds.get(0):null;
        Organization institution=organizationMapper.findById(enrollmentId);
        var history=changeMapper.findByStudentId(studentId).stream().filter(change -> {
            Organization from=change.fromOrganizationId()==null?null:organizationMapper.findById(change.fromOrganizationId());
            Organization to=change.toOrganizationId()==null?null:organizationMapper.findById(change.toOrganizationId());
            return institution!=null&&((from!=null&&from.path().startsWith(institution.path()))||(to!=null&&to.path().startsWith(institution.path())))&&(from==null||dataScopeService.canAccess(actor.userId(),from.id()))&&(to==null||dataScopeService.canAccess(actor.userId(),to.id()));
        }).toList();
        return new StudentOrganizationRelationshipView(studentId,enrollmentId,currentClassId,status,history,classIds);
    }
    private StudentOrganizationRelationshipSummary summary(StudentOrganizationSummaryRow row) {
        return new StudentOrganizationRelationshipSummary(
                row.studentId(), row.studentName(), row.gradeCode(),
                row.enrollmentOrganizationId(), row.enrollmentOrganizationName(),
                row.currentClassOrganizationId(), row.currentClassOrganizationName());
    }

    private Long accessibleEnrollment(AuthenticatedUser currentUser, Long studentId,Long institutionId) {
        var accessible=relationshipMapper.findActiveEnrollmentOrganizationIds(studentId).stream()
            .filter(id -> institutionId==null||institutionId.equals(id))
            .filter(id -> relationshipMapper.existsActiveEnrollmentInScope(studentId,id,dataScopeService.resolve(currentUser.userId()))).toList();
        if(accessible.isEmpty())throw notFound();
        if(accessible.size()>1)throw new IllegalArgumentException("学生已关联多个教育机构，请指定institutionOrganizationId");
        return accessible.get(0);
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





