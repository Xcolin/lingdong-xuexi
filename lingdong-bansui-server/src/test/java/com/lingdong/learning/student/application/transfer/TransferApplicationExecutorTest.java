package com.lingdong.learning.student.application.transfer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.interceptor.*;
import com.lingdong.learning.student.infrastructure.persistence.*;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.feature.application.FeatureAccessService;
import java.time.*;
import static org.mockito.Mockito.*;import static org.junit.jupiter.api.Assertions.*;

class TransferApplicationExecutorTest {
 record Fixture(JdbcTemplate jdbc,TransferApplicationRepository repository,TransferApplicationAccess access,StudentOrganizationChangeMapper changes,TransferApplicationExecutor executor){}
 private Fixture fixture(String db){
  var ds=new DriverManagerDataSource("jdbc:h2:mem:"+db+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");var j=new JdbcTemplate(ds);
  j.execute("CREATE TABLE edu_student(id BIGINT PRIMARY KEY)");j.execute("CREATE TABLE sys_organization(id BIGINT PRIMARY KEY)");j.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");
  j.update("INSERT INTO edu_student VALUES(11)");j.update("INSERT INTO sys_organization VALUES(21),(22),(23)");j.update("INSERT INTO sys_user VALUES(31),(32)");
  j.execute("CREATE TABLE fixture_enrollment(organization_id BIGINT PRIMARY KEY,status VARCHAR(20))");j.update("INSERT INTO fixture_enrollment VALUES(21,'ACTIVE'),(23,'ACTIVE')");
  j.execute("CREATE TABLE fixture_class(status VARCHAR(20))");j.update("INSERT INTO fixture_class VALUES('ACTIVE')");j.execute("CREATE TABLE fixture_membership(id BIGINT)");j.update("INSERT INTO fixture_membership VALUES(21)");j.execute("CREATE TABLE fixture_history(id BIGINT)");
  new ResourceDatabasePopulator(new ClassPathResource("db/migration/V129__student_transfer_applications.sql"),new ClassPathResource("db/migration/V132__transfer_submission_identity.sql")).execute(ds);
  var repo=new TransferApplicationRepository(j,new com.fasterxml.jackson.databind.ObjectMapper());var date=LocalDate.of(2026,10,14);
  repo.insert(1,22,TransferApplication.submit(11,21,31,"destination","reason",date,java.util.List.of(),date));assertTrue(repo.review(1,1,32,true,null,"WEB"));
  var access=mock(TransferApplicationAccess.class);var students=mock(StudentMapper.class);when(students.findByIdForUpdate(11L)).thenReturn(Student.create(11L,"fixture","G1",41L));
  var relations=mock(StudentOrganizationMapper.class);
  when(relations.deactivateActiveClassesInEnrollment(11L,21L)).thenAnswer(i->j.update("UPDATE fixture_class SET status='INACTIVE'"));
  when(relations.removeInstitutionMemberships(41L,21L)).thenAnswer(i->j.update("DELETE FROM fixture_membership WHERE id=21"));
  when(relations.deactivateActiveEnrollment(11L,21L)).thenAnswer(i->j.update("UPDATE fixture_enrollment SET status='INACTIVE' WHERE organization_id=21 AND status='ACTIVE'"));
  var changes=mock(StudentOrganizationChangeMapper.class);when(changes.insert(anyLong(),anyLong(),anyString(),anyLong(),isNull(),anyString(),anyLong(),any())).thenAnswer(i->j.update("INSERT INTO fixture_history VALUES(?)",(Long)i.getArgument(0)));
  var ids=mock(IdGenerator.class);when(ids.nextId()).thenReturn(77L);
  var service=new TransferApplicationExecutor(repo,access,students,relations,changes,ids,Clock.fixed(Instant.parse("2026-10-14T01:00:00Z"),ZoneOffset.UTC),mock(FeatureAccessService.class));
  var factory=new ProxyFactory(service);factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),new MatchAlwaysTransactionAttributeSource()));
  return new Fixture(j,repo,access,changes,(TransferApplicationExecutor)factory.getProxy());
 }
 @Test void completeOnceAndDoNotTouchOtherInstitution(){
  var f=fixture("transfer_execute_success");assertEquals(TransferApplicationExecutor.Result.COMPLETED,f.executor.execute(1));assertEquals(TransferApplicationExecutor.Result.SKIPPED,f.executor.execute(1));
  assertEquals(TransferApplication.Status.COMPLETED,f.repository.find(1).orElseThrow().application().status());
  assertEquals("INACTIVE",f.jdbc.queryForObject("SELECT status FROM fixture_enrollment WHERE organization_id=21",String.class));assertEquals("ACTIVE",f.jdbc.queryForObject("SELECT status FROM fixture_enrollment WHERE organization_id=23",String.class));assertEquals(1,f.jdbc.queryForObject("SELECT COUNT(*) FROM fixture_history",Integer.class));
  assertEquals("WEB",f.repository.executionIdentity(1).reviewerClient());
 }
 @Test void historyFailureRollsBackAllRelationshipWritesAndCompletion(){
  var f=fixture("transfer_execute_rollback");when(f.changes.insert(anyLong(),anyLong(),anyString(),anyLong(),isNull(),anyString(),anyLong(),any())).thenThrow(new IllegalStateException("fixture failure"));
  assertThrows(IllegalStateException.class,()->f.executor.execute(1));assertEquals(TransferApplication.Status.APPROVED,f.repository.find(1).orElseThrow().application().status());
  assertEquals("ACTIVE",f.jdbc.queryForObject("SELECT status FROM fixture_enrollment WHERE organization_id=21",String.class));assertEquals("ACTIVE",f.jdbc.queryForObject("SELECT status FROM fixture_class",String.class));assertEquals(1,f.jdbc.queryForObject("SELECT COUNT(*) FROM fixture_membership",Integer.class));
 }
 @Test void concurrentExecutorsProduceExactlyOneCompletionAndOneHistory() throws Exception {
  var f=fixture("transfer_execute_concurrent");var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
  try {
   var one=pool.submit(()->f.executor.execute(1));var two=pool.submit(()->f.executor.execute(1));
   var results=java.util.List.of(one.get(5,java.util.concurrent.TimeUnit.SECONDS),two.get(5,java.util.concurrent.TimeUnit.SECONDS));
   assertEquals(1,results.stream().filter(r->r==TransferApplicationExecutor.Result.COMPLETED).count());
   assertEquals(1,results.stream().filter(r->r==TransferApplicationExecutor.Result.SKIPPED).count());
   assertEquals(1,f.jdbc.queryForObject("SELECT COUNT(*) FROM fixture_history",Integer.class));
  }finally{pool.shutdownNow();}
 }
 @Test void changedAuthorizationBlocksWithoutLeavingAndFutureDateSkips(){
  var f=fixture("transfer_execute_blocked");when(f.access.executionIssue(any(),any())).thenReturn("GUARDIAN_CHANGED");
  assertEquals(TransferApplicationExecutor.Result.BLOCKED,f.executor.execute(1));assertEquals(TransferApplicationExecutor.Result.SKIPPED,f.executor.execute(1));
  assertEquals("ACTIVE",f.jdbc.queryForObject("SELECT status FROM fixture_enrollment WHERE organization_id=21",String.class));assertEquals("GUARDIAN_CHANGED",f.jdbc.queryForObject("SELECT execution_error_code FROM edu_transfer_application WHERE id=1",String.class));
  var blocked=f.repository.find(1).orElseThrow();assertTrue(blocked.executionBlocked());
  assertFalse(f.repository.rejectBlocked(1,blocked.version(),31,"reapply","WEB"));
  assertFalse(f.repository.rejectBlocked(1,blocked.version()-1,32,"reapply","WEB"));
  assertTrue(f.repository.rejectBlocked(1,blocked.version(),32,"reapply","WEB"));
  assertFalse(f.repository.find(1).orElseThrow().executionBlocked());
  var today=LocalDate.of(2026,10,14);f.repository.insert(2,22,TransferApplication.submit(11,21,31,"new destination","new reason",today,java.util.List.of(),today));
  var future=fixture("transfer_execute_future");future.jdbc.update("UPDATE edu_transfer_application SET effective_date=DATE '2026-10-15' WHERE id=1");assertEquals(TransferApplicationExecutor.Result.SKIPPED,future.executor.execute(1));verifyNoInteractions(future.access);
 }
}
