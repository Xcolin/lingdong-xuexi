package com.lingdong.learning.student.application;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;
class AcademicMetadataMigrationTest {
 @Test void perInstitutionNumbersAreUniqueWithoutGlobalAccountCoupling() throws Exception {
  var ds=new org.h2.jdbcx.JdbcDataSource();ds.setURL("jdbc:h2:mem:academic_metadata;MODE=MySQL;DB_CLOSE_DELAY=-1");var db=new JdbcTemplate(ds);
  db.execute("CREATE TABLE sys_organization(id BIGINT PRIMARY KEY)");db.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");db.execute("CREATE TABLE edu_student(id BIGINT PRIMARY KEY)");
  try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/V134__academic_class_and_enrollment_metadata.sql"));}
  db.update("INSERT INTO sys_organization VALUES(1),(2)");db.update("INSERT INTO edu_student VALUES(10),(11)");
  db.update("INSERT INTO edu_student_enrollment_metadata(student_id,institution_organization_id,student_number) VALUES(10,1,'20230101'),(10,2,'20230101')");
  assertThatThrownBy(()->db.update("INSERT INTO edu_student_enrollment_metadata(student_id,institution_organization_id,student_number) VALUES(11,1,'20230101')")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->db.update("INSERT INTO edu_student_enrollment_metadata(student_id,institution_organization_id,student_number) VALUES(11,2,'   ')")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->db.update("INSERT INTO edu_class_academic_metadata(class_organization_id,head_teacher_user_id) VALUES(1,999)")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  db.update("INSERT INTO sys_user VALUES(100)");
  db.update("INSERT INTO edu_class_academic_metadata(class_organization_id,head_teacher_user_id) VALUES(1,100)");
  assertThatThrownBy(()->db.update("INSERT INTO edu_class_academic_metadata(class_organization_id,head_teacher_user_id) VALUES(1,100)")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM edu_student_enrollment_metadata",Integer.class)).isEqualTo(2);
 }
}
