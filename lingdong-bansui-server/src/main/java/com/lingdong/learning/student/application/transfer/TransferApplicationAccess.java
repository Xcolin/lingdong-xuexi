package com.lingdong.learning.student.application.transfer;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.student.infrastructure.persistence.*;
import com.lingdong.learning.student.domain.StudentStatus;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.organization.application.OrganizationOperationalStatusService;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import org.springframework.stereotype.Service;

/** Re-evaluated on every operation; stored application IDs are never authorization. */
@Service
public class TransferApplicationAccess {
 private final PermissionDecisionService permissions;private final ParentStudentMapper parents;
 private final InstitutionEnrollmentMapper families;private final StudentMapper students;
 private final StudentOrganizationMapper enrollments;private final OrganizationMapper organizations;
 private final OrganizationDataScopeService scopes;
 public TransferApplicationAccess(PermissionDecisionService p,ParentStudentMapper g,InstitutionEnrollmentMapper f,StudentMapper s,StudentOrganizationMapper e,OrganizationMapper o,OrganizationDataScopeService d){permissions=p;parents=g;families=f;students=s;enrollments=e;organizations=o;scopes=d;}
 private void permission(AuthenticatedUser actor,String code){if(actor==null||actor.userId()==null||actor.clientType()==null||!permissions.isAllowed(actor.userId(),PermissionClient.valueOf(actor.clientType().name()),code))deny();}
 private void active(long student,long source){var s=students.findById(student);var o=organizations.findById(source);if(s==null||s.status()!=StudentStatus.ENABLED||o==null||!("SCHOOL".equals(o.typeCode())||"TRAINING_INSTITUTION".equals(o.typeCode()))||!OrganizationOperationalStatusService.isOperational(o)||!enrollments.findActiveEnrollmentOrganizationIds(student).contains(source))deny();}
 public void requireFamily(AuthenticatedUser actor,long student,long source,long family,String code){requireFamilyContext(actor,student,family,code);active(student,source);}
 public void requireFamilyContext(AuthenticatedUser actor,long student,long family,String code){
  permission(actor,code);var f=organizations.findById(family);var s=students.findById(student);var selected=com.lingdong.learning.common.security.SelectedOrganizationContext.organizationId();
  if((selected!=null&&selected.longValue()!=family)||s==null||s.status()!=StudentStatus.ENABLED||f==null||!"FAMILY".equals(f.typeCode())||!OrganizationOperationalStatusService.isOperational(f)||!families.familyMember(actor.userId(),family)||!families.familyMember(s.studentUserId(),family)||!parents.existsActivePrimaryByParentAndStudent(actor.userId(),student))deny();
 }
 public void requireReviewer(AuthenticatedUser actor,TransferApplicationRepository.Stored stored){permission(actor,"STUDENT_TRANSFER_REVIEW");var a=stored.application();active(a.studentId(),a.sourceInstitutionId());if(actor.userId()==a.applicantId()||!enrollments.existsActiveEnrollmentInScope(a.studentId(),a.sourceInstitutionId(),scopes.resolve(actor.userId())))deny();}
 public void requireViewPermission(AuthenticatedUser actor){permission(actor,"STUDENT_TRANSFER_VIEW");}
 public void requireCreatePermission(AuthenticatedUser actor){permission(actor,"STUDENT_TRANSFER_REQUEST_CREATE");}
 public long requireInstitutionSubmission(AuthenticatedUser actor,long student,long source){return institutionGuardian(actor,student,source,true);}
 public long requireInstitutionOptions(AuthenticatedUser actor,long student,long source){return institutionGuardian(actor,student,source,false);}
 private long institutionGuardian(AuthenticatedUser actor,long student,long source,boolean lock){
  permission(actor,"STUDENT_TRANSFER_REQUEST_CREATE");permission(actor,"STUDENT_ORGANIZATION_MANAGE");
  if(!scopes.canAccess(actor.userId(),source))deny();
  if(lock&&students.findByIdForUpdate(student)==null)deny();active(student,source);
  var guardians=(lock?parents.findActiveByStudentIdForUpdate(student):parents.findActiveByStudentId(student)).stream().filter(g->g.role()==com.lingdong.learning.student.domain.ParentRelationshipRole.PRIMARY_GUARDIAN&&"ACTIVE".equals(g.status())&&"PRIMARY".equals(g.primaryScopeKey())).toList();
  if(guardians.size()!=1)deny();return guardians.get(0).parentUserId();
 }
 public void requireBlockedReviewer(AuthenticatedUser actor,TransferApplicationRepository.Stored stored){
  permission(actor,"STUDENT_TRANSFER_REVIEW");
  if(actor.userId()==stored.application().applicantId()||!scopes.canAccess(actor.userId(),stored.application().sourceInstitutionId()))deny();
 }
 /** Background execution uses persisted identities, never an invented HTTP principal. */
 public String executionIssue(TransferApplicationRepository.Stored stored,TransferApplicationRepository.ExecutionIdentity identity){
  var a=stored.application();var student=students.findById(a.studentId());var source=organizations.findById(a.sourceInstitutionId());var family=organizations.findById(stored.familyId());
  if(student==null||student.status()!=StudentStatus.ENABLED)return "STUDENT_UNAVAILABLE";
  if(source==null||!("SCHOOL".equals(source.typeCode())||"TRAINING_INSTITUTION".equals(source.typeCode()))||!OrganizationOperationalStatusService.isOperational(source))return "SOURCE_UNAVAILABLE";
  if(!enrollments.findActiveEnrollmentOrganizationIds(a.studentId()).contains(a.sourceInstitutionId()))return "ENROLLMENT_CHANGED";
  if(identity==null||identity.applicantId()!=a.applicantId()||identity.reviewerId()==null||identity.reviewerId()==a.applicantId()||identity.guardianId()==null||identity.submissionMode()==null||!java.util.Set.of("FAMILY","INSTITUTION").contains(identity.submissionMode())||("FAMILY".equals(identity.submissionMode())&&identity.guardianId()!=a.applicantId()))return "REVIEW_IDENTITY_MISSING";
  long guardianId=identity.guardianId();
  var currentGuardian=parents.findActivePrimaryByStudentIdForUpdate(a.studentId());
  if(currentGuardian==null||currentGuardian.parentUserId()!=guardianId||currentGuardian.role()!=com.lingdong.learning.student.domain.ParentRelationshipRole.PRIMARY_GUARDIAN||!"ACTIVE".equals(currentGuardian.status())||!"PRIMARY".equals(currentGuardian.primaryScopeKey()))return "GUARDIAN_CHANGED";
  if(family==null||!"FAMILY".equals(family.typeCode())||!OrganizationOperationalStatusService.isOperational(family)||!families.familyMember(guardianId,stored.familyId())||!families.familyMember(student.studentUserId(),stored.familyId())||!parents.existsActivePrimaryByParentAndStudent(guardianId,a.studentId()))return "GUARDIAN_CHANGED";
  PermissionClient applicantClient,reviewerClient;
  try{applicantClient=PermissionClient.valueOf(identity.applicantClient());reviewerClient=PermissionClient.valueOf(identity.reviewerClient());}catch(RuntimeException invalid){return "REVIEW_IDENTITY_MISSING";}
  if(applicantClient==PermissionClient.BOTH||reviewerClient==PermissionClient.BOTH)return "REVIEW_IDENTITY_MISSING";
  if(!permissions.isAllowed(a.applicantId(),applicantClient,"STUDENT_TRANSFER_REQUEST_CREATE"))return "APPLICANT_PERMISSION_CHANGED";
  if("INSTITUTION".equals(identity.submissionMode())&&(!permissions.isAllowed(a.applicantId(),applicantClient,"STUDENT_ORGANIZATION_MANAGE")||!scopes.canAccess(a.applicantId(),a.sourceInstitutionId())))return "DELEGATE_AUTHORITY_CHANGED";
  if(!permissions.isAllowed(identity.reviewerId(),reviewerClient,"STUDENT_TRANSFER_REVIEW")||!scopes.canAccess(identity.reviewerId(),a.sourceInstitutionId()))return "REVIEW_AUTHORITY_CHANGED";
  return null;
 }
 public boolean requireList(AuthenticatedUser actor,long organizationId){
  requireViewPermission(actor);var organization=organizations.findById(organizationId);
  if(organization==null||!OrganizationOperationalStatusService.isOperational(organization))deny();
  if("FAMILY".equals(organization.typeCode())){
   var selected=com.lingdong.learning.common.security.SelectedOrganizationContext.organizationId();
   if((selected!=null&&selected.longValue()!=organizationId)||!families.familyMember(actor.userId(),organizationId))deny();
   return true;
  }
  if(!("SCHOOL".equals(organization.typeCode())||"TRAINING_INSTITUTION".equals(organization.typeCode()))||!scopes.canAccess(actor.userId(),organizationId))deny();
  return false;
 }
 public void requireRead(AuthenticatedUser actor,TransferApplicationRepository.Stored stored){
  requireViewPermission(actor);var a=stored.application();var student=students.findById(a.studentId());
  var family=organizations.findById(stored.familyId());
  boolean guardian=student!=null&&family!=null&&"FAMILY".equals(family.typeCode())&&OrganizationOperationalStatusService.isOperational(family)
    &&families.familyMember(actor.userId(),stored.familyId())&&families.familyMember(student.studentUserId(),stored.familyId())&&parents.existsActivePrimaryByParentAndStudent(actor.userId(),a.studentId());
  // Historical proposals remain readable after leaving school; current scope is still mandatory.
  if(!guardian&&!scopes.canAccess(actor.userId(),a.sourceInstitutionId()))deny();
 }
 private static void deny(){throw new SystemOperationAccessDeniedException("转学申请不存在或无权访问");}
}
