package com.lingdong.learning.exportjob.application;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class StudentTaskExportScopeTest {
 @Test void relationshipPriorityAndCustomOrganizationModeAreExplicit() {
  assertThat(StudentTaskExportAccessService.role(List.of("PARENT","SYS_AUDITOR"))).isEqualTo("PARENT");
  assertThat(StudentTaskExportAccessService.role(List.of("PARENT","TEACHER","ORG_ADMIN"))).isEqualTo("ORG_ADMIN");
  assertThat(StudentTaskExportAccessService.role(List.of("PARENT","TEACHER"))).isEqualTo("TEACHER");
  assertThat(StudentTaskExportAccessService.role(List.of("PARENT"))).isEqualTo("PARENT");
  assertThat(StudentTaskExportAccessService.role(List.of("ALL_ROLE_TEST"))).isEqualTo("ORG_ADMIN");
 }
}
