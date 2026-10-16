package com.lingdong.learning.student.application.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import static org.junit.jupiter.api.Assertions.*;

class TransferPermissionRegistrationTest {
 @Test void migrationRegistersBothClientsUnderOrganizationWithoutAssigningRoles() throws Exception {
  var ds=new DriverManagerDataSource("jdbc:h2:mem:transfer_permissions;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
  var jdbc=new JdbcTemplate(ds);
  jdbc.execute("CREATE TABLE sys_permission(id BIGINT PRIMARY KEY, permission_code VARCHAR(100) UNIQUE, permission_name VARCHAR(200),resource_type VARCHAR(20),client_type VARCHAR(20),status VARCHAR(20),parent_id BIGINT)");
  jdbc.execute("CREATE TABLE sys_menu(id BIGINT PRIMARY KEY,code VARCHAR(100) UNIQUE,name VARCHAR(200),type VARCHAR(20),parent_id BIGINT,permission_code VARCHAR(100),grantable INT,sort_order INT,status VARCHAR(20),version INT)");
  jdbc.update("INSERT INTO sys_permission(id,permission_code) VALUES(5,'ORG_NODE_READ')");
  jdbc.update("INSERT INTO sys_menu(id,code) VALUES(6,'ORG_NODE_READ')");
  // No role tables exist: any automatic grant introduced by this migration fails.
  new ResourceDatabasePopulator(new ClassPathResource("db/migration/V130__student_transfer_permissions.sql")).execute(ds);
  var model=new ObjectMapper().readTree(new ClassPathResource("authorization-model.json").getInputStream());
  for(String code:java.util.List.of("STUDENT_TRANSFER_VIEW","STUDENT_TRANSFER_REQUEST_CREATE","STUDENT_TRANSFER_REVIEW")) {
   assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sys_permission WHERE permission_code=? AND client_type='BOTH' AND parent_id=5 AND status='ENABLED'",Integer.class,code));
   assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE code=? AND parent_id=6 AND grantable=1",Integer.class,code));
   var policies=model.path("policies").findValues("code").stream().filter(n->code.equals(n.asText())).count();
   assertEquals(1,policies);
   var action=java.util.stream.StreamSupport.stream(model.path("actions").spliterator(),false).filter(n->code.equals(n.path("code").asText())).findFirst().orElseThrow();
   assertEquals("ORG_NODE_READ",action.path("pagePermissionCode").asText());
  }
 }
}
