package com.lingdong.learning.teacher.application;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import com.lingdong.learning.user.domain.UserStatus;
class TeacherCampusQueryContractTest {
 @Test void campusIsExplicitOptionalAndLegacyQueryRemainsCompatible() throws Exception {
  var type=TeacherQuery.class;var query=type.getConstructor(String.class,Long.class,Long.class,UserStatus.class,int.class,int.class,Long.class).newInstance(null,2L,null,null,1,20,3L);
  assertThat(type.getMethod("campusOrganizationId").invoke(query)).isEqualTo(3L);
  var legacy=new TeacherQuery(null,2L,null,null,1,20);assertThat(type.getMethod("campusOrganizationId").invoke(legacy)).isNull();
 }
}
