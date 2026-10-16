package com.lingdong.learning.teacher.application;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class TeacherSubjectStoreTest {
 @Test void realStoreIsInstitutionScopedAndVersioned() throws Exception {
  var type=Class.forName("com.lingdong.learning.teacher.infrastructure.persistence.TeacherSubjectStore");
  var ds=new org.h2.jdbcx.JdbcDataSource();ds.setURL("jdbc:h2:mem:subject_store_"+System.nanoTime()+";MODE=MySQL;DB_CLOSE_DELAY=-1");var db=new org.springframework.jdbc.core.JdbcTemplate(ds);db.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");db.execute("CREATE TABLE sys_organization(id BIGINT PRIMARY KEY)");try(var c=ds.getConnection()){org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(c,new org.springframework.core.io.ClassPathResource("db/migration/V135__teacher_subject_metadata.sql"));}db.update("INSERT INTO sys_user VALUES(1)");db.update("INSERT INTO sys_organization VALUES(10),(20)");
  var store=type.getConstructor(org.springframework.jdbc.core.JdbcTemplate.class).newInstance(db);var read=type.getMethod("read",long.class,long.class);var insert=type.getMethod("insert",long.class,long.class,String.class);var update=type.getMethod("update",long.class,long.class,String.class,int.class);
  assertThat((java.util.Optional<?>)read.invoke(store,1L,10L)).isEmpty();insert.invoke(store,1L,10L,"MATH");insert.invoke(store,1L,20L,"ART");assertThat((java.util.Optional<?>)read.invoke(store,1L,10L)).isPresent();
  assertThat(update.invoke(store,1L,10L,"OTHER",0)).isEqualTo(false);assertThat(update.invoke(store,1L,10L,null,1)).isEqualTo(true);assertThat(update.invoke(store,1L,10L,"OTHER",1)).isEqualTo(false);
  assertThat(db.queryForObject("SELECT subject_code FROM edu_teacher_subject_metadata WHERE teacher_user_id=1 AND institution_organization_id=10",String.class)).isNull();assertThat(db.queryForObject("SELECT version_no FROM edu_teacher_subject_metadata WHERE teacher_user_id=1 AND institution_organization_id=10",Integer.class)).isEqualTo(2);assertThat(db.queryForObject("SELECT subject_code FROM edu_teacher_subject_metadata WHERE teacher_user_id=1 AND institution_organization_id=20",String.class)).isEqualTo("ART");
 }
}
