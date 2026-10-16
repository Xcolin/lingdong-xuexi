package com.lingdong.learning.student.application;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;
class AcademicMetadataStoreTest {
 @Test void realWritesUseVersionsAndRetainOtherInstitutions() throws Exception {
  var ds=new org.h2.jdbcx.JdbcDataSource();ds.setURL("jdbc:h2:mem:academic_store;MODE=MySQL;DB_CLOSE_DELAY=-1");var db=new JdbcTemplate(ds);
  db.execute("CREATE TABLE sys_organization(id BIGINT PRIMARY KEY)");db.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");db.execute("CREATE TABLE edu_student(id BIGINT PRIMARY KEY)");
  try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/V134__academic_class_and_enrollment_metadata.sql"));}
  db.update("INSERT INTO sys_organization VALUES(1),(2)");db.update("INSERT INTO sys_user VALUES(100),(101)");db.update("INSERT INTO edu_student VALUES(10)");
  var type=Class.forName("com.lingdong.learning.student.infrastructure.persistence.AcademicMetadataStore");var store=type.getConstructor(JdbcTemplate.class).newInstance(db);
  assertThat(type.getMethod("classMetadata",long.class).invoke(store,1L)).isEqualTo(java.util.Optional.empty());
  type.getMethod("insertClass",long.class,Long.class).invoke(store,1L,100L);
  assertThat(type.getMethod("updateClass",long.class,Long.class,int.class).invoke(store,1L,101L,1)).isEqualTo(true);
  assertThat(type.getMethod("updateClass",long.class,Long.class,int.class).invoke(store,1L,100L,1)).isEqualTo(false);
  assertThat(((java.util.Optional<?>)type.getMethod("classMetadata",long.class).invoke(store,1L))).isPresent();
  assertThat(db.queryForObject("SELECT head_teacher_user_id FROM edu_class_academic_metadata WHERE class_organization_id=1",Long.class)).isEqualTo(101L);
  type.getMethod("insertEnrollment",long.class,long.class,String.class).invoke(store,10L,1L,"A001");
  type.getMethod("insertEnrollment",long.class,long.class,String.class).invoke(store,10L,2L,"B001");
  assertThat(type.getMethod("updateEnrollment",long.class,long.class,String.class,int.class).invoke(store,10L,1L,"A002",1)).isEqualTo(true);
  assertThat(type.getMethod("updateEnrollment",long.class,long.class,String.class,int.class).invoke(store,10L,1L,"A003",1)).isEqualTo(false);
  type.getMethod("recordAudit",String.class,long.class,long.class,int.class,long.class,String.class,String.class).invoke(store,"STUDENT_NUMBER",10L,1L,2,100L,"A001","A002");
  assertThat(db.queryForObject("SELECT operator_user_id FROM edu_academic_metadata_audit",Long.class)).isEqualTo(100L);
  assertThat(db.queryForObject("SELECT student_number FROM edu_student_enrollment_metadata WHERE institution_organization_id=2",String.class)).isEqualTo("B001");
 }
}
