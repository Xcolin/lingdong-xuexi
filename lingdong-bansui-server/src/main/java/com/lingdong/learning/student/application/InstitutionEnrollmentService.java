package com.lingdong.learning.student.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper;
import com.lingdong.learning.organization.application.OrganizationHierarchy;
import com.lingdong.learning.organization.application.OrganizationOperationalStatusService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.domain.StudentStatus;
import com.lingdong.learning.student.infrastructure.persistence.*;
import com.lingdong.learning.user.infrastructure.persistence.UserOrganizationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class InstitutionEnrollmentService {
    @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.feature.application.FeatureAccessService features;
    private final InstitutionEnrollmentMapper requests;
    private final StudentMapper students;
    private final StudentOrganizationMapper relationships;
    private final StudentOrganizationChangeMapper changes;
    private final ParentStudentMapper parents;
    private final OrganizationMapper organizations;
    private final OrganizationAdminMapper admins;
    private final UserOrganizationMapper memberships;
    private final PermissionDecisionService permissions;
    private final OrganizationDataScopeService scopes;
    private final IdGenerator ids;
    private final Clock clock;
    private final SecureRandom random=new SecureRandom();
    private final Map<Long,java.util.ArrayDeque<Long>> resolveAttempts=new java.util.concurrent.ConcurrentHashMap<>();
    public InstitutionEnrollmentService(InstitutionEnrollmentMapper requests,StudentMapper students,
            StudentOrganizationMapper relationships,StudentOrganizationChangeMapper changes,ParentStudentMapper parents,
            OrganizationMapper organizations,OrganizationAdminMapper admins,UserOrganizationMapper memberships,
            PermissionDecisionService permissions,OrganizationDataScopeService scopes,IdGenerator ids,Clock clock) {
        this.requests=requests;this.students=students;this.relationships=relationships;this.changes=changes;
        this.parents=parents;this.organizations=organizations;this.admins=admins;this.memberships=memberships;
        this.permissions=permissions;this.scopes=scopes;this.ids=ids;this.clock=clock;
    }
    public record ResolvedInvitation(@com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class) Long institutionOrganizationId,String institutionName,@com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class) Long classOrganizationId,String className,LocalDateTime expiresAt) {}
    public record CreatedInvitation(@com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class) Long id,String inviteCode,@com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class) Long institutionOrganizationId,String institutionName,@com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class) Long classOrganizationId,String className,LocalDateTime expiresAt) {
        @Override public String toString(){return "CreatedInvitation[inviteCode=REDACTED]";}
    }
    @Transactional public CreatedInvitation createInvitation(AuthenticatedUser actor,Long classId,Integer validDays) {
        requirePermission(actor,"STUDENT_ENROLLMENT_INVITE_CREATE");
        Organization classroom=requireClass(classId);
        requireScope(actor,classroom.id());
        Organization institution=OrganizationHierarchy.schoolOf(classroom,organizations);
        if(institution==null)throw new IllegalArgumentException("班级没有所属教育机构");
        int days=validDays==null?7:validDays;
        if(days<1||days>30)throw new IllegalArgumentException("邀请码有效天数必须为1至30");
        byte[] bytes=new byte[32];random.nextBytes(bytes);
        String code=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Long id=ids.nextId();LocalDateTime expiry=LocalDateTime.now(clock).plusDays(days);
        requests.insertInvitation(new InstitutionEnrollmentMapper.Invitation(id,hash(code),institution.id(),classId,actor.userId(),expiry,"ACTIVE",100,0));
        return new CreatedInvitation(id,code,institution.id(),institution.name(),classId,classroom.name(),expiry);
    }
    public record InvitationExpiryUpdated(@com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class) Long id,LocalDateTime expiresAt) {}
    /** 管理员对已生成邀请码设置有效期（1-30 天），仅 ACTIVE 邀请码且授权范围内可更新。 */
    @Transactional public InvitationExpiryUpdated updateInvitationExpiry(AuthenticatedUser actor,Long invitationId,Integer validDays) {
        requirePermission(actor,"STUDENT_ENROLLMENT_INVITE_CREATE");
        if(validDays==null||validDays<1||validDays>30)throw new IllegalArgumentException("邀请码有效天数必须为1至30");
        InstitutionEnrollmentMapper.Invitation invite=requests.lockInvitationById(invitationId);
        if(invite==null||!"ACTIVE".equals(invite.status()))throw new InstitutionEnrollmentStateException("邀请码不存在或已失效");
        requireScope(actor,invite.classOrganizationId());
        LocalDateTime expiry=LocalDateTime.now(clock).plusDays(validDays);
        if(requests.updateInvitationExpiry(invitationId,expiry)!=1)throw new InstitutionEnrollmentStateException("邀请码已失效");
        return new InvitationExpiryUpdated(invitationId,expiry);
    }
    @Transactional public ResolvedInvitation resolve(AuthenticatedUser actor,String code) {
        requireActor(actor);limitResolve(actor.userId());
        var invite=requireInvitation(code);var classroom=requireClass(invite.classOrganizationId());var institution=requireInstitution(invite.institutionOrganizationId());
        ensureClassInstitution(classroom,institution);
        return new ResolvedInvitation(institution.id(),institution.name(),classroom.id(),classroom.name(),invite.expiresAt());
    }
    @Transactional public InstitutionEnrollmentMapper.Request request(AuthenticatedUser actor,Long studentUserId,String code,Long familyId) {
        requirePermission(actor,"STUDENT_ENROLLMENT_REQUEST_CREATE");
        Student student=students.findByStudentUserId(studentUserId);
        if(student==null||student.status()!=StudentStatus.ENABLED)throw notFound();
        student=students.findByIdForUpdate(student.id());
        requireFamilyStudent(actor,student,familyId,"STUDENT_ENROLLMENT_REQUEST_CREATE");
        var invite=requireInvitation(code);var classroom=requireClass(invite.classOrganizationId());var institution=requireInstitution(invite.institutionOrganizationId());
        ensureClassInstitution(classroom,institution);
        if(relationships.existsActiveClass(student.id(),classroom.id()))throw new InstitutionEnrollmentStateException("学生已加入该班级");
        if(requests.pending(student.id(),classroom.id())>0)throw new InstitutionEnrollmentStateException("该学生已提交待审核的入学申请");
        rejectSchoolDuplicate(student.id(),institution,classroom.id());
        if(requests.consume(invite.id())!=1)throw new InstitutionEnrollmentStateException("邀请码已失效");
        Long id=ids.nextId();requests.insertRequest(id,invite.id(),student.id(),familyId,institution.id(),classroom.id(),actor.userId(),LocalDateTime.now(clock));
        return requests.lockRequest(id);
    }
    @Transactional(readOnly=true) public List<InstitutionEnrollmentMapper.Request> list(AuthenticatedUser actor,Long institutionId,Long classId,Long familyId,String status) {
        requireActor(actor);
        if(status!=null&&!Set.of("PENDING","APPROVED","REJECTED").contains(status))throw new IllegalArgumentException("申请状态无效");
        if(familyId==null) {
            requirePermission(actor,"STUDENT_ENROLLMENT_REVIEW");
            if(institutionId==null&&classId==null)throw new IllegalArgumentException("请明确指定教育机构或班级");
            if(classId!=null){var c=requireClass(classId);var i=OrganizationHierarchy.schoolOf(c,organizations);if(i==null||institutionId!=null&&!institutionId.equals(i.id()))throw notFound();requireScope(actor,classId);}
            else {requireInstitution(institutionId);requireScope(actor,institutionId);}
        } else {requirePermission(actor,"STUDENT_ENROLLMENT_REQUEST_CREATE");requireFamily(familyId);}
        return requests.list(institutionId,classId,familyId,status).stream().filter(r -> {
            if(familyId!=null) {Student student=students.findById(r.studentId());return student!=null&&canFamilyStudent(actor,student,familyId,"STUDENT_ENROLLMENT_REQUEST_CREATE");}
            return scopes.canAccess(actor.userId(),r.classOrganizationId());
        }).toList();
    }
    @Transactional public InstitutionEnrollmentMapper.Request review(AuthenticatedUser actor,Long id,boolean approve,String reason) {
        requirePermission(actor,"STUDENT_ENROLLMENT_REVIEW");
        var request=requests.lockRequest(id);if(request==null)throw notFound();
        requireScope(actor,request.classOrganizationId());
        if(!"PENDING".equals(request.status()))throw new InstitutionEnrollmentStateException("申请已经审核");
        if(reason!=null&&reason.length()>200)throw new IllegalArgumentException("审核说明不能超过200字");
        if(!approve&&(reason==null||reason.isBlank()))throw new IllegalArgumentException("请填写拒绝原因");
        if(approve) {
            Student student=students.findByIdForUpdate(request.studentId());if(student==null||student.status()!=StudentStatus.ENABLED)throw notFound();
            var classroom=requireClass(request.classOrganizationId());var institution=requireInstitution(request.institutionOrganizationId());ensureClassInstitution(classroom,institution);
            requireFamily(request.familyOrganizationId());if(!requests.familyMember(student.studentUserId(),request.familyOrganizationId()))throw new InstitutionEnrollmentStateException("学生已离开申请家庭，请重新申请");
            rejectSchoolDuplicate(student.id(),institution,classroom.id());
            if(!relationships.findActiveEnrollmentOrganizationIds(student.id()).contains(institution.id())&&requests.activateEnrollment(student.id(),institution.id())==0)
                relationships.insertEnrollment(ids.nextId(),student.id(),institution.id());
            if(!relationships.existsActiveClass(student.id(),classroom.id())&&relationships.activateExistingClass(student.id(),classroom.id())==0)
                relationships.insertClass(ids.nextId(),student.id(),classroom.id());
            addMembership(student.studentUserId(),institution.id());addMembership(student.studentUserId(),classroom.id());
            changes.insert(ids.nextId(),student.id(),"CLASS_ASSIGN",null,classroom.id(),"机构入学申请审核通过",actor.userId(),LocalDateTime.now(clock));
        }
        if(requests.review(id,approve?"APPROVED":"REJECTED",actor.userId(),LocalDateTime.now(clock),reason)!=1)throw new InstitutionEnrollmentStateException("申请已经审核");
        return requests.lockRequest(id);
    }
    public void requireFamilyStudent(AuthenticatedUser actor,Student student,Long familyId,String permission) {
        requireFamily(familyId);if(!canFamilyStudent(actor,student,familyId,permission))throw new SystemOperationAccessDeniedException("无权管理该家庭学生");
    }
    public boolean canFamilyStudent(AuthenticatedUser actor,Student student,Long familyId,String permission) {
        if(actor==null||student==null||!requests.familyMember(student.studentUserId(),familyId))return false;
        return parents.existsActiveByParentAndStudent(actor.userId(),student.id())||admins.exists(actor.userId(),familyId)
                || allowed(actor,permission)&&scopes.canAccess(actor.userId(),familyId);
    }
    private void addMembership(Long userId,Long orgId){if(!memberships.exists(userId,orgId))memberships.insert(ids.nextId(),userId,orgId);}
    private void rejectSchoolDuplicate(Long studentId,Organization institution,Long classId) {
        if("SCHOOL".equals(institution.typeCode())&&relationships.findActiveClassOrganizationIdsInEnrollment(studentId,institution.id()).stream().anyMatch(id->!id.equals(classId)))
            throw new InstitutionEnrollmentStateException("学生在该学校已有行政班级，请使用校内转班");
    }
    private void ensureClassInstitution(Organization classroom,Organization institution){var actual=OrganizationHierarchy.schoolOf(classroom,organizations);if(actual==null||!actual.id().equals(institution.id()))throw new InstitutionEnrollmentStateException("邀请码班级归属已经变更");}
    private InstitutionEnrollmentMapper.Invitation requireInvitation(String code) {
        var invitation=requests.lockInvitation(hash(code));
        if(invitation==null||!"ACTIVE".equals(invitation.status())||!invitation.expiresAt().isAfter(LocalDateTime.now(clock))||invitation.usedCount()>=invitation.maxUses())throw new IllegalArgumentException("邀请码不存在或已失效");
        return invitation;
    }
    private Organization requireClass(Long id){var o=requireOrganization(id);if(!"CLASS".equals(o.typeCode()))throw new IllegalArgumentException("目标组织不是班级");return o;}
    private Organization requireInstitution(Long id){var o=requireOrganization(id);if(!OrganizationHierarchy.isInstitutionRoot(o))throw new IllegalArgumentException("目标组织不是教育机构");return o;}
    private Organization requireFamily(Long id){var o=requireOrganization(id);if(!"FAMILY".equals(o.typeCode()))throw new IllegalArgumentException("目标组织不是家庭");return o;}
    private Organization requireOrganization(Long id){var o=id==null?null:organizations.findById(id);if(o==null||!OrganizationOperationalStatusService.isOperational(o))throw notFound();return o;}
    private void requireScope(AuthenticatedUser actor,Long id){if(!scopes.canAccess(actor.userId(),id))throw notFound();}
    private boolean allowed(AuthenticatedUser actor,String permission){return actor!=null&&actor.clientType()!=null&&permissions.isAllowed(actor.userId(),PermissionClient.valueOf(actor.clientType().name()),permission);}
    private void requirePermission(AuthenticatedUser actor,String permission){requireActor(actor);if(!allowed(actor,permission))throw new SystemOperationAccessDeniedException("无权执行机构入学操作");}
    private void requireActor(AuthenticatedUser actor){if(actor==null||actor.userId()==null)throw new SystemOperationAccessDeniedException("请先登录");if(features!=null)features.requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP",null);}
    private String hash(String code){if(code==null||!code.matches("[A-Za-z0-9_-]{43}"))throw new IllegalArgumentException("邀请码格式无效");try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
    private void limitResolve(Long userId){long now=clock.millis();var attempts=resolveAttempts.computeIfAbsent(userId,id->new java.util.ArrayDeque<>());synchronized(attempts){while(!attempts.isEmpty()&&attempts.peekFirst()<now-60000)attempts.removeFirst();if(attempts.size()>=20)throw new InstitutionEnrollmentStateException("查询过于频繁，请稍后重试");attempts.addLast(now);}}
    private ResourceNotFoundException notFound(){return new ResourceNotFoundException("机构、班级或申请不存在或无权访问");}
}


