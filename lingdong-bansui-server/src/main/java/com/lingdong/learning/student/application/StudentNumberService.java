package com.lingdong.learning.student.application;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.organization.application.*;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.student.infrastructure.persistence.*;
import com.lingdong.learning.student.domain.StudentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class StudentNumberService {
 private final AcademicMetadataStore store;private final StudentManagementAccessService access;private final OrganizationMapper organizations;private final StudentMapper students;private final StudentOrganizationMapper relationships;private final FeatureAccessService features;
 public StudentNumberService(AcademicMetadataStore store,StudentManagementAccessService access,OrganizationMapper organizations,StudentMapper students,StudentOrganizationMapper relationships,FeatureAccessService features){this.store=store;this.access=access;this.organizations=organizations;this.students=students;this.relationships=relationships;this.features=features;}
 @Transactional(readOnly=true) public AcademicMetadataStore.EnrollmentMetadata read(AuthenticatedUser actor,long studentId,long institutionId){
  require(actor,studentId,institutionId,false);return store.enrollmentMetadata(studentId,institutionId).orElse(new AcademicMetadataStore.EnrollmentMetadata(studentId,institutionId,null,0));
 }
 @Transactional public AcademicMetadataStore.EnrollmentMetadata update(AuthenticatedUser actor,long studentId,long institutionId,String number,int version){
  require(actor,studentId,institutionId,true);if(version<0)throw new IllegalArgumentException("版本不合法");
  String normalized=number==null?null:number.trim();if(normalized!=null&&(normalized.isEmpty()||normalized.codePointCount(0,normalized.length())>64||normalized.codePoints().anyMatch(Character::isISOControl)))throw new IllegalArgumentException("学号需为1至64个有效字符");
  var previous=store.enrollmentMetadata(studentId,institutionId);
  try{if(previous.isEmpty()){if(version!=0)throw new IllegalStateException("学号已变化，请刷新");store.insertEnrollment(studentId,institutionId,normalized);}else if(version!=previous.get().versionNo()||!store.updateEnrollment(studentId,institutionId,normalized,version))throw new IllegalStateException("学号已变化，请刷新");}
  catch(org.springframework.dao.DuplicateKeyException duplicate){throw new IllegalStateException("该机构学号已被使用，请刷新后重试",duplicate);}
  store.recordAudit("STUDENT_NUMBER",studentId,institutionId,previous.map(value->value.versionNo()+1).orElse(1),actor.userId(),previous.map(AcademicMetadataStore.EnrollmentMetadata::studentNumber).orElse(null),normalized);
  return store.enrollmentMetadata(studentId,institutionId).orElseThrow();
 }
 private void require(AuthenticatedUser actor,long studentId,long institutionId,boolean lock){
  access.require(actor,"STUDENT_ORGANIZATION_MANAGE");features.requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP",null);
  if(studentId<=0||institutionId<=0)throw new IllegalArgumentException("学生及入学机构不能为空");
  var student=lock?students.findByIdForUpdate(studentId):students.findById(studentId);if(student==null||student.status()!=StudentStatus.ENABLED)throw new ResourceNotFoundException("学生不存在或不可访问");
  if(lock&&!relationships.findActiveOrganizationIdsForUpdate(studentId).contains(institutionId))throw new ResourceNotFoundException("有效入学关系不存在");
  var institution=lock?organizations.findByIdForUpdate(institutionId):organizations.findById(institutionId);
  if(!OrganizationHierarchy.isInstitutionRoot(institution)||!OrganizationOperationalStatusService.isOperational(institution)||!relationships.existsActiveEnrollmentInScope(studentId,institutionId,access.scope(actor)))throw new ResourceNotFoundException("入学机构不存在或不可访问");
  features.requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP",institutionId);
 }
}
