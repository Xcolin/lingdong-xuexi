package com.lingdong.learning.training;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import static org.junit.jupiter.api.Assertions.*;
class TrainingPermissionTest {
 @Test void actionsUseBothClientsCorrectParentsAndOnlyControlledExistingGrants() {
  var ds=new DriverManagerDataSource("jdbc:h2:mem:training_permission_"+java.util.UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");var db=new JdbcTemplate(ds);
  db.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");db.execute("CREATE TABLE sys_organization(id BIGINT PRIMARY KEY)");db.execute("CREATE TABLE edu_student(id BIGINT PRIMARY KEY)");
  db.execute("CREATE TABLE sys_permission(id BIGINT PRIMARY KEY,permission_code VARCHAR(100),permission_name VARCHAR(200),resource_type VARCHAR(20),client_type VARCHAR(20),parent_id BIGINT,status VARCHAR(20))");
  db.execute("CREATE TABLE sys_menu(id BIGINT PRIMARY KEY,code VARCHAR(100),name VARCHAR(200),type VARCHAR(20),parent_id BIGINT,permission_code VARCHAR(100),grantable INT,sort_order INT,status VARCHAR(20),version INT)");
  db.execute("CREATE TABLE sys_role(id BIGINT PRIMARY KEY,role_code VARCHAR(100),status VARCHAR(20))");db.execute("CREATE TABLE sys_role_permission(id BIGINT PRIMARY KEY,role_id BIGINT,permission_id BIGINT,effect VARCHAR(20))");db.execute("CREATE TABLE sys_user_permission(id BIGINT PRIMARY KEY,user_id BIGINT,permission_id BIGINT,effect VARCHAR(20))");
  db.update("INSERT INTO sys_permission(id,permission_code,resource_type,parent_id,status) VALUES(1,'PARENT_RELATIONSHIP_READ','PAGE',NULL,'ENABLED'),(2,'CLASS_READ','BUTTON',6,'ENABLED'),(3,'STUDENT_ENROLLMENT_REQUEST_CREATE','BUTTON',1,'ENABLED'),(4,'STUDENT_ENROLLMENT_REVIEW','BUTTON',6,'ENABLED'),(5,'STUDENT_ENROLLMENT_INVITE_CREATE','BUTTON',6,'ENABLED'),(6,'ORG_NODE_READ','PAGE',NULL,'ENABLED')");
  // Menu IDs deliberately differ from permission IDs: each hierarchy must resolve its own PAGE.
  db.update("INSERT INTO sys_menu(id,code,type,parent_id) VALUES(101,'PARENT_RELATIONSHIP_READ','PAGE',NULL),(102,'CLASS_READ','BUTTON',106),(106,'ORG_NODE_READ','PAGE',NULL)");
  db.update("INSERT INTO sys_role VALUES(1,'PARENT','ENABLED'),(2,'CUSTOM_REVIEWER','ENABLED'),(3,'UNRELATED','ENABLED'),(4,'CUSTOM_NO_PARENT_PAGE','ENABLED'),(5,'DENIED_REVIEWER','ENABLED'),(6,'PARENT_ADMIN','ENABLED'),(7,'PAGE_ONLY_REVIEWER','ENABLED'),(8,'DISABLED_REVIEWER','DISABLED'),(9,'DENIED_PARENT','ENABLED')");
  db.update("INSERT INTO sys_role_permission VALUES(1,1,1,'ALLOW'),(2,2,6,'ALLOW'),(3,2,4,'ALLOW'),(4,2,5,'ALLOW'),(5,4,4,'ALLOW'),(6,5,6,'ALLOW'),(7,5,4,'DENY'),(8,1,3,'ALLOW'),(9,6,1,'ALLOW'),(10,7,6,'ALLOW'),(11,8,6,'ALLOW'),(12,8,4,'ALLOW'),(13,9,1,'ALLOW'),(14,9,3,'DENY'),(15,4,2,'ALLOW'),(16,4,5,'ALLOW')");
  db.update("INSERT INTO sys_user_permission VALUES(1,99,6,'ALLOW'),(2,99,4,'ALLOW'),(3,98,4,'ALLOW'),(4,97,4,'DENY'),(5,96,6,'ALLOW'),(6,95,1,'ALLOW'),(7,95,3,'ALLOW'),(8,94,1,'ALLOW'),(9,93,6,'ALLOW'),(10,93,5,'ALLOW'),(11,92,5,'DENY'),(12,98,2,'ALLOW')");
  new ResourceDatabasePopulator(new ClassPathResource("db/migration/V150__institution_training.sql")).execute(ds);
  for(var entry:java.util.Map.of("STUDENT_TRAINING_REQUEST_CREATE","PARENT_RELATIONSHIP_READ","STUDENT_TRAINING_REQUEST_REVIEW","ORG_NODE_READ","STUDENT_TRAINING_PROGRAM_MANAGE","ORG_NODE_READ").entrySet()){
   assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM sys_permission action_row JOIN sys_permission parent_page ON parent_page.id=action_row.parent_id WHERE action_row.permission_code=? AND action_row.client_type='BOTH' AND action_row.resource_type='BUTTON' AND action_row.status='ENABLED' AND parent_page.permission_code=? AND parent_page.resource_type='PAGE'",Integer.class,entry.getKey(),entry.getValue()));
   assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM sys_menu action_row JOIN sys_menu parent_page ON parent_page.id=action_row.parent_id WHERE action_row.code=? AND action_row.type='BUTTON' AND action_row.grantable=1 AND parent_page.code=? AND parent_page.type='PAGE'",Integer.class,entry.getKey(),entry.getValue()));
  }
  assertEquals(1,grants(db,"sys_role_permission","role_id",1));assertEquals(2,grants(db,"sys_role_permission","role_id",2));
  for(long role:java.util.List.of(3L,4L,5L,6L,7L,8L,9L))assertEquals(0,grants(db,"sys_role_permission","role_id",role),"Unexpected widening for role "+role);
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM sys_role_permission grant_row JOIN sys_permission p ON p.id=grant_row.permission_id WHERE grant_row.role_id=5 AND p.permission_code='STUDENT_TRAINING_REQUEST_REVIEW' AND grant_row.effect='DENY'",Integer.class));
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM sys_user_permission grant_row JOIN sys_permission p ON p.id=grant_row.permission_id WHERE grant_row.user_id=97 AND p.permission_code='STUDENT_TRAINING_REQUEST_REVIEW' AND grant_row.effect='DENY'",Integer.class));
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM sys_role_permission grant_row JOIN sys_permission p ON p.id=grant_row.permission_id WHERE grant_row.role_id=9 AND p.permission_code='STUDENT_TRAINING_REQUEST_CREATE' AND grant_row.effect='DENY'",Integer.class));
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM sys_user_permission grant_row JOIN sys_permission p ON p.id=grant_row.permission_id WHERE grant_row.user_id=92 AND p.permission_code='STUDENT_TRAINING_PROGRAM_MANAGE' AND grant_row.effect='DENY'",Integer.class));
  for(long user:java.util.List.of(99L,95L,93L))assertEquals(1,grants(db,"sys_user_permission","user_id",user));
  for(long user:java.util.List.of(98L,97L,96L,94L,92L))assertEquals(0,grants(db,"sys_user_permission","user_id",user));
 }
 private int grants(JdbcTemplate db,String table,String key,long subject){return db.queryForObject("SELECT COUNT(*) FROM "+table+" grant_row JOIN sys_permission p ON p.id=grant_row.permission_id WHERE grant_row."+key+"=? AND p.permission_code LIKE 'STUDENT_TRAINING_%' AND grant_row.effect='ALLOW'",Integer.class,subject);}
}

