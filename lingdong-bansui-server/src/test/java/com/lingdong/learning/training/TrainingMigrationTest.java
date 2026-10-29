package com.lingdong.learning.training;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import static org.junit.jupiter.api.Assertions.*;
class TrainingMigrationTest {
 @Test void pendingAndActiveGuardsRejectDuplicatesButAllowHistoryAndOtherPrograms() {
  var ds=new DriverManagerDataSource("jdbc:h2:mem:training_guards;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
  var db=new JdbcTemplate(ds);
  db.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");
  db.execute("CREATE TABLE sys_organization(id BIGINT PRIMARY KEY)");
  db.execute("CREATE TABLE edu_student(id BIGINT PRIMARY KEY)");
  db.execute("CREATE TABLE sys_permission(id BIGINT PRIMARY KEY,permission_code VARCHAR(100),permission_name VARCHAR(200),resource_type VARCHAR(20),client_type VARCHAR(20),parent_id BIGINT,status VARCHAR(20))");
  db.execute("CREATE TABLE sys_menu(id BIGINT PRIMARY KEY,code VARCHAR(100),name VARCHAR(200),type VARCHAR(20),parent_id BIGINT,permission_code VARCHAR(100),grantable INT,sort_order INT,status VARCHAR(20),version INT)");
  db.execute("CREATE TABLE sys_role(id BIGINT PRIMARY KEY,role_code VARCHAR(100),status VARCHAR(20))");
  db.execute("CREATE TABLE sys_role_permission(id BIGINT PRIMARY KEY,role_id BIGINT,permission_id BIGINT,effect VARCHAR(20))");
  db.execute("CREATE TABLE sys_user_permission(id BIGINT PRIMARY KEY,user_id BIGINT,permission_id BIGINT,effect VARCHAR(20))");
  db.update("INSERT INTO sys_user VALUES(1)");db.update("INSERT INTO edu_student VALUES(1)");
  for(int i=1;i<=4;i++)db.update("INSERT INTO sys_organization VALUES(?)",i);
  new ResourceDatabasePopulator(new ClassPathResource("db/migration/V150__institution_training.sql")).execute(ds);
  db.update("INSERT INTO org_training_program_registration(program_organization_id,institution_organization_id,program_type) VALUES(3,2,'OFFICIAL_COURSE'),(4,2,'OFFICIAL_COURSE')");
  String insert="INSERT INTO edu_institution_training_request(id,student_id,family_organization_id,institution_organization_id,program_organization_id,request_type,status,pending_guard,requested_by) VALUES(?,1,1,2,?,'OFFICIAL_COURSE',?,?,1)";
  db.update(insert,1,3,"PENDING",1);
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update(insert,2,3,"PENDING",1));
  db.update(insert,3,4,"PENDING",1);
  db.update("UPDATE edu_institution_training_request SET status='APPROVED',pending_guard=NULL WHERE id=1");
  db.update(insert,4,3,"PENDING",1);
  String participation="INSERT INTO edu_training_participation(id,training_request_id,student_id,program_organization_id,institution_organization_id,status,active_guard,joined_at) VALUES(?,?,1,?,2,?,?,CURRENT_TIMESTAMP)";
  db.update(participation,1,1,3,"ACTIVE",1);
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update(participation,2,4,3,"ACTIVE",1));
  db.update(participation,3,3,4,"ACTIVE",1);
  db.update("UPDATE edu_training_participation SET status='LEFT',active_guard=NULL WHERE id=1");
  db.update(participation,4,4,3,"ACTIVE",1);
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update(insert,5,3,"PENDING",null));
 }
}

