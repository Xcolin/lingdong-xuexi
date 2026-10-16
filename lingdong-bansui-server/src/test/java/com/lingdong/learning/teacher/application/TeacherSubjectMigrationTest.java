package com.lingdong.learning.teacher.application;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class TeacherSubjectMigrationTest {
 @Test void subjectBelongsToTeacherAndInstitutionAndCanBeCleared() throws Exception {
  var ds=new org.h2.jdbcx.JdbcDataSource();ds.setURL("jdbc:h2:mem:teacher_subject_"+System.nanoTime()+";MODE=MySQL;DB_CLOSE_DELAY=-1");var db=new org.springframework.jdbc.core.JdbcTemplate(ds);db.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");db.execute("CREATE TABLE sys_organization(id BIGINT PRIMARY KEY)");
  try(var c=ds.getConnection()){org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(c,new org.springframework.core.io.ClassPathResource("db/migration/V135__teacher_subject_metadata.sql"));}
  db.update("INSERT INTO sys_user VALUES(1),(2)");db.update("INSERT INTO sys_organization VALUES(10),(20)");
  db.update("INSERT INTO edu_teacher_subject_metadata(teacher_user_id,institution_organization_id,subject_code) VALUES(1,10,'MATH'),(1,20,'ART'),(2,10,NULL)");
  assertThatThrownBy(()->db.update("INSERT INTO edu_teacher_subject_metadata(teacher_user_id,institution_organization_id,subject_code) VALUES(1,10,'ART')")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->db.update("UPDATE edu_teacher_subject_metadata SET subject_code='  ' WHERE teacher_user_id=1 AND institution_organization_id=10")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->db.update("INSERT INTO edu_teacher_subject_metadata(teacher_user_id,institution_organization_id) VALUES(999,10)")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->db.update("UPDATE edu_teacher_subject_metadata SET version_no=0 WHERE teacher_user_id=1 AND institution_organization_id=10")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  db.update("UPDATE edu_teacher_subject_metadata SET subject_code=NULL WHERE teacher_user_id=1 AND institution_organization_id=10");assertThat(db.queryForObject("SELECT COUNT(*) FROM edu_teacher_subject_metadata",Integer.class)).isEqualTo(3);
 }
}
