package com.lingdong.learning.student.application.transfer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import static org.junit.jupiter.api.Assertions.*;
class TransferSubmissionIdentityTest {
 @Test void upgradesExistingFamilyHistoryAndKeepsOperatorSeparateFromGuardian(){
  var ds=new DriverManagerDataSource("jdbc:h2:mem:transfer_identity;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");var jdbc=new JdbcTemplate(ds);
  jdbc.execute("CREATE TABLE edu_student(id BIGINT PRIMARY KEY)");jdbc.execute("CREATE TABLE sys_organization(id BIGINT PRIMARY KEY)");jdbc.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");
  jdbc.update("INSERT INTO edu_student VALUES(11)");jdbc.update("INSERT INTO sys_organization VALUES(21),(22)");jdbc.update("INSERT INTO sys_user VALUES(31),(32)");
  new ResourceDatabasePopulator(new ClassPathResource("db/migration/V129__student_transfer_applications.sql")).execute(ds);
  jdbc.update("INSERT INTO edu_transfer_application(id,student_id,source_institution_id,family_organization_id,applicant_user_id,target_description,reason,effective_date) VALUES(1,11,21,22,31,'destination','reason',CURRENT_DATE)");
  new ResourceDatabasePopulator(new ClassPathResource("db/migration/V132__transfer_submission_identity.sql")).execute(ds);
  var repo=new TransferApplicationRepository(jdbc,new com.fasterxml.jackson.databind.ObjectMapper());
  assertEquals(31L,repo.executionIdentity(1).guardianId());assertEquals("FAMILY",repo.executionIdentity(1).submissionMode());
  repo.recordInstitutionSubmission(1,32,"WEB");assertEquals(31,repo.find(1).orElseThrow().application().applicantId());assertEquals(32L,repo.executionIdentity(1).guardianId());assertEquals("INSTITUTION",repo.executionIdentity(1).submissionMode());assertEquals("WEB",repo.executionIdentity(1).applicantClient());
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE edu_transfer_application SET submission_mode='UNKNOWN' WHERE id=1"));
 }
}
