package com.lingdong.learning.training;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.*;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.organization.application.*;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.student.application.InstitutionEnrollmentStateException;
import com.lingdong.learning.student.domain.*;
import com.lingdong.learning.student.infrastructure.persistence.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class TrainingService {
 public static final String CREATE="STUDENT_TRAINING_REQUEST_CREATE",REVIEW="STUDENT_TRAINING_REQUEST_REVIEW",MANAGE="STUDENT_TRAINING_PROGRAM_MANAGE";
 private final TrainingStore store;private final StudentMapper students;private final ParentStudentMapper parents;
 private final InstitutionEnrollmentMapper enrollment;private final StudentOrganizationMapper relationships;
 private final OrganizationMapper organizations;private final OrganizationAdminMapper admins;
 private final OrganizationDataScopeService scopes;private final PermissionDecisionService permissions;
 private final FeatureAccessService features;private final IdGenerator ids;private final Clock clock;private final ObjectMapper json;
 public TrainingService(TrainingStore store,StudentMapper students,ParentStudentMapper parents,InstitutionEnrollmentMapper enrollment,
 StudentOrganizationMapper relationships,OrganizationMapper organizations,OrganizationAdminMapper admins,OrganizationDataScopeService scopes,
 PermissionDecisionService permissions,FeatureAccessService features,IdGenerator ids,Clock clock,ObjectMapper json){
  this.store=store;this.students=students;this.parents=parents;this.enrollment=enrollment;this.relationships=relationships;this.organizations=organizations;
  this.admins=admins;this.scopes=scopes;this.permissions=permissions;this.features=features;this.ids=ids;this.clock=clock;this.json=json;
 }
 public record Page(List<Map<String,Object>> items,long total,int page,int pageSize){}
 static void validatePage(int page,int pageSize){if(page<1||pageSize<1||pageSize>100||(long)(page-1)*pageSize>Integer.MAX_VALUE)throw new IllegalArgumentException("分页参数无效，pageSize为1至100");}
 private static Map<String,Object> args(Object... values){var result=new HashMap<String,Object>();for(int i=0;i<values.length;i+=2)result.put((String)values[i],values[i+1]);return result;}
 private static Long id(Map<String,Object> row,String key){return Long.valueOf(row.get(key).toString());}
 private static final String PROGRAM_FROM=" FROM org_training_program_registration r JOIN sys_organization p ON p.id=r.program_organization_id JOIN sys_organization i ON i.id=r.institution_organization_id LEFT JOIN org_course_category_metadata metadata ON metadata.organization_id=p.parent_id";
 private static final String PROGRAM_COLUMNS="SELECT r.program_organization_id,r.institution_organization_id,p.organization_name program_name,r.program_type,r.enrollable,metadata.description,metadata.stage_codes,metadata.subject_code";
 private static final String REQUEST_FROM=" FROM edu_institution_training_request r JOIN edu_student s ON s.id=r.student_id JOIN sys_organization i ON i.id=r.institution_organization_id JOIN sys_organization p ON p.id=r.program_organization_id";
 private static final String REQUEST_COLUMNS="SELECT r.id,r.student_id,s.student_name,r.family_organization_id,r.institution_organization_id,i.organization_name institution_name,r.program_organization_id,p.organization_name program_name,r.request_type,r.reason,r.status,r.requested_by,r.created_at,r.reviewed_by,r.reviewed_at,r.review_reason";
 private static final String PARTICIPATION_FROM=" FROM edu_training_participation t JOIN edu_institution_training_request r ON r.id=t.training_request_id JOIN edu_student s ON s.id=t.student_id JOIN sys_organization i ON i.id=t.institution_organization_id JOIN sys_organization p ON p.id=t.program_organization_id";
 private static final String PARTICIPATION_COLUMNS="SELECT t.id,t.training_request_id,t.student_id,s.student_name,r.family_organization_id,t.institution_organization_id,i.organization_name institution_name,t.program_organization_id,p.organization_name program_name,t.status,t.joined_at,t.left_at";
 private static final String CURRENT_PROGRAM=" AND p.organization_type='CLASS' AND p.status='ENABLED' AND p.effective_status='ENABLED' AND i.status='ENABLED' AND i.effective_status='ENABLED' AND LEFT(p.organization_path,CHAR_LENGTH(i.organization_path))=i.organization_path";
 @Transactional(readOnly=true)
 public List<Map<String,Object>> institutions(AuthenticatedUser actor,Long studentId,Long familyId){
  require(actor,CREATE,null);familyStudent(actor,studentId,familyId);
  var result=new ArrayList<Map<String,Object>>();
  for(Long institutionId:new LinkedHashSet<>(relationships.findActiveEnrollmentOrganizationIds(studentId))){
   Organization node=organizations.findById(institutionId);
   if(!OrganizationHierarchy.isInstitutionRoot(node)||!OrganizationOperationalStatusService.isOperational(node))continue;
   if(!features.isEnabled("STUDENT_ORGANIZATION_RELATIONSHIP",institutionId))continue;
   if(!hasActiveClass(studentId,institutionId))continue;
   result.add(args("id",institutionId.toString(),"name",node.name(),"typeCode",node.typeCode()));
  }
  result.sort(Comparator.comparing(row->row.get("id").toString()));return result;
 }
 private Map<String,Object> programView(Map<String,Object> row){
  Object stages=row.get("stageCodes");try{row.put("stageCodes",stages==null?List.of():json.readValue(stages.toString(),List.class));}catch(Exception e){throw new IllegalStateException("课程分类学段数据无效",e);}return row;
 }
 @Transactional(readOnly=true)
 public Page programCandidates(AuthenticatedUser actor,Long institutionId,int page,int pageSize){
  validatePage(page,pageSize);require(actor,MANAGE,institutionId);institution(institutionId);scope(actor,institutionId);
  String from=" FROM sys_organization p JOIN sys_organization i ON i.id=:institution";
  String where=" WHERE p.id<>i.id"+CURRENT_PROGRAM;
  return page("SELECT p.id program_organization_id,p.organization_name program_name",from,where,"p.organization_path,p.id",args("institution",institutionId),page,pageSize);
 }
 @Transactional(readOnly=true)
 public Page programs(AuthenticatedUser actor,Long institutionId,Long studentId,Long familyId,int page,int pageSize,boolean management){
  validatePage(page,pageSize);institution(institutionId);require(actor,management?MANAGE:CREATE,institutionId);
  if(management)scope(actor,institutionId);else{familyStudent(actor,studentId,familyId);inClass(studentId,institutionId);}
  String where=" WHERE r.institution_organization_id=:institution"+(management?"":" AND r.enrollable='ENABLED'"+CURRENT_PROGRAM);
  var params=args("institution",institutionId);Page result=page(PROGRAM_COLUMNS,PROGRAM_FROM,where,"r.program_organization_id",params,page,pageSize);
  for(var row:result.items())programView(row);
  return result;
 }
 @Transactional
 public Map<String,Object> register(AuthenticatedUser actor,Long programId,Long institutionId,String type,String enabled){
  require(actor,MANAGE,institutionId);scope(actor,institutionId);type(type);if(!Set.of("ENABLED","DISABLED").contains(Objects.toString(enabled,"")))throw new IllegalArgumentException("可报名状态无效");
  organizations.findByIdForUpdate(programId);requireProgramNode(programId,institutionId);
  var params=args("program",programId,"institution",institutionId,"type",type,"enabled",enabled,"actor",actor.userId(),"now",LocalDateTime.now(clock));
  var existing=store.one("SELECT institution_organization_id FROM org_training_program_registration WHERE program_organization_id=:program FOR UPDATE",params);
  if(existing!=null&&!institutionId.equals(id(existing,"institutionOrganizationId")))throw conflict("项目原所属机构已变更，请先处理历史登记");
  if(existing==null)store.update("INSERT INTO org_training_program_registration(program_organization_id,institution_organization_id,program_type,enrollable,updated_by,updated_at) VALUES(:program,:institution,:type,:enabled,:actor,:now)",params);
  else store.update("UPDATE org_training_program_registration SET program_type=:type,enrollable=:enabled,updated_by=:actor,updated_at=:now WHERE program_organization_id=:program",params);
  return programView(store.one(PROGRAM_COLUMNS+PROGRAM_FROM+" WHERE r.program_organization_id=:program",params));
 }
 @Transactional
 public Map<String,Object> create(AuthenticatedUser actor,Long studentId,Long familyId,Long institutionId,Long programId,String requestType,String reason){
  require(actor,CREATE,institutionId);type(requestType);text(reason,false);familyStudent(actor,studentId,familyId);
  Student student=students.findByIdForUpdate(studentId);if(student==null||student.status()!=StudentStatus.ENABLED)throw missing();
  inClass(studentId,institutionId);var program=available(programId,institutionId);if(!requestType.equals(program.get("programType")))throw new IllegalArgumentException("申请类型与培训项目不一致");
  var params=args("student",studentId,"family",familyId,"institution",institutionId,"program",programId,"type",requestType,"reason",reason,"actor",actor.userId(),"now",LocalDateTime.now(clock),"id",ids.nextId());
  noActive(params);
  if(store.count("SELECT COUNT(*) FROM edu_institution_training_request WHERE student_id=:student AND program_organization_id=:program AND status='PENDING'",params)>0)throw conflict("已有待审核申请");
  try{store.update("INSERT INTO edu_institution_training_request(id,student_id,family_organization_id,institution_organization_id,program_organization_id,request_type,reason,status,pending_guard,requested_by,created_at) VALUES(:id,:student,:family,:institution,:program,:type,:reason,'PENDING',1,:actor,:now)",params);}catch(DuplicateKeyException e){throw conflict("已有待审核申请");}
  return requestRow((Long)params.get("id"),false);
 }
 @Transactional(readOnly=true)
 public Page requests(AuthenticatedUser actor,Long institutionId,Long familyId,Long studentId,String status,int page,int pageSize){
  if(status!=null&&!Set.of("PENDING","APPROVED","REJECTED","WITHDRAWN").contains(status))throw new IllegalArgumentException("申请状态无效");
  var params=args("institution",institutionId,"family",familyId,"student",studentId,"status",status,"actor",actor==null?null:actor.userId());
  String where=listScope(actor,institutionId,familyId,studentId,params)+" AND (:status IS NULL OR r.status=:status)";
  return page(REQUEST_COLUMNS,REQUEST_FROM,where,"r.created_at DESC,r.id DESC",params,page,pageSize);
 }
 @Transactional(readOnly=true)
 public Map<String,Object> detail(AuthenticatedUser actor,Long requestId){var row=requestRow(requestId,false);access(actor,row);return row;}
 @Transactional
 public Map<String,Object> withdraw(AuthenticatedUser actor,Long requestId){
  require(actor,CREATE,null);var row=requestRow(requestId,true);features.requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP",id(row,"institutionOrganizationId"));familyRequest(actor,row);pending(row);
  if(store.update("UPDATE edu_institution_training_request SET status='WITHDRAWN',pending_guard=NULL WHERE id=:id AND status='PENDING'",args("id",requestId))!=1)throw conflict("申请状态已变更");return requestRow(requestId,false);
 }
 @Transactional
 public Map<String,Object> review(AuthenticatedUser actor,Long requestId,boolean approve,String reason){
  require(actor,REVIEW,null);text(reason,!approve);var row=requestRow(requestId,true);Long institutionId=id(row,"institutionOrganizationId");
  scope(actor,institutionId);features.requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP",institutionId);pending(row);
  if(approve){
   Long studentId=id(row,"studentId");var student=students.findByIdForUpdate(studentId);if(student==null||student.status()!=StudentStatus.ENABLED)throw missing();
   Long familyId=id(row,"familyOrganizationId"),requester=id(row,"requestedBy");
   familyStudentIdentity(requester,student,familyId);inClass(studentId,institutionId);
   var program=available(id(row,"programOrganizationId"),institutionId);
   if(!Objects.equals(program.get("programType"),row.get("requestType")))throw conflict("培训项目类型已变更，请重新申请");
  }
  var params=args("id",requestId,"status",approve?"APPROVED":"REJECTED","actor",actor.userId(),"now",LocalDateTime.now(clock),"reason",reason,"student",id(row,"studentId"),"program",id(row,"programOrganizationId"),"institution",institutionId,"participation",ids.nextId());
  if(approve)noActive(params);
  if(store.update("UPDATE edu_institution_training_request SET status=:status,pending_guard=NULL,reviewed_by=:actor,reviewed_at=:now,review_reason=:reason WHERE id=:id AND status='PENDING'",params)!=1)throw conflict("申请已处理");
  if(approve)try{store.update("INSERT INTO edu_training_participation(id,training_request_id,student_id,program_organization_id,institution_organization_id,status,active_guard,joined_at) VALUES(:participation,:id,:student,:program,:institution,'ACTIVE',1,:now)",params);}catch(DuplicateKeyException e){throw conflict("孩子已参与该项目");}
  return requestRow(requestId,false);
 }
 @Transactional(readOnly=true)
 public Page participations(AuthenticatedUser actor,Long institutionId,Long familyId,Long studentId,String status,int page,int pageSize){
  if(status!=null&&!Set.of("ACTIVE","LEFT").contains(status))throw new IllegalArgumentException("参与状态无效");
  var params=args("institution",institutionId,"family",familyId,"student",studentId,"status",status,"actor",actor==null?null:actor.userId());
  String where=listScope(actor,institutionId,familyId,studentId,params)+" AND (:status IS NULL OR t.status=:status)";
  return page(PARTICIPATION_COLUMNS,PARTICIPATION_FROM,where,"t.joined_at DESC,t.id DESC",params,page,pageSize);
 }
 private Page page(String columns,String from,String where,String order,Map<String,Object> params,int page,int pageSize){
  validatePage(page,pageSize);long total=store.count("SELECT COUNT(*)"+from+where,params);params.put("limit",pageSize);params.put("offset",(page-1)*pageSize);
  return new Page(store.rows(columns+from+where+" ORDER BY "+order+" LIMIT :limit OFFSET :offset",params),total,page,pageSize);
 }
 private String listScope(AuthenticatedUser actor,Long institutionId,Long familyId,Long studentId,Map<String,Object> params){
  if(familyId==null){require(actor,REVIEW,institutionId);institution(institutionId);scope(actor,institutionId);return " WHERE r.institution_organization_id=:institution AND (:student IS NULL OR r.student_id=:student)";}
  require(actor,CREATE,institutionId);family(actor,familyId);if(studentId!=null)familyStudent(actor,studentId,familyId);
  // Authorization happens before COUNT/LIMIT; no collect-all and no post-pagination filtering.
  return " WHERE r.family_organization_id=:family AND (:institution IS NULL OR r.institution_organization_id=:institution) AND (:student IS NULL OR r.student_id=:student) AND s.status='ENABLED'"
   +" AND EXISTS(SELECT 1 FROM sys_user_organization child_member WHERE child_member.user_id=s.student_user_id AND child_member.organization_id=:family)"
   +" AND EXISTS(SELECT 1 FROM edu_parent_student guardian WHERE guardian.student_id=s.id AND guardian.parent_user_id=:actor AND guardian.status='ACTIVE')"
   +" AND (r.requested_by=:actor OR EXISTS(SELECT 1 FROM sys_organization_admin administrator WHERE administrator.user_id=:actor AND administrator.organization_id=:family))";
 }
 private Map<String,Object> requestRow(Long requestId,boolean lock){var row=store.one(REQUEST_COLUMNS+REQUEST_FROM+" WHERE r.id=:id"+(lock?" FOR UPDATE":""),args("id",requestId));if(row==null)throw missing();return row;}
 private void access(AuthenticatedUser actor,Map<String,Object> row){if(allowed(actor,REVIEW)&&scopes.canAccess(actor.userId(),id(row,"institutionOrganizationId"))){require(actor,REVIEW,id(row,"institutionOrganizationId"));return;}require(actor,CREATE,id(row,"institutionOrganizationId"));familyRequest(actor,row);}
 private void familyRequest(AuthenticatedUser actor,Map<String,Object> row){Long familyId=id(row,"familyOrganizationId");familyStudent(actor,id(row,"studentId"),familyId);if(!actor.userId().equals(id(row,"requestedBy"))&&!admins.exists(actor.userId(),familyId))throw denied();}
 private Student familyStudent(AuthenticatedUser actor,Long studentId,Long familyId){family(actor,familyId);Student student=studentId==null?null:students.findById(studentId);if(student==null||student.status()!=StudentStatus.ENABLED)throw missing();familyStudentIdentity(actor.userId(),student,familyId);return student;}
 private void familyStudentIdentity(Long actorId,Student student,Long familyId){
  Organization family=organization(familyId);if(!"FAMILY".equals(family.typeCode()))throw denied();
  if(!enrollment.familyMember(actorId,familyId)||!enrollment.familyMember(student.studentUserId(),familyId)||!parents.existsActiveByParentAndStudent(actorId,student.id()))throw denied();
 }
 private void family(AuthenticatedUser actor,Long familyId){Organization family=organization(familyId);if(!"FAMILY".equals(family.typeCode())||!enrollment.familyMember(actor.userId(),familyId))throw denied();Long selected=SelectedOrganizationContext.organizationId();if(selected!=null&&!selected.equals(familyId))throw denied();}
 private void inClass(Long studentId,Long institutionId){institution(institutionId);if(!relationships.findActiveEnrollmentOrganizationIds(studentId).contains(institutionId)||!hasActiveClass(studentId,institutionId))throw denied();}
 private boolean hasActiveClass(Long studentId,Long institutionId){return relationships.findActiveClassOrganizationIdsInEnrollment(studentId,institutionId).stream().anyMatch(classId->{Organization node=organizations.findById(classId);return OrganizationOperationalStatusService.isOperational(node)&&institutionId.equals(Optional.ofNullable(OrganizationHierarchy.schoolOf(node,organizations)).map(Organization::id).orElse(null));});}
 private Organization institution(Long institutionId){var node=organization(institutionId);if(!OrganizationHierarchy.isInstitutionRoot(node))throw new IllegalArgumentException("请选择教育机构");return node;}
 private Organization organization(Long id){var node=id==null?null:organizations.findById(id);if(!OrganizationOperationalStatusService.isOperational(node))throw missing();return node;}
 private void requireProgramNode(Long programId,Long institutionId){institution(institutionId);var node=organization(programId);var school=OrganizationHierarchy.schoolOf(node,organizations);if(!"CLASS".equals(node.typeCode())||school==null||!institutionId.equals(school.id()))throw denied();}
 private Map<String,Object> available(Long programId,Long institutionId){requireProgramNode(programId,institutionId);var row=store.one("SELECT program_type,enrollable,institution_organization_id FROM org_training_program_registration WHERE program_organization_id=:program FOR UPDATE",args("program",programId));if(row==null||!"ENABLED".equals(row.get("enrollable"))||!institutionId.equals(id(row,"institutionOrganizationId")))throw conflict("培训项目未登记或已停止报名");return row;}
 private void noActive(Map<String,Object> params){if(store.count("SELECT COUNT(*) FROM edu_training_participation WHERE student_id=:student AND program_organization_id=:program AND status='ACTIVE'",params)>0)throw conflict("孩子已参与该项目");}
 private void scope(AuthenticatedUser actor,Long institutionId){if(!scopes.canAccess(actor.userId(),institutionId))throw denied();}
 private boolean allowed(AuthenticatedUser actor,String permission){return actor!=null&&actor.userId()!=null&&actor.clientType()!=null&&permissions.isAllowed(actor.userId(),PermissionClient.valueOf(actor.clientType().name()),permission);}
 private void require(AuthenticatedUser actor,String permission,Long institutionId){if(!allowed(actor,permission))throw denied();features.requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP",institutionId);}
 private void pending(Map<String,Object> row){if(!"PENDING".equals(row.get("status")))throw conflict("申请已处理");}
 private void type(String type){if(type==null||!Set.of("OFFICIAL_COURSE","CLUB_ACTIVITY").contains(type))throw new IllegalArgumentException("培训类型无效");}
 private void text(String text,boolean required){if(required&&(text==null||text.isBlank()))throw new IllegalArgumentException("请填写拒绝原因");if(text!=null&&text.length()>200)throw new IllegalArgumentException("说明不能超过200字");}
 private SystemOperationAccessDeniedException denied(){return new SystemOperationAccessDeniedException("无权操作该家庭、孩子或机构培训数据");}
 private ResourceNotFoundException missing(){return new ResourceNotFoundException("培训项目、孩子或申请不存在或已停用");}
 private InstitutionEnrollmentStateException conflict(String message){return new InstitutionEnrollmentStateException(message);}
}


