package com.lingdong.learning.teacher.application;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class TeacherSubjectAuditTest {
 @Test void auditRetainsActorBeforeAfterAndUniqueVersion() throws Exception {
  var type=Class.forName("com.lingdong.learning.teacher.infrastructure.persistence.TeacherSubjectStore");
  var audit=type.getMethod("recordAudit",long.class,long.class,int.class,long.class,String.class,String.class);
  var ds=new org.h2.jdbcx.JdbcDataSource();ds.setURL("jdbc:h2:mem:subject_audit_"+System.nanoTime()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
  var db=new org.springframework.jdbc.core.JdbcTemplate(ds);
  db.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");db.execute("CREATE TABLE sys_organization(id BIGINT PRIMARY KEY)");
  try(var c=ds.getConnection()){org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(c,new org.springframework.core.io.ClassPathResource("db/migration/V135__teacher_subject_metadata.sql"));}
  db.update("INSERT INTO sys_user VALUES(1),(2)");db.update("INSERT INTO sys_organization VALUES(10)");
  var store=type.getConstructor(org.springframework.jdbc.core.JdbcTemplate.class).newInstance(db);
  audit.invoke(store,1L,10L,1,2L,null,"MATH");audit.invoke(store,1L,10L,2,2L,"MATH",null);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM edu_teacher_subject_audit",Integer.class)).isEqualTo(2);
  assertThat(db.queryForObject("SELECT operator_user_id FROM edu_teacher_subject_audit WHERE version_no=1",Long.class)).isEqualTo(2L);
  assertThat(db.queryForObject("SELECT before_subject_code FROM edu_teacher_subject_audit WHERE version_no=1",String.class)).isNull();
  assertThat(db.queryForObject("SELECT after_subject_code FROM edu_teacher_subject_audit WHERE version_no=2",String.class)).isNull();
  assertThatThrownBy(()->audit.invoke(store,1L,10L,2,2L,"MATH","ART")).hasCauseInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
 }
}
