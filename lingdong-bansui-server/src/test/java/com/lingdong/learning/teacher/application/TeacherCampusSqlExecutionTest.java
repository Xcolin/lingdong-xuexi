package com.lingdong.learning.teacher.application;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import com.lingdong.learning.teacher.infrastructure.persistence.TeacherDirectoryCriteria;
class TeacherCampusSqlExecutionTest {
 @Test void actualCountSeparatesCampusesAndExcludesEndedAssignments() throws Exception {
  var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:teacher_campus_"+System.nanoTime()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");var db=new org.springframework.jdbc.core.JdbcTemplate(ds);
  db.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY,user_type VARCHAR(30),username VARCHAR(50),display_name VARCHAR(50),status VARCHAR(30),mobile VARCHAR(40),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");db.execute("CREATE TABLE sys_organization(id BIGINT PRIMARY KEY,organization_type VARCHAR(30),organization_path VARCHAR(100),organization_name VARCHAR(100) DEFAULT 'org')");db.execute("CREATE TABLE sys_user_organization(user_id BIGINT,organization_id BIGINT)");db.execute("CREATE TABLE edu_teacher_profile(user_id BIGINT,status VARCHAR(30))");db.execute("CREATE TABLE edu_teacher_class(teacher_user_id BIGINT,class_organization_id BIGINT,status VARCHAR(30),effective_to TIMESTAMP)");
  db.update("INSERT INTO sys_user(id,user_type,username,display_name,status) VALUES(1,'ORGANIZATION','a','a','ENABLED'),(2,'ORGANIZATION','b','b','ENABLED'),(3,'ORGANIZATION','ended','ended','ENABLED'),(4,'ORGANIZATION','second-a','second-a','ENABLED')");db.update("INSERT INTO sys_organization(id,organization_type,organization_path) VALUES(10,'SCHOOL','/10/'),(11,'CLASS','/10/20/11/'),(12,'CLASS','/10/30/12/')");db.update("INSERT INTO sys_user_organization VALUES(1,10),(2,10),(3,10),(4,10)");db.update("INSERT INTO edu_teacher_profile VALUES(1,'ENABLED'),(2,'ENABLED'),(3,'ENABLED'),(4,'ENABLED')");db.update("INSERT INTO edu_teacher_class VALUES(1,11,'ACTIVE',NULL),(2,12,'ACTIVE',NULL),(3,11,'ACTIVE',CURRENT_TIMESTAMP),(4,11,'ACTIVE',NULL)");
  var config=new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("test",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),ds));try(var in=getClass().getClassLoader().getResourceAsStream("mapper/teacher/TeacherManagementMapper.xml")){new org.apache.ibatis.builder.xml.XMLMapperBuilder(in,config,"campus-real-sql",config.getSqlFragments()).parse();}
  try(var session=new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config).openSession()){
   String query="com.lingdong.learning.teacher.infrastructure.persistence.TeacherManagementMapper.count";
   assertThat((Long)session.selectOne(query,Map.of("criteria",criteria("/10/20/")))).isEqualTo(2);
   assertThat((Long)session.selectOne(query,Map.of("criteria",criteria("/10/30/")))).isEqualTo(1);
   assertThat((Long)session.selectOne(query,Map.of("criteria",criteria("/10/unknown/")))).isZero();
   assertThat((Long)session.selectOne(query,Map.of("criteria",criteria(null)))).isEqualTo(4);
   String pageQuery="com.lingdong.learning.teacher.infrastructure.persistence.TeacherManagementMapper.findPage";
   var first=session.<com.lingdong.learning.teacher.infrastructure.persistence.TeacherDirectoryRow>selectList(pageQuery,Map.of("criteria",new TeacherDirectoryCriteria(null,10L,null,null,true,List.of(),1,0,"/10/20/")));
   var second=session.<com.lingdong.learning.teacher.infrastructure.persistence.TeacherDirectoryRow>selectList(pageQuery,Map.of("criteria",new TeacherDirectoryCriteria(null,10L,null,null,true,List.of(),1,1,"/10/20/")));
   assertThat(first).hasSize(1);assertThat(second).hasSize(1);assertThat(List.of(first.get(0).id(),second.get(0).id())).containsExactlyInAnyOrder(1L,4L);
   assertThat(session.selectList(pageQuery,Map.of("criteria",new TeacherDirectoryCriteria(null,10L,null,null,true,List.of(),1,2,"/10/20/")))).isEmpty();

  }
 }
 private TeacherDirectoryCriteria criteria(String path){return new TeacherDirectoryCriteria(null,10L,null,null,true,List.of(),20,0,path);}
}
