package com.lingdong.learning.student.application;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import static org.junit.jupiter.api.Assertions.*;
class ClassMemberPermissionClientTest {
 @Test void existingClassAssignmentSupportsMiniappWithoutNewGrantsOrStatusChanges(){
  var ds=new DriverManagerDataSource("jdbc:h2:mem:class_client_"+System.nanoTime()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");var jdbc=new JdbcTemplate(ds);
  jdbc.execute("CREATE TABLE sys_permission(id BIGINT PRIMARY KEY,permission_code VARCHAR(100),client_type VARCHAR(20),status VARCHAR(20))");
  jdbc.update("INSERT INTO sys_permission VALUES(1,'STUDENT_CLASS_ASSIGN','WEB','DISABLED'),(2,'OTHER_ACTION','WEB','ENABLED')");
  new ResourceDatabasePopulator(new ClassPathResource("db/migration/V133__class_assignment_miniapp_client.sql")).execute(ds);
  assertEquals("BOTH",jdbc.queryForObject("SELECT client_type FROM sys_permission WHERE id=1",String.class));assertEquals("DISABLED",jdbc.queryForObject("SELECT status FROM sys_permission WHERE id=1",String.class));assertEquals("WEB",jdbc.queryForObject("SELECT client_type FROM sys_permission WHERE id=2",String.class));assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM sys_permission",Integer.class));
 }
}
