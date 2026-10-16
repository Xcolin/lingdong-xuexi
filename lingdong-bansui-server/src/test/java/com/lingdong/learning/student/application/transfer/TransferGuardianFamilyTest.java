package com.lingdong.learning.student.application.transfer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;
class TransferGuardianFamilyTest {
 @Test void acceptsOnlyOneOperationalCommonFamilyAndRejectsAmbiguity(){
  var j=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:transfer_guardian_family;MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
  j.execute("CREATE TABLE edu_student(id BIGINT,student_user_id BIGINT)");j.execute("CREATE TABLE sys_user_organization(user_id BIGINT,organization_id BIGINT)");j.execute("CREATE TABLE sys_organization(id BIGINT,organization_type VARCHAR(30),status VARCHAR(20),effective_status VARCHAR(20))");
  j.update("INSERT INTO edu_student VALUES(11,41)");j.update("INSERT INTO sys_user_organization VALUES(31,22),(41,22),(31,24),(41,24)");j.update("INSERT INTO sys_organization VALUES(22,'FAMILY','ENABLED','ENABLED'),(24,'FAMILY','ENABLED','ENABLED')");
  var repo=new TransferApplicationRepository(j,new com.fasterxml.jackson.databind.ObjectMapper());assertThrows(IllegalArgumentException.class,()->repo.uniqueGuardianFamily(11,31));
  j.update("UPDATE sys_organization SET effective_status='DISABLED' WHERE id=24");assertEquals(22,repo.uniqueGuardianFamily(11,31));assertThrows(IllegalArgumentException.class,()->repo.uniqueGuardianFamily(11,99));
 }
}
