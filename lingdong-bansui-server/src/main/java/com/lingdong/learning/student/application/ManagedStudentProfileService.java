package com.lingdong.learning.student.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointAccountMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointLifecycleMapper;
import com.lingdong.learning.organization.application.OrganizationOperationalStatusService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.domain.StudentStatus;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Creates the business profile for the same managed STUDENT identity. */
@Service
public class ManagedStudentProfileService {
    @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.user.infrastructure.persistence.UserOrganizationMapper memberships;
    private final UserMapper users;
    private final OrganizationMapper organizations;
    private final StudentMapper students;
    private final StudentOrganizationMapper relationships;
    private final GrowthPointAccountMapper points;
    private final GrowthPointLifecycleMapper lifecycle;
    private final StudentIdentityProvisioningService identities;
    private final PermissionDecisionService permissions;
    private final OrganizationDataScopeService scopes;
    private final FeatureAccessService features;
    private final IdGenerator ids;

    public ManagedStudentProfileService(UserMapper users, OrganizationMapper organizations, StudentMapper students,
            StudentOrganizationMapper relationships, GrowthPointAccountMapper points, GrowthPointLifecycleMapper lifecycle,
            StudentIdentityProvisioningService identities, PermissionDecisionService permissions,
            OrganizationDataScopeService scopes, FeatureAccessService features, IdGenerator ids) {
        this.users = users; this.organizations = organizations; this.students = students;
        this.relationships = relationships; this.points = points; this.lifecycle = lifecycle;
        this.identities = identities; this.permissions = permissions; this.scopes = scopes;
        this.features = features; this.ids = ids;
    }

    public Placement validate(Long operatorId, Long organizationId, Long explicitSchoolId, String gradeCode) {return validatePlacement(operatorId,organizationId,explicitSchoolId,gradeCode,false);}
    public Placement validateInitialPlacement(Long operatorId, Long organizationId, Long explicitSchoolId, String gradeCode) {return validatePlacement(operatorId,organizationId,explicitSchoolId,gradeCode,true);}
    private Placement validatePlacement(Long operatorId, Long organizationId, Long explicitSchoolId, String gradeCode,boolean initial) {
        requirePermission(operatorId, "IAM_USER_CREATE");
        if(!initial)features.requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP", null);
        Organization selected = requireOrganization(organizationId);
        requireScope(operatorId, selected.id());
        Organization school = null;
        Organization grade = null;
        boolean educational = Set.of("SCHOOL", "TRAINING_INSTITUTION", "CAMPUS", "GRADE", "CLASS").contains(selected.typeCode());
        if (educational) {
            Set<Long> visited = new HashSet<>();
            Organization ancestor = selected;
            while (ancestor != null) {
                if (!visited.add(ancestor.id())) throw new IllegalStateException("组织层级存在循环");
                if ("GRADE".equals(ancestor.typeCode()) && grade == null) grade = ancestor;
                if (com.lingdong.learning.organization.application.OrganizationHierarchy.isInstitutionRoot(ancestor)) { school = ancestor; break; }
                ancestor = ancestor.parentId() == null ? null : requireOrganization(ancestor.parentId());
            }
            if (school == null) throw new IllegalArgumentException("所选教育组织没有所属学校");
            if (explicitSchoolId != null && !school.id().equals(explicitSchoolId))
                throw new IllegalArgumentException("指定学校与所选教育组织所属学校不一致");
        } else if (explicitSchoolId != null) {
            school = requireOrganization(explicitSchoolId);
            if (!com.lingdong.learning.organization.application.OrganizationHierarchy.isInstitutionRoot(school)) throw new IllegalArgumentException("学生学校必须是学校组织");
            requireScope(operatorId, school.id());
        }
        if ("CLASS".equals(selected.typeCode())) {
            if(!initial)features.requireEnabled("CLASS_MANAGEMENT", null);
        }
        String resolvedGrade = gradeCode == null || gradeCode.isBlank() ? (grade == null ? null : grade.name()) : gradeCode.trim();
        if ((school != null && "SCHOOL".equals(school.typeCode()) && (resolvedGrade == null || resolvedGrade.isBlank())) || (resolvedGrade != null && resolvedGrade.length() > 64))
            throw new IllegalArgumentException("学生年级信息必填且不能超过64个字符");
        return new Placement(selected.id(), school == null ? null : school.id(), "CLASS".equals(selected.typeCode()) ? selected.id() : null, resolvedGrade);
    }

    @Transactional
    public Student complete(Long operatorId, Long userId, Long organizationId, Long schoolId, String gradeCode) {
        return completeWithCredential(operatorId, userId, organizationId, schoolId, gradeCode).student();
    }

