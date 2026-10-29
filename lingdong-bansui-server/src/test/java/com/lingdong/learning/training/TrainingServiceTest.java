package com.lingdong.learning.training;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.student.application.InstitutionEnrollmentStateException;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.infrastructure.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrainingServiceTest {
 JdbcTemplate db;DriverManagerDataSource ds;TrainingService service;TrainingStore store;
 StudentMapper students=mock(StudentMapper.class);ParentStudentMapper parents=mock(ParentStudentMapper.class);
 InstitutionEnrollmentMapper enrollment=mock(InstitutionEnrollmentMapper.class);StudentOrganizationMapper relationships=mock(StudentOrganizationMapper.class);
 OrganizationMapper organizations=mock(OrganizationMapper.class);OrganizationAdminMapper admins=mock(OrganizationAdminMapper.class);
 OrganizationDataScopeService scopes=mock(OrganizationDataScopeService.class);PermissionDecisionService permissions=mock(PermissionDecisionService.class);
 FeatureAccessService features=mock(FeatureAccessService.class);AtomicLong sequence=new AtomicLong(1000);
 AuthenticatedUser parent=new AuthenticatedUser(1L,1L,"parent","家长",AuthClientType.MINIAPP,List.of());
 AuthenticatedUser reviewer=new AuthenticatedUser(3L,3L,"reviewer","机构管理员",AuthClientType.WEB,List.of());
 AuthenticatedUser otherParent=new AuthenticatedUser(4L,4L,"other","另一监护人",AuthClientType.MINIAPP,List.of());
 @BeforeEach void setup(){
  ds=new DriverManagerDataSource("jdbc:h2:mem:training_service_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","sa","");db=new JdbcTemplate(ds);
  db.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");
  db.execute("CREATE TABLE sys_organization(id BIGINT PRIMARY KEY,organization_name VARCHAR(100),organization_type VARCHAR(30),organization_path VARCHAR(200),status VARCHAR(20),effective_status VARCHAR(20),parent_id BIGINT)");
  db.execute("CREATE TABLE edu_student(id BIGINT PRIMARY KEY,student_name VARCHAR(100),student_user_id BIGINT,status VARCHAR(20))");
  db.execute("CREATE TABLE sys_permission(id BIGINT PRIMARY KEY,permission_code VARCHAR(100),permission_name VARCHAR(200),resource_type VARCHAR(20),client_type VARCHAR(20),parent_id BIGINT,status VARCHAR(20))");
  db.execute("CREATE TABLE sys_menu(id BIGINT PRIMARY KEY,code VARCHAR(100),name VARCHAR(200),type VARCHAR(20),parent_id BIGINT,permission_code VARCHAR(100),grantable INT,sort_order INT,status VARCHAR(20),version INT)");
  db.execute("CREATE TABLE sys_role(id BIGINT PRIMARY KEY,role_code VARCHAR(100),status VARCHAR(20))");
  db.execute("CREATE TABLE sys_role_permission(id BIGINT PRIMARY KEY,role_id BIGINT,permission_id BIGINT,effect VARCHAR(20))");
  db.execute("CREATE TABLE sys_user_permission(id BIGINT PRIMARY KEY,user_id BIGINT,permission_id BIGINT,effect VARCHAR(20))");
  db.execute("CREATE TABLE sys_user_organization(user_id BIGINT,organization_id BIGINT)");
  db.execute("CREATE TABLE edu_parent_student(parent_user_id BIGINT,student_id BIGINT,status VARCHAR(20))");
  db.execute("CREATE TABLE sys_organization_admin(user_id BIGINT,organization_id BIGINT)");
  db.execute("CREATE TABLE org_course_category_metadata(organization_id BIGINT,stage_codes VARCHAR(200),subject_code VARCHAR(30),description VARCHAR(100))");
  for(long user=1;user<=4;user++)db.update("INSERT INTO sys_user VALUES(?)",user);
  node(10,null,"FAMILY","家庭","/10/");node(100,null,"TRAINING_INSTITUTION","科学培训机构","/100/");node(101,null,"TRAINING_INSTITUTION","音乐培训机构","/101/");
  node(200,100L,"CLASS","科学探究","/100/200/");node(201,100L,"CLASS","原在读班","/100/201/");node(202,101L,"CLASS","合唱社团","/101/202/");node(203,101L,"CLASS","音乐在读班","/101/203/");
  db.update("INSERT INTO edu_student VALUES(30,'小明',2,'ENABLED')");
  for(long user:List.of(1L,2L,4L))db.update("INSERT INTO sys_user_organization VALUES(?,10)",user);
  db.update("INSERT INTO edu_parent_student VALUES(1,30,'ACTIVE'),(4,30,'ACTIVE')");
  new ResourceDatabasePopulator(new ClassPathResource("db/migration/V150__institution_training.sql")).execute(ds);
  db.update("INSERT INTO org_training_program_registration(program_organization_id,institution_organization_id,program_type) VALUES(200,100,'OFFICIAL_COURSE'),(202,101,'CLUB_ACTIVITY')");
  Student student=Student.create(30L,"小明","PRIMARY",2L);when(students.findById(30L)).thenReturn(student);when(students.findByIdForUpdate(30L)).thenReturn(student);
  when(enrollment.familyMember(anyLong(),eq(10L))).thenReturn(true);when(parents.existsActiveByParentAndStudent(anyLong(),eq(30L))).thenReturn(true);
  when(relationships.findActiveEnrollmentOrganizationIds(30L)).thenReturn(List.of(100L,101L));
  when(relationships.findActiveClassOrganizationIdsInEnrollment(30L,100L)).thenReturn(List.of(201L));when(relationships.findActiveClassOrganizationIdsInEnrollment(30L,101L)).thenReturn(List.of(203L));
  when(permissions.isAllowed(1L,PermissionClient.MINIAPP,TrainingService.CREATE)).thenReturn(true);when(permissions.isAllowed(4L,PermissionClient.MINIAPP,TrainingService.CREATE)).thenReturn(true);
  when(permissions.isAllowed(3L,PermissionClient.WEB,TrainingService.REVIEW)).thenReturn(true);when(permissions.isAllowed(3L,PermissionClient.WEB,TrainingService.MANAGE)).thenReturn(true);
  when(scopes.canAccess(3L,100L)).thenReturn(true);when(scopes.canAccess(3L,101L)).thenReturn(true);
  when(features.isEnabled(eq("STUDENT_ORGANIZATION_RELATIONSHIP"),anyLong())).thenReturn(true);
  store=spy(new TrainingStore(db));service=proxied(store);
 }
 void node(long id,Long parent,String type,String name,String path){
  var node=Organization.create(id,parent,parent==null?"ROOT":parent.toString(),"n"+id,name,type,path,0,null);
  when(organizations.findById(id)).thenReturn(node);when(organizations.findByIdForUpdate(id)).thenReturn(node);
  db.update("INSERT INTO sys_organization VALUES(?,?,?,?, 'ENABLED','ENABLED',?)",id,name,type,path,parent);
 }
 TrainingService proxied(TrainingStore selected){
  var target=new TrainingService(selected,students,parents,enrollment,relationships,organizations,admins,scopes,permissions,features,sequence::incrementAndGet,Clock.systemUTC(),new ObjectMapper());
  var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),new AnnotationTransactionAttributeSource()));return (TrainingService)proxy.getProxy();
 }
 long create(){return Long.parseLong(service.create(parent,30L,10L,100L,200L,"OFFICIAL_COURSE","孩子喜欢科学").get("id").toString());}
 @Test void approvalCreatesIndependentParticipationAndCannotEnrollAgain(){
  long id=create();service.review(reviewer,id,true,"同意");
  assertEquals("APPROVED",service.detail(parent,id).get("status"));
  assertEquals(1,service.participations(parent,null,10L,30L,null,1,20).total());
  assertThrows(InstitutionEnrollmentStateException.class,this::create);
  verify(relationships,never()).insertClass(anyLong(),anyLong(),anyLong());verify(relationships,never()).deactivateActiveClasses(anyLong());
 }
 @Test void sameChildHasIndependentParticipationAtTwoInstitutions(){
  service.review(reviewer,create(),true,null);
  var second=service.create(parent,30L,10L,101L,202L,"CLUB_ACTIVITY",null);service.review(reviewer,Long.valueOf(second.get("id").toString()),true,null);
  assertEquals(2,service.participations(parent,null,10L,30L,null,1,20).total());
  assertEquals(1,service.participations(reviewer,100L,null,30L,null,1,20).total());
  assertEquals(1,service.participations(reviewer,101L,null,30L,null,1,20).total());
 }
 @Test void pendingIsUniqueWithdrawalKeepsHistoryAndAllowsNewRequest(){
  long id=create();assertThrows(InstitutionEnrollmentStateException.class,this::create);service.withdraw(parent,id);
  assertEquals("WITHDRAWN",service.detail(parent,id).get("status"));assertNotEquals(id,create());assertEquals(2,service.requests(parent,null,10L,30L,null,1,20).total());
 }
 @Test void onlyApplicantOrFamilyAdministratorMayWithdrawEvenForAnotherGuardian(){
  long id=create();assertThrows(SystemOperationAccessDeniedException.class,()->service.withdraw(otherParent,id));
  assertThrows(SystemOperationAccessDeniedException.class,()->service.detail(otherParent,id));
  assertEquals(0,service.requests(otherParent,null,10L,30L,null,1,20).total());
  when(admins.exists(4L,10L)).thenReturn(true);db.update("INSERT INTO sys_organization_admin VALUES(4,10)");
  service.withdraw(otherParent,id);assertEquals("WITHDRAWN",service.detail(parent,id).get("status"));
 }
 @Test void withdrawalRejectsDisabledTargetInstitutionEvenWhenGlobalFeatureIsEnabled(){
  long id=create();
  String code="STUDENT_ORGANIZATION_RELATIONSHIP";
  when(features.isEnabled(code,null)).thenReturn(true);
  when(features.isEnabled(code,100L)).thenReturn(false);
  doAnswer(call->{
   String feature=call.getArgument(0);Long organizationId=call.getArgument(1);
   if(!features.isEnabled(feature,organizationId))throw new com.lingdong.learning.feature.application.FeatureDisabledException(feature);
   return null;
  }).when(features).requireEnabled(eq(code),org.mockito.ArgumentMatchers.<Long>any());
  assertThrows(com.lingdong.learning.feature.application.FeatureDisabledException.class,()->service.withdraw(parent,id));
  assertEquals("PENDING",db.queryForObject("SELECT status FROM edu_institution_training_request WHERE id=?",String.class,id));
  assertEquals(1L,db.queryForObject("SELECT pending_guard FROM edu_institution_training_request WHERE id=?",Long.class,id));
  verify(features).requireEnabled(code,null);
  verify(features,atLeastOnce()).requireEnabled(code,100L);
 }
 @Test void expiredGuardianCannotReadCreateOrWithdraw(){
  long id=create();when(parents.existsActiveByParentAndStudent(1L,30L)).thenReturn(false);db.update("UPDATE edu_parent_student SET status='INACTIVE' WHERE parent_user_id=1");
  assertThrows(SystemOperationAccessDeniedException.class,()->service.detail(parent,id));assertThrows(SystemOperationAccessDeniedException.class,()->service.withdraw(parent,id));assertThrows(SystemOperationAccessDeniedException.class,this::create);
  assertEquals(0,service.requests(parent,null,10L,null,null,1,20).total());
 }
 @Test void disabledUnregisteredOrCrossInstitutionProgramsCannotBeSubmitted(){
  db.update("UPDATE org_training_program_registration SET enrollable='DISABLED' WHERE program_organization_id=200");
  assertEquals(0,service.programs(parent,100L,30L,10L,1,20,false).total());assertThrows(InstitutionEnrollmentStateException.class,this::create);
  assertThrows(InstitutionEnrollmentStateException.class,()->service.create(parent,30L,10L,100L,201L,"OFFICIAL_COURSE",null));
  assertThrows(SystemOperationAccessDeniedException.class,()->service.create(parent,30L,10L,100L,202L,"CLUB_ACTIVITY",null));
 }
 @Test void enrollmentAndActionPermissionsAreBothRequired(){
  when(relationships.findActiveEnrollmentOrganizationIds(30L)).thenReturn(List.of(100L,101L));
  when(relationships.findActiveClassOrganizationIdsInEnrollment(30L,100L)).thenReturn(List.of());assertThrows(SystemOperationAccessDeniedException.class,this::create);
  when(permissions.isAllowed(1L,PermissionClient.MINIAPP,TrainingService.CREATE)).thenReturn(false);assertThrows(SystemOperationAccessDeniedException.class,this::create);
 }
 @Test void reviewerNeedsActionPermissionAndInstitutionScope(){
  long id=create();when(scopes.canAccess(3L,100L)).thenReturn(false);assertThrows(SystemOperationAccessDeniedException.class,()->service.review(reviewer,id,true,null));
  when(scopes.canAccess(3L,100L)).thenReturn(true);when(permissions.isAllowed(3L,PermissionClient.WEB,TrainingService.REVIEW)).thenReturn(false);assertThrows(SystemOperationAccessDeniedException.class,()->service.review(reviewer,id,true,null));
 }
 @Test void rejectRequiresReasonAndCanOnlyResolvePendingOnce(){
  long id=create();assertThrows(IllegalArgumentException.class,()->service.review(reviewer,id,false," "));assertThrows(IllegalArgumentException.class,()->service.review(reviewer,id,false,"字".repeat(201)));
  service.review(reviewer,id,false,"名额已满");assertThrows(InstitutionEnrollmentStateException.class,()->service.review(reviewer,id,true,null));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM edu_training_participation",Integer.class));
 }
 @Test void approvalRechecksDisabledProjectAndCurrentGuardian(){
  long id=create();db.update("UPDATE org_training_program_registration SET enrollable='DISABLED' WHERE program_organization_id=200");assertThrows(InstitutionEnrollmentStateException.class,()->service.review(reviewer,id,true,null));
  db.update("UPDATE org_training_program_registration SET enrollable='ENABLED' WHERE program_organization_id=200");when(parents.existsActiveByParentAndStudent(1L,30L)).thenReturn(false);assertThrows(SystemOperationAccessDeniedException.class,()->service.review(reviewer,id,true,null));
  assertEquals("PENDING",db.queryForObject("SELECT status FROM edu_institution_training_request WHERE id=?",String.class,id));
 }
 @Test void participationInsertFailureRollsBackCasAndReviewAudit(){
  long id=create();doAnswer(call->{String sql=call.getArgument(0);if(sql.startsWith("INSERT INTO edu_training_participation"))throw new DuplicateKeyException("simulated unique race");return call.callRealMethod();}).when(store).update(anyString(),anyMap());
  assertThrows(InstitutionEnrollmentStateException.class,()->service.review(reviewer,id,true,"同意"));
  var row=store.one("SELECT status,pending_guard,reviewed_by,reviewed_at,review_reason FROM edu_institution_training_request WHERE id=:id",Map.of("id",id));assertEquals("PENDING",row.get("status"));assertNull(row.get("reviewedBy"));assertNull(row.get("reviewedAt"));assertNull(row.get("reviewReason"));assertNotNull(row.get("pendingGuard"));
 }
 @Test void concurrentReviewApprovesExactlyOnce() throws Exception {
  long id=create();var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
  Callable<String> attempt=()->{start.await();try{service.review(reviewer,id,true,"同意");return "approved";}catch(InstitutionEnrollmentStateException e){return "conflict";}};
  try{var a=pool.submit(attempt);var b=pool.submit(attempt);start.countDown();assertEquals(Set.of("approved","conflict"),Set.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM edu_training_participation",Integer.class));}finally{pool.shutdownNow();}
 }
 @Test void listsApplySecurityBeforePagination(){
  for(int i=0;i<3;i++){long id=create();service.review(reviewer,id,false,"名额已满");}
  var page=service.requests(parent,null,10L,null,null,2,2);assertEquals(3,page.total());assertEquals(1,page.items().size());assertEquals(2,page.page());
  assertThrows(IllegalArgumentException.class,()->service.requests(parent,null,10L,null,null,1,101));
 }
 @Test void institutionCandidatesOnlyContainCurrentChildActiveEnrollmentWithOperationalClass() throws Exception {
  when(relationships.findActiveEnrollmentOrganizationIds(30L)).thenReturn(List.of(100L,101L));
  when(relationships.findActiveClassOrganizationIdsInEnrollment(30L,101L)).thenReturn(List.of());
  var method=TrainingService.class.getMethod("institutions",AuthenticatedUser.class,Long.class,Long.class);
  var result=(List<?>)method.invoke(service,parent,30L,10L);
  assertEquals(1,result.size());assertEquals("100",((Map<?,?>)result.get(0)).get("id"));
  when(parents.existsActiveByParentAndStudent(1L,30L)).thenReturn(false);
  var failure=assertThrows(java.lang.reflect.InvocationTargetException.class,()->method.invoke(service,parent,30L,10L));
  assertInstanceOf(SystemOperationAccessDeniedException.class,failure.getCause());
 }
 @Test void expiredEnrollmentCannotBeUsedEvenIfHistoricalClassIsStillActive(){
  when(relationships.findActiveEnrollmentOrganizationIds(30L)).thenReturn(List.of());
  assertThrows(SystemOperationAccessDeniedException.class,this::create);
 }
 @Test void managementCandidatesUseRealClassSqlScopeStatusAndTreePagination(){
  node(210,100L,"COURSE_CATEGORY","分类","/100/210/");
  node(211,210L,"CLASS","分类内课程","/100/210/211/");
  node(212,100L,"CLASS","已停用课程","/100/212/");
  node(213,100L,"CLASS","上级停用课程","/100/213/");
  db.update("UPDATE sys_organization SET status='DISABLED' WHERE id=212");
  db.update("UPDATE sys_organization SET effective_status='DISABLED' WHERE id=213");
  var first=service.programCandidates(reviewer,100L,1,2);
  assertEquals(3,first.total());assertEquals(1,first.page());assertEquals(2,first.pageSize());
  assertEquals(List.of("200","201"),first.items().stream().map(row->row.get("programOrganizationId")).toList());
  assertEquals(Set.of("programOrganizationId","programName"),first.items().get(0).keySet());
  var second=service.programCandidates(reviewer,100L,2,2);
  assertEquals(List.of("211"),second.items().stream().map(row->row.get("programOrganizationId")).toList());
  assertEquals("分类内课程",second.items().get(0).get("programName"));
  assertEquals(0,service.programCandidates(reviewer,100L,3,2).items().size());
  // Being selectable for management never registers the class or permits a family application.
  assertThrows(InstitutionEnrollmentStateException.class,()->service.create(parent,30L,10L,100L,201L,"OFFICIAL_COURSE",null));
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM org_training_program_registration WHERE institution_organization_id=100",Integer.class));
 }
 @Test void institutionPathsTreatUnderscoreAndPercentAsLiteralCharacters(){
  for(int index=0;index<2;index++){
   long institution=110+index*2,sibling=institution+1,ownClass=220+index*2,siblingClass=ownClass+1;
   String path=index==0?"/code_a/":"/code%a/",siblingPath=index==0?"/codeXa/":"/codeXXa/";
   node(institution,null,"TRAINING_INSTITUTION","本机构",path);
   node(sibling,null,"TRAINING_INSTITUTION","兄弟机构",siblingPath);
   node(ownClass,institution,"CLASS","本机构课程",path+ownClass+"/");
   node(siblingClass,sibling,"CLASS","兄弟机构课程",siblingPath+siblingClass+"/");
   when(scopes.canAccess(3L,institution)).thenReturn(true);
   when(relationships.findActiveEnrollmentOrganizationIds(30L)).thenReturn(List.of(institution));
   when(relationships.findActiveClassOrganizationIdsInEnrollment(30L,institution)).thenReturn(List.of(ownClass));
   // A historical registration can retain its original institution after a class moves.
   db.update("INSERT INTO org_training_program_registration(program_organization_id,institution_organization_id,program_type) VALUES(?,?,'OFFICIAL_COURSE'),(?,?,'OFFICIAL_COURSE')",ownClass,institution,siblingClass,institution);
   var candidates=service.programCandidates(reviewer,institution,1,20);
   assertEquals(1,candidates.total());assertEquals(Long.toString(ownClass),candidates.items().get(0).get("programOrganizationId"));
   var programs=service.programs(parent,institution,30L,10L,1,20,false);
   assertEquals(1,programs.total());assertEquals(Long.toString(ownClass),programs.items().get(0).get("programOrganizationId"));
   assertThrows(SystemOperationAccessDeniedException.class,()->service.register(reviewer,siblingClass,institution,"CLUB_ACTIVITY","ENABLED"));
   assertEquals("OFFICIAL_COURSE",db.queryForObject("SELECT program_type FROM org_training_program_registration WHERE program_organization_id=?",String.class,siblingClass));
  }
 }
 @Test void managementCandidatesRequireManageScopeFeatureAndValidPage(){
  for(int[] pagination:List.of(new int[]{-1,20},new int[]{0,20},new int[]{1,0},new int[]{1,101},new int[]{Integer.MAX_VALUE,100}))
   assertThrows(IllegalArgumentException.class,()->service.programCandidates(reviewer,100L,pagination[0],pagination[1]));
  assertThrows(SystemOperationAccessDeniedException.class,()->service.programCandidates(parent,100L,1,20));
  when(scopes.canAccess(3L,101L)).thenReturn(false);
  assertThrows(SystemOperationAccessDeniedException.class,()->service.programCandidates(reviewer,101L,1,20));
  verify(store,never()).rows(anyString(),anyMap());
  doThrow(new SystemOperationAccessDeniedException("feature disabled")).when(features).requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP",100L);
  assertThrows(SystemOperationAccessDeniedException.class,()->service.programCandidates(reviewer,100L,1,20));
  verify(store,never()).rows(anyString(),anyMap());
 }
 @Test void programMustBeExplicitlyRegisteredAndCanBeDisabledByScopedManager(){
  assertEquals(1,service.programs(parent,100L,30L,10L,1,20,false).total());
  var registered=service.register(reviewer,201L,100L,"OFFICIAL_COURSE","ENABLED");
  assertInstanceOf(List.class,registered.get("stageCodes"));
  assertEquals(2,service.programs(parent,100L,30L,10L,1,20,false).total());
  service.register(reviewer,200L,100L,"OFFICIAL_COURSE","DISABLED");
  assertEquals(1,service.programs(parent,100L,30L,10L,1,20,false).total());
  assertThrows(SystemOperationAccessDeniedException.class,()->service.register(otherParent,201L,100L,"OFFICIAL_COURSE","DISABLED"));
 }
}





