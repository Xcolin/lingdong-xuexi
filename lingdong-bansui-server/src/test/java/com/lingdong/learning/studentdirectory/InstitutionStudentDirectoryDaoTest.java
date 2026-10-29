package com.lingdong.learning.studentdirectory;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class InstitutionStudentDirectoryDaoTest {
 @Test void pagesDistinctInstitutionEnrollmentsAndExcludesCancelledAndForeignStudents() {
  var ds=new DriverManagerDataSource("jdbc:h2:mem:directory;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
  var jdbc=new NamedParameterJdbcTemplate(ds);var sql=jdbc.getJdbcTemplate();
  sql.execute("CREATE TABLE edu_student(id BIGINT PRIMARY KEY,student_name VARCHAR(100),status VARCHAR(20))");
  sql.execute("CREATE TABLE edu_student_organization(student_id BIGINT,organization_id BIGINT,relation_type VARCHAR(20),status VARCHAR(20))");
  sql.execute("CREATE TABLE edu_student_enrollment_metadata(student_id BIGINT,institution_organization_id BIGINT,student_number VARCHAR(50))");
  sql.execute("INSERT INTO edu_student VALUES(1,'小禾','ENABLED'),(2,'小林','ENABLED'),(3,'小乐','CANCELLED'),(4,'小星','ENABLED')");
  sql.execute("INSERT INTO edu_student_organization VALUES(1,10,'ENROLLMENT','ACTIVE'),(1,10,'ENROLLMENT','ACTIVE'),(1,11,'CLASS','ACTIVE'),(1,12,'CLASS','ACTIVE'),(2,10,'ENROLLMENT','INACTIVE'),(3,10,'ENROLLMENT','ACTIVE'),(4,20,'ENROLLMENT','ACTIVE')");
  var dao=new InstitutionStudentDirectoryDao(jdbc);
  var query=new InstitutionStudentDirectoryDao.Query(10L,List.of(11L,12L),true,null,null,"ALL");
  assertThat(dao.count(query)).isEqualTo(2);
  assertThat(dao.page(query,1,1)).extracting(InstitutionStudentDirectoryDao.Row::studentId).containsExactly(1L);
  assertThat(dao.count(new InstitutionStudentDirectoryDao.Query(10L,List.of(11L),false,null,null,"ALL"))).isEqualTo(1);
  assertThat(dao.count(new InstitutionStudentDirectoryDao.Query(10L,List.of(),false,null,null,"ALL"))).isZero();
  assertThat(dao.count(new InstitutionStudentDirectoryDao.Query(10L,List.of(11L,12L),true,null,null,"INACTIVE"))).isEqualTo(1);
 }
}
