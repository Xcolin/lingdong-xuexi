package com.lingdong.learning.student.application;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import com.lingdong.learning.student.infrastructure.persistence.AcademicMetadataStore;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TeacherClassMapper;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import java.util.List;
class AcademicMetadataTransactionTest {
 static class FailingStore extends AcademicMetadataStore {boolean fail=true;boolean failAudit=false;FailingStore(JdbcTemplate jdbc){super(jdbc);}public void insertClass(long id,Long teacher){super.insertClass(id,teacher);if(fail)throw new IllegalStateException("after actual insert");}public void recordAudit(String kind,long target,long scope,int version,long actor,String before,String after){super.recordAudit(kind,target,scope,version,actor,before,after);if(failAudit)throw new IllegalStateException("after real audit insert");}}
 static class Fixture {final StudentManagementAccessService access=mock(StudentManagementAccessService.class);final OrganizationMapper orgs=mock(OrganizationMapper.class);final UserMapper users=mock(UserMapper.class);final TeacherClassMapper teachers=mock(TeacherClassMapper.class);final FeatureAccessService features=mock(FeatureAccessService.class);}
 @Configuration @EnableTransactionManagement static class Config {
  @Bean javax.sql.DataSource dataSource(){return new DriverManagerDataSource("jdbc:h2:mem:academic_tx_"+System.nanoTime()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");}
  @Bean org.springframework.transaction.PlatformTransactionManager transactionManager(javax.sql.DataSource ds){return new DataSourceTransactionManager(ds);}
  @Bean JdbcTemplate jdbc(javax.sql.DataSource ds){return new JdbcTemplate(ds);}
  @Bean Fixture fixture(){return new Fixture();}
  @Bean FailingStore store(JdbcTemplate jdbc){return new FailingStore(jdbc);}
  @Bean AcademicMetadataService service(FailingStore store,Fixture f){return new AcademicMetadataService(store,f.access,f.orgs,f.users,f.teachers,f.features);}
 }
 @Test void failureAfterRealWriteRollsBackAndRetryCanSucceed() throws Exception {
  try(var c=new AnnotationConfigApplicationContext(Config.class)) {
   var db=c.getBean(JdbcTemplate.class);db.execute("CREATE TABLE sys_organization(id BIGINT PRIMARY KEY)");db.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");db.execute("CREATE TABLE edu_student(id BIGINT PRIMARY KEY)");
   try(var connection=c.getBean(javax.sql.DataSource.class).getConnection()){ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V134__academic_class_and_enrollment_metadata.sql"));}
   db.update("INSERT INTO sys_organization VALUES(2),(3)");var f=c.getBean(Fixture.class);
   when(f.orgs.findById(2L)).thenReturn(Organization.create(2L,null,"ROOT","S","school","SCHOOL","/2/",1,null));when(f.orgs.findByIdForUpdate(3L)).thenReturn(Organization.create(3L,2L,"S","C","class","CLASS","/2/3/",1,null));
   var actor=new AuthenticatedUser(1L,2L,"actor","actor",AuthClientType.MINIAPP,List.of());when(f.access.organizationAllowed(actor,3L)).thenReturn(true);
   var service=c.getBean(AcademicMetadataService.class);assertThat(org.springframework.aop.support.AopUtils.isAopProxy(service)).isTrue();
   assertThatThrownBy(()->service.setHeadTeacher(actor,3L,null,0)).isInstanceOf(IllegalStateException.class);
   assertThat(db.queryForObject("SELECT COUNT(*) FROM edu_class_academic_metadata",Integer.class)).isZero();
   c.getBean(FailingStore.class).fail=false;assertThat(service.setHeadTeacher(actor,3L,null,0).versionNo()).isEqualTo(1);
   assertThat(db.queryForObject("SELECT COUNT(*) FROM edu_class_academic_metadata",Integer.class)).isEqualTo(1);
  }
 }
 @Test void concurrentSameVersionHasOneSuccessAndOneConflict() throws Exception {
  try(var c=new AnnotationConfigApplicationContext(Config.class)) {
   var db=c.getBean(JdbcTemplate.class);db.execute("CREATE TABLE sys_organization(id BIGINT PRIMARY KEY)");db.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");db.execute("CREATE TABLE edu_student(id BIGINT PRIMARY KEY)");
   try(var connection=c.getBean(javax.sql.DataSource.class).getConnection()){ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V134__academic_class_and_enrollment_metadata.sql"));}
   db.update("INSERT INTO sys_organization VALUES(2),(3)");var f=c.getBean(Fixture.class);
   when(f.orgs.findById(2L)).thenReturn(Organization.create(2L,null,"ROOT","S","school","SCHOOL","/2/",1,null));when(f.orgs.findByIdForUpdate(3L)).thenReturn(Organization.create(3L,2L,"S","C","class","CLASS","/2/3/",1,null));
   var actor=new AuthenticatedUser(1L,2L,"actor","actor",AuthClientType.MINIAPP,List.of());when(f.access.organizationAllowed(actor,3L)).thenReturn(true);
   c.getBean(FailingStore.class).fail=false;
   var node=Organization.create(3L,2L,"S","C","class","CLASS","/2/3/",1,null);
   when(f.orgs.findByIdForUpdate(3L)).thenAnswer(invocation->{db.queryForObject("SELECT id FROM sys_organization WHERE id=3 FOR UPDATE",Long.class);return node;});
   var service=c.getBean(AcademicMetadataService.class);var ready=new java.util.concurrent.CountDownLatch(2);var start=new java.util.concurrent.CountDownLatch(1);
   var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
   try {
    java.util.concurrent.Callable<String> operation=()->{ready.countDown();if(!start.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("start timeout");try{service.setHeadTeacher(actor,3L,null,0);return "SUCCESS";}catch(IllegalStateException conflict){if(!conflict.getMessage().contains("刷新"))throw conflict;return "CONFLICT";}};
    var first=pool.submit(operation);var second=pool.submit(operation);assertThat(ready.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();start.countDown();
    assertThat(List.of(first.get(10,java.util.concurrent.TimeUnit.SECONDS),second.get(10,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder("SUCCESS","CONFLICT");
    assertThat(db.queryForObject("SELECT COUNT(*) FROM edu_class_academic_metadata",Integer.class)).isEqualTo(1);
    assertThat(db.queryForObject("SELECT version_no FROM edu_class_academic_metadata WHERE class_organization_id=3",Integer.class)).isEqualTo(1);
   } finally {start.countDown();pool.shutdownNow();}
  }
 }
 @Test void auditFailureRollsBackBothConfigurationAndHistory() throws Exception {
  try(var c=new AnnotationConfigApplicationContext(Config.class)) {
   var db=c.getBean(JdbcTemplate.class);db.execute("CREATE TABLE sys_organization(id BIGINT PRIMARY KEY)");db.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");db.execute("CREATE TABLE edu_student(id BIGINT PRIMARY KEY)");
   try(var connection=c.getBean(javax.sql.DataSource.class).getConnection()){ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V134__academic_class_and_enrollment_metadata.sql"));}
   db.update("INSERT INTO sys_organization VALUES(2),(3)");var f=c.getBean(Fixture.class);
   when(f.orgs.findById(2L)).thenReturn(Organization.create(2L,null,"ROOT","S","school","SCHOOL","/2/",1,null));when(f.orgs.findByIdForUpdate(3L)).thenReturn(Organization.create(3L,2L,"S","C","class","CLASS","/2/3/",1,null));
   var actor=new AuthenticatedUser(1L,2L,"actor","actor",AuthClientType.MINIAPP,List.of());when(f.access.organizationAllowed(actor,3L)).thenReturn(true);
   var service=c.getBean(AcademicMetadataService.class);assertThat(org.springframework.aop.support.AopUtils.isAopProxy(service)).isTrue();
   c.getBean(FailingStore.class).fail=false;c.getBean(FailingStore.class).failAudit=true;
   assertThatThrownBy(()->service.setHeadTeacher(actor,3L,null,0)).isInstanceOf(IllegalStateException.class);
   assertThat(db.queryForObject("SELECT COUNT(*) FROM edu_class_academic_metadata",Integer.class)).isZero();
   assertThat(db.queryForObject("SELECT COUNT(*) FROM edu_academic_metadata_audit",Integer.class)).isZero();
   c.getBean(FailingStore.class).failAudit=false;assertThat(service.setHeadTeacher(actor,3L,null,0).versionNo()).isEqualTo(1);
   assertThat(db.queryForObject("SELECT COUNT(*) FROM edu_class_academic_metadata",Integer.class)).isEqualTo(1);
   assertThat(db.queryForObject("SELECT COUNT(*) FROM edu_academic_metadata_audit",Integer.class)).isEqualTo(1);
  }
 }
}
