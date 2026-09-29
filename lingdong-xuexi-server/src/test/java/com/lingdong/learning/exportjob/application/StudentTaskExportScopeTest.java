package com.lingdong.learning.exportjob.application;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class StudentTaskExportScopeTest {
 @Test void auditorMixedRoleIsDeniedAndRolePriorityIsExplicit() {
  assertThatThrownBy(() -> StudentTaskExportAccessService.role(List.of("PARENT","SYS_AUDITOR"))).isInstanceOf(RuntimeException.class);
  assertThat(StudentTaskExportAccessService.role(List.of("PARENT","TEACHER","ORG_ADMIN"))).isEqualTo("ORG_ADMIN");
  assertThat(StudentTaskExportAccessService.role(List.of("PARENT","TEACHER"))).isEqualTo("TEACHER");
  assertThat(StudentTaskExportAccessService.role(List.of("PARENT"))).isEqualTo("PARENT");
  assertThatThrownBy(() -> StudentTaskExportAccessService.role(List.of("SYS_ADMIN"))).isInstanceOf(RuntimeException.class);
 }
}
