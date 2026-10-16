package com.lingdong.learning.student.application.transfer;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.student.infrastructure.persistence.*;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.application.OrganizationOperationalStatusService;
import com.lingdong.learning.organization.application.OrganizationTreeOrder;
import com.lingdong.learning.feature.application.FeatureAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class TransferApplicationOptionsService {
 public record Institution(String id,String name,String typeCode,List<String> classNames){}
 public record Options(String studentId,String studentName,List<Institution> institutions){}
 private final TransferApplicationAccess access;private final StudentMapper students;private final StudentOrganizationMapper enrollments;private final OrganizationMapper organizations;private final FeatureAccessService features;
 public TransferApplicationOptionsService(TransferApplicationAccess access,StudentMapper students,StudentOrganizationMapper enrollments,OrganizationMapper organizations,FeatureAccessService features){this.access=access;this.students=students;this.enrollments=enrollments;this.organizations=organizations;this.features=features;}
 @Transactional(readOnly=true) public Options familyOptions(AuthenticatedUser actor,long studentId,long familyId){
  features.requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP",null);access.requireFamilyContext(actor,studentId,familyId,"STUDENT_TRANSFER_REQUEST_CREATE");
  var student=students.findById(studentId);List<Organization> roots=enrollments.findActiveEnrollmentOrganizationIds(studentId).stream().map(organizations::findById).filter(Objects::nonNull).filter(o->Set.of("SCHOOL","TRAINING_INSTITUTION").contains(o.typeCode())&&OrganizationOperationalStatusService.isOperational(o)).toList();
  return buildOptions(studentId,roots);
 }
 private Options buildOptions(long studentId,List<Organization> roots){
  var student=students.findById(studentId);
  Map<Long,Organization> tree=new HashMap<>();Map<Long,List<Organization>> classes=new HashMap<>();
  for(var root:roots){addAncestors(root,tree);var rows=enrollments.findActiveClassOrganizationIdsInEnrollment(studentId,root.id()).stream().map(organizations::findById).filter(Objects::nonNull).filter(o->"CLASS".equals(o.typeCode())&&o.path().startsWith(root.path())).toList();classes.put(root.id(),rows);rows.forEach(o->addAncestors(o,tree));}
  var order=OrganizationTreeOrder.comparator(new ArrayList<>(tree.values()));
  return new Options(Long.toString(student.id()),student.studentName(),roots.stream().sorted(order).map(root->new Institution(Long.toString(root.id()),root.name(),root.typeCode(),classes.get(root.id()).stream().sorted(order).map(Organization::name).toList())).toList());
 }
 @Transactional(readOnly=true) public Options institutionOptions(AuthenticatedUser actor,long studentId,long sourceId){
  features.requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP",null);access.requireInstitutionOptions(actor,studentId,sourceId);
  return buildOptions(studentId,List.of(organizations.findById(sourceId)));
 }
 private void addAncestors(Organization node,Map<Long,Organization> tree){
  Set<Long> seen=new HashSet<>();while(node!=null&&seen.add(node.id())){if(tree.putIfAbsent(node.id(),node)!=null)break;node=node.parentId()==null?null:organizations.findById(node.parentId());}
 }
}
