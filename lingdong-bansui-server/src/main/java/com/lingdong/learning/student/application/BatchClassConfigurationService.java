package com.lingdong.learning.student.application;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.organization.application.OrganizationHierarchy;
import com.lingdong.learning.organization.application.OrganizationOperationalStatusService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.student.domain.StudentStatus;
import com.lingdong.learning.student.infrastructure.persistence.*;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import java.util.*;
/** Atomic configuration reuses school transfer and training institution multi-class semantics. */
@Service
public class BatchClassConfigurationService {
 private final StudentMapper students;private final StudentOrganizationMapper relations;private final OrganizationMapper organizations;private final StudentManagementAccessService access;private final OrganizationDataScopeService scopes;private final FeatureAccessService features;private final StudentOrganizationLifecycleService lifecycle;
 public BatchClassConfigurationService(StudentMapper s,StudentOrganizationMapper r,OrganizationMapper o,StudentManagementAccessService a,OrganizationDataScopeService d,FeatureAccessService f,StudentOrganizationLifecycleService l){students=s;relations=r;organizations=o;access=a;scopes=d;features=f;lifecycle=l;}
 @Transactional public List<Long> configure(AuthenticatedUser actor,long sourceClassId,long targetClassId,List<Long> studentIds,String reason){
  access.require(actor,"STUDENT_ORGANIZATION_MANAGE");features.requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP",null);
  if(sourceClassId<=0||targetClassId<=0||sourceClassId==targetClassId)throw new IllegalArgumentException("请选择不同的目标班级");
  if(studentIds==null||studentIds.isEmpty()||studentIds.size()>50||studentIds.stream().anyMatch(id->id==null||id<=0)||new HashSet<>(studentIds).size()!=studentIds.size())throw new IllegalArgumentException("每次请选择1至50名不同成员");
  String normalized=reason==null?"":reason.trim();if(normalized.isEmpty()||normalized.length()>200)throw new IllegalArgumentException("请填写200字以内的调整原因");
  Organization source=organizations.findById(sourceClassId),target=organizations.findById(targetClassId);
  if(!usableClass(source)||!usableClass(target)||!scopes.canAccess(actor.userId(),sourceClassId)||!scopes.canAccess(actor.userId(),targetClassId))throw unavailable();
  var sourceRoot=OrganizationHierarchy.schoolOf(source,organizations);var targetRoot=OrganizationHierarchy.schoolOf(target,organizations);
  if(sourceRoot==null||targetRoot==null||!sourceRoot.id().equals(targetRoot.id())||!OrganizationOperationalStatusService.isOperational(sourceRoot)||!OrganizationOperationalStatusService.isOperational(targetRoot))throw unavailable();
  var ordered=studentIds.stream().sorted().toList();
  for(long studentId:ordered){var student=students.findByIdForUpdate(studentId);if(student==null||student.status()!=StudentStatus.ENABLED||!relations.findActiveEnrollmentOrganizationIds(studentId).contains(sourceRoot.id())||!relations.findActiveClassOrganizationIdsInEnrollment(studentId,sourceRoot.id()).contains(sourceClassId))throw new IllegalStateException("成员班级关系已变化，请刷新后重新选择");}
  for(long studentId:ordered)lifecycle.transferClass(actor,studentId,new TransferStudentClassCommand(targetClassId,normalized));
  return ordered;
 }
 private boolean usableClass(Organization o){return o!=null&&"CLASS".equals(o.typeCode())&&OrganizationOperationalStatusService.isOperational(o);}
 private ResourceNotFoundException unavailable(){return new ResourceNotFoundException("班级不存在、不可用或不在同一管理学校范围");}
}
