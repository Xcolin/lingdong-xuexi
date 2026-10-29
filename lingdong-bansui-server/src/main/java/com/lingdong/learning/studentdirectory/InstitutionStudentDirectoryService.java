package com.lingdong.learning.studentdirectory;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.SelectedOrganizationContext;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.organization.application.OrganizationHierarchy;
import com.lingdong.learning.organization.application.OrganizationOperationalStatusService;
import com.lingdong.learning.organization.application.OrganizationTreeOrder;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.student.application.StudentManagementAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly=true)
public class InstitutionStudentDirectoryService {
 private final InstitutionStudentDirectoryDao dao;
 private final OrganizationMapper organizations;
 private final StudentManagementAccessService access;
 private final FeatureAccessService features;
 public InstitutionStudentDirectoryService(InstitutionStudentDirectoryDao dao,OrganizationMapper organizations,StudentManagementAccessService access,FeatureAccessService features){this.dao=dao;this.organizations=organizations;this.access=access;this.features=features;}
 public record Item(String studentId,String studentName,String studentNumber,String status,String gradeNames,String classNames,String classOrganizationId){}
 public record Page(List<Item> items,int page,int pageSize,long total){}
 public record GradeOption(String id,String name){}
 public record ClassOption(String id,String name,String gradeId){}
 public record Options(List<GradeOption> grades,List<ClassOption> classes){}
 private record Context(Organization institution,List<Organization> all,List<Organization> classes,boolean institutionScope){}
 private ResourceNotFoundException missing(){return new ResourceNotFoundException("机构或学生不存在或不可访问");}
 private Context context(AuthenticatedUser actor,Long institutionId){
  access.require(actor,"STUDENT_READ"); features.requireEnabled("CLASS_MANAGEMENT",null);
  Organization institution=institutionId==null?null:organizations.findById(institutionId);
  if(!OrganizationHierarchy.isInstitutionRoot(institution)||!OrganizationOperationalStatusService.isOperational(institution))throw missing();
  Organization selected=SelectedOrganizationContext.organization();
  if(selected!=null){var selectedInstitution=OrganizationHierarchy.schoolOf(selected,organizations);if(selectedInstitution==null||!institution.id().equals(selectedInstitution.id()))throw missing();}
  var scope=access.scope(actor);if(scope==null)throw missing();
  var all=organizations.findAll();
  var classes=all.stream().filter(o->"CLASS".equals(o.typeCode())&&OrganizationOperationalStatusService.isOperational(o)&&o.path().startsWith(institution.path())&&scope.allows(o))
    .filter(o->{var root=OrganizationHierarchy.schoolOf(o,organizations);return root!=null&&root.id().equals(institution.id());})
    .sorted(OrganizationTreeOrder.comparator(all)).toList();
  boolean full=scope.allows(institution);
  if(!full&&classes.isEmpty())throw missing();
  features.requireEnabled("CLASS_MANAGEMENT",institution.id());
  return new Context(institution,all,classes,full);
 }
 public Options options(AuthenticatedUser actor,Long institutionId){return options(context(actor,institutionId));}
 private Options options(Context c){
  var grades=c.all().stream().filter(o->"GRADE".equals(o.typeCode())&&OrganizationOperationalStatusService.isOperational(o)&&o.path().startsWith(c.institution().path()))
   .filter(o->c.institutionScope()||c.classes().stream().anyMatch(k->k.path().startsWith(o.path())))
   .sorted(OrganizationTreeOrder.comparator(c.all())).map(o->new GradeOption(o.id().toString(),o.name())).toList();
  var classes=c.classes().stream().map(o->{var grade=OrganizationHierarchy.ancestor(o,"GRADE",organizations);return new ClassOption(o.id().toString(),o.name(),grade==null?null:grade.id().toString());}).toList();
  return new Options(grades,classes);
 }
 public Page list(AuthenticatedUser actor,Long institutionId,String keyword,Long gradeId,Long classId,String status,int page,int pageSize){
  if(page<1||pageSize<1||pageSize>100||(long)(page-1)*pageSize>Integer.MAX_VALUE)throw new IllegalArgumentException("分页参数不合法");
  if(!Set.of("ALL","ACTIVE","INACTIVE").contains(status))throw new IllegalArgumentException("学员状态不合法");
  var c=context(actor,institutionId);List<Organization> filtered=c.classes();
  if(gradeId!=null){var grade=c.all().stream().filter(o->o.id().equals(gradeId)&&"GRADE".equals(o.typeCode())&&OrganizationOperationalStatusService.isOperational(o)&&o.path().startsWith(c.institution().path())).findFirst().orElseThrow(this::missing);filtered=filtered.stream().filter(o->o.path().startsWith(grade.path())).toList();}
  if(classId!=null){if(c.classes().stream().noneMatch(o->o.id().equals(classId)))throw missing();filtered=filtered.stream().filter(o->o.id().equals(classId)).toList();}
  var query=new InstitutionStudentDirectoryDao.Query(institutionId,filtered.stream().map(Organization::id).toList(),c.institutionScope()&&gradeId==null&&classId==null,keyword==null?null:keyword.trim(),null,status);
  long total=dao.count(query);var rows=dao.page(query,(page-1)*pageSize,pageSize);
  return new Page(items(c,rows),page,pageSize,total);
 }
 public Item detail(AuthenticatedUser actor,Long institutionId,Long studentId){
  var c=context(actor,institutionId);var query=new InstitutionStudentDirectoryDao.Query(institutionId,c.classes().stream().map(Organization::id).toList(),c.institutionScope(),null,studentId,"ALL");
  var rows=dao.page(query,0,1);if(rows.isEmpty())throw missing();return items(c,rows).get(0);
 }
 private List<Item> items(Context c,List<InstitutionStudentDirectoryDao.Row> rows){
  var memberships=dao.classes(rows.stream().map(InstitutionStudentDirectoryDao.Row::studentId).toList(),c.classes().stream().map(Organization::id).toList());
  return rows.stream().map(row->{
   var ids=memberships.stream().filter(m->m.studentId().equals(row.studentId())).map(InstitutionStudentDirectoryDao.ClassMembership::classId).collect(java.util.stream.Collectors.toSet());
   var classes=c.classes().stream().filter(o->ids.contains(o.id())).toList();
   var grades=classes.stream().map(o->OrganizationHierarchy.ancestor(o,"GRADE",organizations)).filter(Objects::nonNull).map(Organization::name).distinct().toList();
   return new Item(row.studentId().toString(),row.studentName(),row.studentNumber(),row.status(),String.join("、",grades),String.join("、",classes.stream().map(Organization::name).toList()),classes.size()==1?classes.get(0).id().toString():null);
  }).toList();
 }
}
