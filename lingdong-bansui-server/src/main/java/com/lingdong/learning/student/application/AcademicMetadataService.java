package com.lingdong.learning.student.application;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.organization.application.OrganizationHierarchy;
import com.lingdong.learning.organization.application.OrganizationOperationalStatusService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.student.infrastructure.persistence.AcademicMetadataStore;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.learningtask.infrastructure.persistence.TeacherClassMapper;
import com.lingdong.learning.learningtask.domain.TeacherClassStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class AcademicMetadataService {
 private final com.lingdong.learning.feature.application.FeatureAccessService features;private final AcademicMetadataStore store;private final StudentManagementAccessService access;private final OrganizationMapper organizations;private final UserMapper users;private final TeacherClassMapper teachers;
 public AcademicMetadataService(AcademicMetadataStore store,StudentManagementAccessService access,OrganizationMapper organizations,UserMapper users,TeacherClassMapper teachers,com.lingdong.learning.feature.application.FeatureAccessService features){this.features=features;this.store=store;this.access=access;this.organizations=organizations;this.users=users;this.teachers=teachers;}
 @Transactional public AcademicMetadataStore.ClassMetadata setHeadTeacher(AuthenticatedUser actor,long classId,Long teacherId,int version){
  access.require(actor,"CLASS_UPDATE");features.requireEnabled("CLASS_MANAGEMENT",null);if(classId<=0||version<0||teacherId!=null&&teacherId<=0)throw new IllegalArgumentException("班级、教师或版本不合法");
  Organization node=organizations.findByIdForUpdate(classId);requireClass(actor,node);features.requireEnabled("CLASS_MANAGEMENT",classId);
  if(teacherId!=null){var teacher=users.findByIdForUpdate(teacherId);if(teacher==null||teacher.status()!=UserStatus.ENABLED)throw new IllegalArgumentException("教师账号不可用");requireTeaching(teacherId,classId);if(!eligibleTeacher(teacherId,classId))throw new IllegalArgumentException("教师档案已停用或任教关系已变化");}
  var previous=store.classMetadata(classId);
  if(previous.isEmpty()){if(version!=0)throw new IllegalStateException("班级配置已变化，请刷新");store.insertClass(classId,teacherId);}
  else if(version!=previous.get().versionNo()||!store.updateClass(classId,teacherId,version))throw new IllegalStateException("班级配置已变化，请刷新");
  store.recordAudit("HEAD_TEACHER",classId,classId,previous.map(value->value.versionNo()+1).orElse(1),actor.userId(),previous.map(value->value.headTeacherUserId()==null?null:value.headTeacherUserId().toString()).orElse(null),teacherId==null?null:teacherId.toString());
  return store.classMetadata(classId).orElseThrow();
 }
 @Transactional(readOnly=true) public AcademicMetadataStore.ClassMetadata classMetadata(AuthenticatedUser actor,long classId){
  access.require(actor,"CLASS_READ");features.requireEnabled("CLASS_MANAGEMENT",null);var node=organizations.findById(classId);requireClass(actor,node);features.requireEnabled("CLASS_MANAGEMENT",classId);
  var value=store.classMetadata(classId).orElse(new AcademicMetadataStore.ClassMetadata(classId,null,0));
  if(value.headTeacherUserId()!=null){var teacher=users.findById(value.headTeacherUserId());var relation=teachers.findByTeacherAndClass(value.headTeacherUserId(),classId);if(teacher==null||teacher.status()!=UserStatus.ENABLED||relation==null||relation.status()!=TeacherClassStatus.ACTIVE||relation.effectiveTo()!=null||!eligibleTeacher(value.headTeacherUserId(),classId))return new AcademicMetadataStore.ClassMetadata(classId,null,value.versionNo());}
  return value;
 }
 public record ClassView(AcademicMetadataStore.ClassMetadata metadata,String headTeacherName){}
 @Transactional(readOnly=true) public ClassView classView(AuthenticatedUser actor,long classId){
  var metadata=classMetadata(actor,classId);
  String name=metadata.headTeacherUserId()==null?null:store.headTeacherOptions(classId).stream().filter(option->option.userId()==metadata.headTeacherUserId()).map(AcademicMetadataStore.HeadTeacherOption::displayName).findFirst().orElse(null);
  return new ClassView(metadata,name);
 }
 @Transactional(readOnly=true) public java.util.List<AcademicMetadataStore.HeadTeacherOption> headTeacherOptions(AuthenticatedUser actor,long classId){
  access.require(actor,"CLASS_UPDATE");features.requireEnabled("CLASS_MANAGEMENT",null);var node=organizations.findById(classId);requireClass(actor,node);features.requireEnabled("CLASS_MANAGEMENT",classId);return store.headTeacherOptions(classId);
 }
 private void requireClass(AuthenticatedUser actor,Organization node){if(node==null||!"CLASS".equals(node.typeCode())||OrganizationHierarchy.schoolOf(node,organizations)==null)throw new ResourceNotFoundException("班级不存在");if(!OrganizationOperationalStatusService.isOperational(node))throw new IllegalStateException("班级已停用");if(!access.organizationAllowed(actor,node.id()))throw new SystemOperationAccessDeniedException("无权管理该班级");}
 private boolean eligibleTeacher(long teacherId,long classId){return store.headTeacherOptions(classId).stream().anyMatch(option->option.userId()==teacherId);}
 private void requireTeaching(long teacherId,long classId){var relation=teachers.findByTeacherAndClass(teacherId,classId);if(relation==null||relation.status()!=TeacherClassStatus.ACTIVE||relation.effectiveTo()!=null)throw new IllegalArgumentException("班主任必须是本班有效任教教师");}
}
