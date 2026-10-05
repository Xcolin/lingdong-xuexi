package com.lingdong.learning.exportjob.application;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.exportjob.infrastructure.config.ExportJobProperties;
import com.lingdong.learning.exportjob.infrastructure.persistence.StudentTaskExportMapper;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import com.lingdong.learning.user.domain.UserStatus;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
public class StudentTaskExportAccessService {
 private final UserMapper users; private final UserRoleMapper roles;
 private final PermissionDecisionService permissions; private final FeatureAccessService features;
 private final OrganizationDataScopeService organizations; private final StudentTaskExportMapper mapper;
 private final ExportJobProperties properties;
 public StudentTaskExportAccessService(UserMapper users, UserRoleMapper roles, PermissionDecisionService permissions,
  FeatureAccessService features, OrganizationDataScopeService organizations, StudentTaskExportMapper mapper, ExportJobProperties properties) {
  this.users=users; this.roles=roles; this.permissions=permissions; this.features=features; this.organizations=organizations; this.mapper=mapper; this.properties=properties;
 }
 public static String role(List<String> roles) {
  for (String role : List.of("ORG_ADMIN","TEACHER","PARENT")) if (roles.contains(role)) return role;
  // Legacy SQL uses ORG_ADMIN as the organization visibility mode, not as a granted role.
  return "ORG_ADMIN";
 }
 public StudentTaskVisibility require(long userId) {
  var user=users.findById(userId); if(user==null || user.status()!=UserStatus.ENABLED) throw denied();
  for (String feature:List.of("DATA_EXPORT","IMPORT_EXPORT_TEMPLATE_MANAGEMENT","ATTACHMENT_SERVICE","LEARNING_TASK_MANAGEMENT")) features.requireEnabled(feature,null);
  String role = role(roles.findEnabledRoleCodesByUserId(userId));
  for(String permission:List.of("STUDENT_TASK_REPORT_EXPORT",role.equals("PARENT")?"LEARNING_TASK_READ_MANAGED":"LEARNING_TASK_PROGRESS_READ"))
   if(!permissions.isAllowed(userId,PermissionClient.WEB,permission)) throw denied();
  var scope=organizations.resolve(userId);
  if (role.equals("ORG_ADMIN") && !scope.allOrganizations() && scope.rootPaths().isEmpty()) throw denied();
  return new StudentTaskVisibility(userId,role,role.equals("ORG_ADMIN") && scope.allOrganizations(),role.equals("ORG_ADMIN")?scope.rootPaths():List.of());
 }
 public List<com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointStudentOptionRow> students(long userId) {return mapper.findStudents(require(userId));}
 public Frozen freeze(CreateExportJobCommand command) {
  var current=require(command.requesterId());
  if(command.studentId()!=null && mapper.findStudents(current).stream().noneMatch(s->s.studentId().equals(command.studentId()))) throw denied();
  int maximum=properties.getStudentTaskMaxAssignments();
  if(maximum<1 || maximum>50_000) throw new IllegalStateException("冻结任务上限配置无效");
  var ids=mapper.findVisibleIds(current,command.studentId(),normalize(command.studentTaskSource()),normalize(command.studentTaskStatus()),
   command.startedAt()==null?null:command.startedAt().toLocalDate(),command.endedAt()==null?null:command.endedAt().toLocalDate(),maximum+1);
  if(ids.size()>maximum) throw new IllegalArgumentException("任务报表超过允许行数，请缩小日期或学生范围");
  return new Frozen(current.role(),List.copyOf(ids));
 }
 public void requireFrozen(long userId, ExportScopeSnapshot frozen) {
  var current=require(userId); var ids=frozen.studentTaskAssignmentIds();
  if(!current.role().equals(frozen.studentTaskRole()) || ids==null || ids.size()>50_000 || frozen.upperBound()<0 || ids.stream().anyMatch(id->id==null||id<=0||id>frozen.upperBound()) || ids.stream().distinct().count()!=ids.size()) throw denied();
  // 分批复核避免大型 IN；任何一条关系撤销都拒绝旧文件，而非交付部分过滤后的假快照。
  for(int start=0;start<ids.size();start+=500) {
   var page=ids.subList(start,Math.min(start+500,ids.size()));
   if(mapper.countVisibleIds(current,page)!=page.size()) throw denied();
  }
 }
 private static String normalize(String value) {return value==null||value.isBlank()?null:value.trim().toUpperCase(java.util.Locale.ROOT);}
 public record Frozen(String role,List<Long> ids) {}
 private static SystemOperationAccessDeniedException denied(){return new SystemOperationAccessDeniedException("学生任务报表权限或范围已失效");}
}
