package com.lingdong.learning.student.application.transfer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import static org.junit.jupiter.api.Assertions.*;

class TransferApplicationSchemaTest {
 @Test void oneActiveProposalPerStudentAndSourceWithTerminalHistoryRetained() {
  var ds=new DriverManagerDataSource("jdbc:h2:mem:transfer_schema;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");var j=new JdbcTemplate(ds);
  j.execute("CREATE TABLE edu_student(id BIGINT PRIMARY KEY)");j.execute("CREATE TABLE sys_organization(id BIGINT PRIMARY KEY)");j.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");
  j.update("INSERT INTO edu_student VALUES (11),(12)");j.update("INSERT INTO sys_organization VALUES (21),(22),(23)");j.update("INSERT INTO sys_user VALUES (31)");
  new ResourceDatabasePopulator(new ClassPathResource("db/migration/V129__student_transfer_applications.sql"),new ClassPathResource("db/migration/V132__transfer_submission_identity.sql")).execute(ds);
  String sql="INSERT INTO edu_transfer_application(id,student_id,source_institution_id,family_organization_id,applicant_user_id,target_description,reason,effective_date) VALUES (?, ?, ?,22,31,'fixture target','fixture reason',CURRENT_DATE)";
  j.update(sql,1L,11L,21L);
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->j.update(sql,2L,11L,21L));
  j.update(sql,3L,12L,21L);j.update(sql,4L,11L,23L);
  j.update("UPDATE edu_transfer_application SET status='WITHDRAWN' WHERE id=1");j.update(sql,5L,11L,21L);
  assertEquals(4,j.queryForObject("SELECT COUNT(*) FROM edu_transfer_application",Integer.class));
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->j.update("UPDATE edu_transfer_application SET status='COMPLETED' WHERE id=5"));
  j.update("INSERT INTO sys_user VALUES (32)");
  var repository=new TransferApplicationRepository(j,new com.fasterxml.jackson.databind.ObjectMapper());
  var proposal=TransferApplication.submit(12,23,31,"fixture destination","fixture reason",java.time.LocalDate.now(),java.util.List.of(2110374231123427328L),java.time.LocalDate.now());
  repository.insert(6,22,proposal);
  var stored=repository.find(6).orElseThrow();assertEquals(proposal,stored.application());assertEquals(1,stored.version());
  assertFalse(repository.review(6,1,31,true,null));
  assertFalse(repository.review(6,2,32,true,null));
  assertTrue(repository.review(6,1,32,true,null));
  assertFalse(repository.withdraw(6,1,31));
  assertFalse(repository.review(6,1,32,false,"duplicate"));
  assertEquals(TransferApplication.Status.APPROVED,repository.find(6).orElseThrow().application().status());
  assertEquals(2,repository.find(6).orElseThrow().version());
  assertNotNull(repository.find(6).orElseThrow().createdAt());assertNotNull(repository.find(6).orElseThrow().reviewedAt());
 }
}
