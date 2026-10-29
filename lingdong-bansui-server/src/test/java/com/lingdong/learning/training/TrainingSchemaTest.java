package com.lingdong.learning.training;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.junit.jupiter.api.Assertions.*;
class TrainingSchemaTest {
 @Test void migrationHasIndependentParticipationAndBothUniquenessGuards() throws Exception {
  var resource=new ClassPathResource("db/migration/V150__institution_training.sql");
  assertTrue(resource.exists(),"培训独立数据域尚未实现");
  String sql=resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
  assertTrue(sql.contains("UNIQUE(student_id,program_organization_id,pending_guard)"));
  assertTrue(sql.contains("UNIQUE(student_id,program_organization_id,active_guard)"));
  assertFalse(sql.contains("UPDATE edu_student "));
 }
 @Test void endpointsAreImplementedSeparatelyFromEnrollment() throws Exception {
  assertNotNull(Class.forName("com.lingdong.learning.training.TrainingController"));
 }
}
