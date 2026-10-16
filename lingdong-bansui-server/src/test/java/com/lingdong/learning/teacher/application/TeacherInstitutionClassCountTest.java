package com.lingdong.learning.teacher.application;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import com.lingdong.learning.teacher.infrastructure.persistence.TeacherSubjectStore;
class TeacherInstitutionClassCountTest {
 @Test void countsDistinctActiveUnendedClassesInExactInstitutions() throws Exception {
  var method=TeacherSubjectStore.class.getMethod("readClassCounts",List.class);
  var ds=new org.h2.jdbcx.JdbcDataSource();ds.setURL("jdbc:h2:mem:teacher_counts_"+System.nanoTime()+";MODE=MySQL;DB_CLOSE_DELAY=-1");var db=new org.springframework.jdbc.core.JdbcTemplate(ds);
  db.execute("CREATE TABLE sys_organization(id BIGINT PRIMARY KEY,organization_type VARCHAR(30),organization_path VARCHAR(100))");
  db.execute("CREATE TABLE edu_teacher_class(teacher_user_id BIGINT,class_organization_id BIGINT,status VARCHAR(30),effective_to TIMESTAMP)");
  db.update("INSERT INTO sys_organization VALUES(10,'SCHOOL','/10/'),(20,'TRAINING_INSTITUTION','/20/'),(11,'CLASS','/10/11/'),(12,'CLASS','/10/12/'),(13,'CLASS','/10/13/'),(21,'CLASS','/20/21/'),(14,'GRADE','/10/14/')");
  db.update("INSERT INTO edu_teacher_class VALUES(1,11,'ACTIVE',NULL),(1,11,'ACTIVE',NULL),(1,12,'INACTIVE',NULL),(1,13,'ACTIVE',CURRENT_TIMESTAMP),(1,21,'ACTIVE',NULL),(1,14,'ACTIVE',NULL),(2,11,'ACTIVE',NULL)");
  var store=new TeacherSubjectStore(db);var a=new TeacherSubjectStore.Key(1,10);var b=new TeacherSubjectStore.Key(1,20);var other=new TeacherSubjectStore.Key(2,20);
  var counts=(Map<?,?>)method.invoke(store,List.of(a,b,other));assertThat(counts.get(a)).isEqualTo(1);assertThat(counts.get(b)).isEqualTo(1);assertThat(counts.get(other)).isNull();
  assertThat((Map<?,?>)method.invoke(store,List.of())).isEmpty();
 }
}
