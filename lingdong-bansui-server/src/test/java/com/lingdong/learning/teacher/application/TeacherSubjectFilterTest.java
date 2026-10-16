package com.lingdong.learning.teacher.application;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
class TeacherSubjectFilterTest {
 @Test void actualPageAndCountFilterWithinTheSameInstitution() throws Exception {
  var criteriaType=com.lingdong.learning.teacher.infrastructure.persistence.TeacherDirectoryCriteria.class;
  var constructor=criteriaType.getConstructor(String.class,Long.class,Long.class,com.lingdong.learning.user.domain.UserStatus.class,boolean.class,List.class,int.class,int.class,String.class,String.class);
  var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:subject_filter_"+System.nanoTime()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");var db=new org.springframework.jdbc.core.JdbcTemplate(ds);
  db.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY,user_type VARCHAR(30),username VARCHAR(50),display_name VARCHAR(50),status VARCHAR(30),mobile VARCHAR(40),created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
  db.execute("CREATE TABLE sys_organization(id BIGINT PRIMARY KEY,organization_type VARCHAR(30),organization_path VARCHAR(100),organization_name VARCHAR(100))");
  db.execute("CREATE TABLE sys_user_organization(user_id BIGINT,organization_id BIGINT)");db.execute("CREATE TABLE edu_teacher_profile(user_id BIGINT,status VARCHAR(30))");db.execute("CREATE TABLE edu_teacher_class(teacher_user_id BIGINT,class_organization_id BIGINT,status VARCHAR(30),effective_to TIMESTAMP)");
  try(var c=ds.getConnection()){org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(c,new org.springframework.core.io.ClassPathResource("db/migration/V135__teacher_subject_metadata.sql"));}
  db.update("INSERT INTO sys_user(id,user_type,username,display_name,status) VALUES(1,'ORGANIZATION','a','a','ENABLED'),(2,'ORGANIZATION','b','b','ENABLED')");
  db.update("INSERT INTO sys_organization VALUES(10,'SCHOOL','/10/','first'),(20,'SCHOOL','/20/','second')");db.update("INSERT INTO sys_user_organization VALUES(1,10),(1,20),(2,10)");db.update("INSERT INTO edu_teacher_profile VALUES(1,'ENABLED'),(2,'ENABLED')");
  db.update("INSERT INTO edu_teacher_subject_metadata(teacher_user_id,institution_organization_id,subject_code) VALUES(1,20,'MATH'),(1,10,'ART'),(2,10,'MATH')");
  var config=new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("test",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),ds));
  try(var in=getClass().getClassLoader().getResourceAsStream("mapper/teacher/TeacherManagementMapper.xml")){new org.apache.ibatis.builder.xml.XMLMapperBuilder(in,config,"subject-filter",config.getSqlFragments()).parse();}
  try(var session=new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config).openSession()){
   String base="com.lingdong.learning.teacher.infrastructure.persistence.TeacherManagementMapper.";
   var first=constructor.newInstance(null,10L,null,null,true,List.of(),1,0,null,"MATH");
   assertThat((Long)session.selectOne(base+"count",Map.of("criteria",first))).isEqualTo(1);
   var rows=session.<com.lingdong.learning.teacher.infrastructure.persistence.TeacherDirectoryRow>selectList(base+"findPage",Map.of("criteria",first));assertThat(rows).hasSize(1);assertThat(rows.get(0).id()).isEqualTo(2L);
   var scoped=constructor.newInstance(null,null,null,null,false,List.of("/10/"),20,0,null,"MATH");assertThat((Long)session.selectOne(base+"count",Map.of("criteria",scoped))).isEqualTo(1);
   var other=constructor.newInstance(null,20L,null,null,true,List.of(),20,0,null,"MATH");assertThat((Long)session.selectOne(base+"count",Map.of("criteria",other))).isEqualTo(1);
   var unknown=constructor.newInstance(null,10L,null,null,true,List.of(),20,0,null,"UNKNOWN");assertThat((Long)session.selectOne(base+"count",Map.of("criteria",unknown))).isZero();
  }
 }
}
