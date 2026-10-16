package com.lingdong.learning.teacher.application;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
class TeacherSubjectBatchStoreTest {
 @Test void bulkReadUsesExactPairsInsteadOfCrossProduct() throws Exception {
  var type=com.lingdong.learning.teacher.infrastructure.persistence.TeacherSubjectStore.class;
  var key=Class.forName(type.getName()+"$Key");var read=type.getMethod("readBatch",List.class);
  var ds=new org.h2.jdbcx.JdbcDataSource();ds.setURL("jdbc:h2:mem:subject_batch_"+System.nanoTime()+";MODE=MySQL;DB_CLOSE_DELAY=-1");var db=new org.springframework.jdbc.core.JdbcTemplate(ds);
  db.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");db.execute("CREATE TABLE sys_organization(id BIGINT PRIMARY KEY)");
  try(var c=ds.getConnection()){org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(c,new org.springframework.core.io.ClassPathResource("db/migration/V135__teacher_subject_metadata.sql"));}
  db.update("INSERT INTO sys_user VALUES(1),(2)");db.update("INSERT INTO sys_organization VALUES(10),(20)");
  var store=new com.lingdong.learning.teacher.infrastructure.persistence.TeacherSubjectStore(db);store.insert(1,10,"MATH");store.insert(1,20,"ART");store.insert(2,10,"SECRET");store.insert(2,20,null);
  var first=key.getConstructors()[0].newInstance(1L,10L);var second=key.getConstructors()[0].newInstance(2L,20L);
  var values=(List<?>)read.invoke(store,List.of(first,second));assertThat(values).hasSize(2);
  assertThat(values.toString()).contains("MATH").doesNotContain("ART","SECRET");assertThat((List<?>)read.invoke(store,List.of())).isEmpty();
 }
}
