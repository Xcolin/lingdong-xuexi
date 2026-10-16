package com.lingdong.learning.student.application;
import org.junit.jupiter.api.Test;import org.springframework.jdbc.core.JdbcTemplate;import com.lingdong.learning.student.infrastructure.persistence.AcademicMetadataStore;import static org.assertj.core.api.Assertions.*;
class HeadTeacherOptionsStoreTest {
 @Test void actualSqlReturnsOnlyEnabledCurrentClassTeachers(){var ds=new org.h2.jdbcx.JdbcDataSource();ds.setURL("jdbc:h2:mem:head_options;MODE=MySQL;DB_CLOSE_DELAY=-1");var db=new JdbcTemplate(ds);
 db.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY,display_name VARCHAR(64),status VARCHAR(16))");db.execute("CREATE TABLE edu_teacher_profile(user_id BIGINT PRIMARY KEY,status VARCHAR(16))");db.execute("CREATE TABLE edu_teacher_class(teacher_user_id BIGINT,class_organization_id BIGINT,status VARCHAR(16),effective_to TIMESTAMP)");
 db.update("INSERT INTO sys_user VALUES(100,'valid','ENABLED'),(101,'disabled profile','ENABLED'),(102,'other class','ENABLED'),(103,'disabled user','DISABLED'),(104,'ended','ENABLED')");db.update("INSERT INTO edu_teacher_profile VALUES(100,'ENABLED'),(101,'DISABLED'),(102,'ENABLED'),(103,'ENABLED'),(104,'ENABLED')");db.update("INSERT INTO edu_teacher_class VALUES(100,3,'ACTIVE',NULL),(101,3,'ACTIVE',NULL),(102,4,'ACTIVE',NULL),(103,3,'ACTIVE',NULL),(104,3,'ACTIVE',CURRENT_TIMESTAMP)");
 var options=new AcademicMetadataStore(db).headTeacherOptions(3L);assertThat(options).hasSize(1);assertThat(options.get(0).userId()).isEqualTo(100L);assertThat(options.get(0).classId()).isEqualTo(3L);assertThat(options.get(0).displayName()).isEqualTo("valid");}
}
