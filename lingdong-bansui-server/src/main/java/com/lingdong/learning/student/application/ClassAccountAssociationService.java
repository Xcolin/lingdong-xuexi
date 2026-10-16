package com.lingdong.learning.student.application;

import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.organization.application.OrganizationHierarchy;
import com.lingdong.learning.organization.application.OrganizationOperationalStatusService;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Account ownership proof never grants access to a foreign institution enrollment. */
@Service
public class ClassAccountAssociationService {
 private final StudentManagementAccessService access;
 private final OrganizationMapper organizations;
 private final StudentCodeLoginApplicationService proof;
 private final StudentOrganizationMapper relationships;
 private final StudentOrganizationLifecycleService lifecycle;
 private final FeatureAccessService features;
 public ClassAccountAssociationService(StudentManagementAccessService access,OrganizationMapper organizations,StudentCodeLoginApplicationService proof,StudentOrganizationMapper relationships,StudentOrganizationLifecycleService lifecycle,FeatureAccessService features){this.access=access;this.organizations=organizations;this.proof=proof;this.relationships=relationships;this.lifecycle=lifecycle;this.features=features;}
 // Reads after the credential/student locks must see the latest committed class relation.
 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED,noRollbackFor={StudentAuthenticationFailedException.class,CaptchaRequiredException.class,StudentAccountLockedException.class})
 public StudentOrganizationRelationshipView associate(AuthenticatedUser actor,long classId,StudentCodeLoginCommand command){
  access.require(actor,"STUDENT_CLASS_ASSIGN");access.require(actor,"STUDENT_ORGANIZATION_MANAGE");
  features.requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP",null);features.requireEnabled("CLASS_MANAGEMENT",null);features.requireEnabled("STUDENT_CODE_LOGIN",null);
  var classroom=organizations.findById(classId);
  if(classroom==null||!"CLASS".equals(classroom.typeCode()))throw new ResourceNotFoundException("班级不存在");
  if(!access.organizationAllowed(actor,classId))throw new SystemOperationAccessDeniedException("无权关联该班级账号");
  var institution=OrganizationHierarchy.schoolOf(classroom,organizations);
  if(institution==null||!OrganizationOperationalStatusService.isOperational(classroom)||!OrganizationOperationalStatusService.isOperational(institution))throw new IllegalStateException("班级或所属机构不可用");
  features.requireEnabled("CLASS_MANAGEMENT",classId);features.requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP",institution.id());
  var identity=proof.verifyForBinding(command);
  if(!relationships.existsActiveEnrollmentInScope(identity.studentId(),institution.id(),access.scope(actor)))throw new SystemOperationAccessDeniedException("账号尚未加入当前机构，请先完成入学申请");
  return lifecycle.transferClass(actor,identity.studentId(),new TransferStudentClassCommand(classId,"验证账号归属后关联班级",true));
 }
}
