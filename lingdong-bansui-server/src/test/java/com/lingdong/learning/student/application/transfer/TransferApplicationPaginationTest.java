package com.lingdong.learning.student.application.transfer;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import static org.junit.jupiter.api.Assertions.*;

class TransferApplicationPaginationTest {
 @Test void authorizedPredicatePrecedesPaginationAndCountAndReflectsRemovedGuardians(){
  var ds=new DriverManagerDataSource("jdbc:h2:mem:transfer_page;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");var jdbc=new JdbcTemplate(ds);
  jdbc.execute("CREATE TABLE edu_student(id BIGINT PRIMARY KEY,student_user_id BIGINT)");
  jdbc.execute("CREATE TABLE sys_organization(id BIGINT PRIMARY KEY)");jdbc.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");
  jdbc.execute("CREATE TABLE sys_user_organization(user_id BIGINT,organization_id BIGINT)");
  jdbc.execute("CREATE TABLE edu_parent_student(student_id BIGINT,parent_user_id BIGINT,status VARCHAR(20),relation_role VARCHAR(30),primary_scope_key VARCHAR(30))");
  jdbc.update("INSERT INTO edu_student VALUES(11,41),(12,42),(13,43)");jdbc.update("INSERT INTO sys_organization VALUES(21),(22),(23),(24)");jdbc.update("INSERT INTO sys_user VALUES(31),(32)");
  jdbc.update("INSERT INTO sys_user_organization VALUES(31,22),(41,22),(42,22),(43,24),(31,24)");
  jdbc.update("INSERT INTO edu_parent_student VALUES(11,31,'ACTIVE','PRIMARY_GUARDIAN','PRIMARY'),(12,32,'ACTIVE','PRIMARY_GUARDIAN','PRIMARY'),(13,31,'ACTIVE','PRIMARY_GUARDIAN','PRIMARY')");
  new ResourceDatabasePopulator(new ClassPathResource("db/migration/V129__student_transfer_applications.sql"),new ClassPathResource("db/migration/V132__transfer_submission_identity.sql")).execute(ds);
  var repo=new TransferApplicationRepository(jdbc,new com.fasterxml.jackson.databind.ObjectMapper());
  var date=java.time.LocalDate.now();
  for(long student:java.util.List.of(11L,12L,13L))repo.insert(student,student==13?24:22,TransferApplication.submit(student,21,31,"destination","reason",date,java.util.List.of(),date));
  repo.insert(14,22,TransferApplication.submit(11,23,31,"destination","reason",date,java.util.List.of(),date));
  var first=repo.list(31,22,true,null,1,1);assertEquals(2,first.total());assertEquals(14,first.items().get(0).id());
  assertEquals(11,repo.list(31,22,true,null,2,1).items().get(0).id());assertTrue(repo.list(31,22,true,null,3,1).items().isEmpty());
  assertEquals(3,repo.list(31,21,false,null,1,10).total());assertEquals(1,repo.list(31,24,true,null,1,10).total());
  assertTrue(repo.review(11,1,32,false,"reason"));assertEquals(1,repo.list(31,22,true,"PENDING",1,10).total());
  jdbc.update("UPDATE edu_parent_student SET status='INACTIVE' WHERE student_id=11");
  assertEquals(0,repo.list(31,22,true,null,1,10).total());
  assertThrows(IllegalArgumentException.class,()->repo.list(31,22,true,null,0,10));assertThrows(IllegalArgumentException.class,()->repo.list(31,22,true,null,1,101));
 }
}
