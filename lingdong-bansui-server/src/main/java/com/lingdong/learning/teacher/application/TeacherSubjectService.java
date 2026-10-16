package com.lingdong.learning.teacher.application;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.teacher.infrastructure.persistence.*;
import com.lingdong.learning.user.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class TeacherSubjectService {
 private final PermissionDecisionService permissions;private final TeacherManagementAccessService access;private final OrganizationDataScopeService scopes;private final TeacherManagementMapper teachers;private final UserOrganizationMapper memberships;private final UserMapper users;private final TeacherSubjectStore store;private final TeacherSubjectDictionary dictionary;
 public TeacherSubjectService(PermissionDecisionService permissions,TeacherManagementAccessService access,OrganizationDataScopeService scopes,TeacherManagementMapper teachers,UserOrganizationMapper memberships,UserMapper users,TeacherSubjectStore store,TeacherSubjectDictionary dictionary){this.permissions=permissions;this.access=access;this.scopes=scopes;this.teachers=teachers;this.memberships=memberships;this.users=users;this.store=store;this.dictionary=dictionary;}
 private void require(AuthenticatedUser actor,String code){if(actor==null||actor.clientType()==null||!permissions.isAllowed(actor.userId(),PermissionClient.valueOf(actor.clientType().name()),code))throw new SystemOperationAccessDeniedException("无权维护教师学科");access.requireManager(actor);}
 private void guard(AuthenticatedUser actor,long teacher,long institution){if(teacher<=0||institution<=0)throw missing();access.requireManageableSchool(actor,institution);var scope=scopes.resolve(actor.userId());if(!scope.allOrganizations()&&scope.rootPaths().isEmpty())throw missing();if(teachers.findAccessibleById(teacher,scope.allOrganizations(),scope.rootPaths())==null||!memberships.exists(teacher,institution))throw missing();}
 @Transactional(readOnly=true) public TeacherSubjectStore.Metadata read(AuthenticatedUser actor,long teacher,long institution){require(actor,"TEACHER_READ");guard(actor,teacher,institution);return store.read(teacher,institution).orElse(new TeacherSubjectStore.Metadata(teacher,institution,null,0));}
 public java.util.List<TeacherSubjectDictionary.Option> options(AuthenticatedUser actor){require(actor,"TEACHER_READ");return dictionary.options();}
 public record Summary(
  @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class) long teacherUserId,
  @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class) long institutionOrganizationId,
  String subjectCode,String subjectName,int classCount){}
 @Transactional(readOnly=true) public java.util.List<Summary> summaries(AuthenticatedUser actor,java.util.List<TeacherSubjectStore.Key> keys){
  require(actor,"TEACHER_READ");
  if(keys==null||keys.size()>100||keys.stream().anyMatch(java.util.Objects::isNull))throw new IllegalArgumentException("一次最多读取100位教师学科");
  if(keys.isEmpty())return java.util.List.of();
  var distinct=keys.stream().distinct().toList();
  for(var key:distinct)guard(actor,key.teacherUserId(),key.institutionOrganizationId());
  var values=store.readBatch(distinct);
  var metadata=values.stream().collect(java.util.stream.Collectors.toMap(value->new TeacherSubjectStore.Key(value.teacherUserId(),value.institutionOrganizationId()),java.util.function.Function.identity()));
  var counts=store.readClassCounts(distinct);
  var names=dictionary.options().stream().collect(java.util.stream.Collectors.toMap(TeacherSubjectDictionary.Option::code,TeacherSubjectDictionary.Option::name,(a,b)->a));
  return distinct.stream().map(key->{var value=metadata.get(key);String code=value==null?null:value.subjectCode();return new Summary(key.teacherUserId(),key.institutionOrganizationId(),code,code==null?null:names.get(code),counts.getOrDefault(key,0));}).toList();
 }
 @Transactional(isolation=Isolation.READ_COMMITTED) public TeacherSubjectStore.Metadata update(AuthenticatedUser actor,long teacher,long institution,String code,int version){require(actor,"TEACHER_UPDATE");if(version<0)throw new IllegalArgumentException("学科版本无效");if(users.findByIdForUpdate(teacher)==null)throw missing();guard(actor,teacher,institution);String subject=dictionary.validate(code);var previous=store.read(teacher,institution);if(previous.isEmpty()){if(version!=0)throw new IllegalStateException("教师学科已变化，请刷新");store.insert(teacher,institution,subject);}else if(previous.get().versionNo()!=version||!store.update(teacher,institution,subject,version))throw new IllegalStateException("教师学科已变化，请刷新");store.recordAudit(teacher,institution,version+1,actor.userId(),previous.map(TeacherSubjectStore.Metadata::subjectCode).orElse(null),subject);return store.read(teacher,institution).orElseThrow();}
 private static ResourceNotFoundException missing(){return new ResourceNotFoundException("教师或所属机构不存在或无权访问");}
}