    @Transactional
    public CompletedProfile completeWithCredential(Long operatorId, Long userId, Long organizationId, Long schoolId, String gradeCode) {return completeProfile(operatorId,userId,organizationId,schoolId,gradeCode,false);}
    @Transactional
    public CompletedProfile completeCreatedIdentity(Long operatorId, Long userId, Long organizationId, Long schoolId, String gradeCode) {return completeProfile(operatorId,userId,organizationId,schoolId,gradeCode,true);}
    private CompletedProfile completeProfile(Long operatorId, Long userId, Long organizationId, Long schoolId, String gradeCode,boolean initial) {
        Placement placement = validatePlacement(operatorId, organizationId, schoolId, gradeCode,initial);
        User user = users.findByIdForUpdate(userId);
        if (user == null) throw new ResourceNotFoundException("用户不存在");
        if (user.type() != UserType.STUDENT) throw new IllegalArgumentException("用户不是学生");
        if (user.status() != com.lingdong.learning.user.domain.UserStatus.ENABLED)
            throw new IllegalStateException("用户已停用、锁定或注销，不能补齐学生档案");
        if (!user.username().matches("[A-Za-z0-9_.@-]{1,64}")) throw new IllegalArgumentException("学生账号格式不支持登录");
        Student student = students.findByStudentUserId(user.id());
        if (student != null) {
            student = students.findByIdForUpdate(student.id());
            if (student.status() != StudentStatus.ENABLED) throw new IllegalStateException("学生档案已停用，不能补齐");
            relationships.findActiveOrganizationIdsForUpdate(student.id());
            List<Long> enrollments = relationships.findActiveEnrollmentOrganizationIds(student.id());
            if (placement.schoolId()!=null && !enrollments.isEmpty() && !enrollments.contains(placement.schoolId())) throw new IllegalStateException("不能覆盖或直接新增其他机构关系，请通过邀请码提交入学申请");

            if (placement.schoolId() != null && !enrollments.contains(placement.schoolId()) && relationships.existsEnrollment(student.id(), placement.schoolId()))
                throw new IllegalStateException("学生已有停用入学关系，请使用专门恢复流程");
            if (placement.schoolId() != null && enrollments.contains(placement.schoolId()) && !relationships.existsActiveEnrollmentInScope(student.id(), placement.schoolId(), scopes.resolve(operatorId)))
                throw new SystemOperationAccessDeniedException("学生当前有效归属不在数据范围内，不能补齐");
            List<Long> classes = placement.schoolId() == null ? List.of() : relationships.findActiveClassOrganizationIdsInEnrollment(student.id(), placement.schoolId());
            if (placement.classId() != null && "SCHOOL".equals(requireOrganization(placement.schoolId()).typeCode()) && classes.stream().anyMatch(id -> !id.equals(placement.classId())))
                throw new IllegalStateException("学生已有其他有效班级关系，不能覆盖");
        } else {
            student = Student.create(ids.nextId(), user.displayName(), placement.gradeCode(), user.id());
            if (students.insert(student) != 1) throw new IllegalStateException("学生档案创建失败");
        }
        if (points.findByStudentIdForUpdate(student.id()) == null && points.insertInitial(student.id()) != 1)
            throw new IllegalStateException("学生积分账户创建失败");
        if (lifecycle.findDormancyStateForUpdate(student.id()) == null && lifecycle.insertDormancyState(student.id()) != 1)
            throw new IllegalStateException("学生积分生命周期状态创建失败");
        if (placement.schoolId() != null && !relationships.findActiveEnrollmentOrganizationIds(student.id()).contains(placement.schoolId())
                && relationships.insertEnrollment(ids.nextId(), student.id(), placement.schoolId()) != 1)
            throw new IllegalStateException("学生入学关系创建失败");
        if (placement.classId() != null && !relationships.existsActiveClass(student.id(), placement.classId())) {
            if (relationships.activateExistingClass(student.id(), placement.classId()) == 0
                    && relationships.insertClass(ids.nextId(), student.id(), placement.classId()) != 1)
                throw new IllegalStateException("学生班级关系创建失败");
        }
        if (memberships != null && placement.schoolId() != null && !memberships.exists(user.id(),placement.schoolId())) memberships.insert(ids.nextId(),user.id(),placement.schoolId());
        String initialLoginCode = identities.ensureExistingUserIdentity(user);
        return new CompletedProfile(students.findById(student.id()), initialLoginCode);
    }

    private Organization requireOrganization(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("学生学校或组织必填");
        Organization organization = organizations.findByIdForUpdate(id);
        if (organization == null) throw new ResourceNotFoundException("组织不存在");
        if (!OrganizationOperationalStatusService.isOperational(organization)) throw new IllegalStateException("组织已停用");
        return organization;
    }
    private void requirePermission(Long operatorId, String permission) {
        if (operatorId == null || !permissions.isAllowed(operatorId, PermissionClient.WEB, permission))
            throw new SystemOperationAccessDeniedException("无权执行学生管理操作");
    }
    private void requireScope(Long operatorId, Long organizationId) {
        if (!scopes.canAccess(operatorId, organizationId)) throw new SystemOperationAccessDeniedException("组织不在当前用户数据范围内");
    }
    public record Placement(Long organizationId, Long schoolId, Long classId, String gradeCode) {}
    public record CompletedProfile(Student student, String initialLoginCode) {
        @Override public String toString() { return "CompletedProfile[initialLoginCode=REDACTED]"; }
    }
}



