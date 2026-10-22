package com.lingdong.learning.testsupport;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class MysqlIsolatedSchemaCleanupTest {
 @Test void onlyExactGeneratedTestSchemaNamesAreAccepted(){
  var name="reward_exchange_0123456789abcdef0123456789abcdef_1";
  assertThat(MysqlIsolatedSchemaCleanup.dropSql(name)).isEqualTo("DROP DATABASE IF EXISTS `"+name+"`");
  for(String invalid:new String[]{"lingdong_learning","reward_exchange_","reward_exchange_%","feature_management_bad`name","system_task_query_foo"})
   assertThatThrownBy(()->MysqlIsolatedSchemaCleanup.dropSql(invalid)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void cleanupExecutesOnlyExactTargetsAndContinuesAfterFailure() throws Exception {
  var connection=org.mockito.Mockito.mock(java.sql.Connection.class);
  var first=org.mockito.Mockito.mock(java.sql.Statement.class);var second=org.mockito.Mockito.mock(java.sql.Statement.class);
  org.mockito.Mockito.when(connection.createStatement()).thenReturn(first,second);
  var a="feature_management_0123456789abcdef0123456789abcdef";
  var b="system_task_query_abcdef0123456789abcdef0123456789";
  org.mockito.Mockito.when(first.execute(MysqlIsolatedSchemaCleanup.dropSql(a))).thenThrow(new java.sql.SQLException("fixture"));
  MysqlIsolatedSchemaCleanup.cleanup(connection,java.util.List.of(a,b));
  org.mockito.Mockito.verify(first).execute(MysqlIsolatedSchemaCleanup.dropSql(a));
  org.mockito.Mockito.verify(second).execute(MysqlIsolatedSchemaCleanup.dropSql(b));
  org.mockito.Mockito.verify(first).close();org.mockito.Mockito.verify(second).close();
  org.mockito.Mockito.verify(connection,org.mockito.Mockito.times(2)).createStatement();
  org.mockito.Mockito.verifyNoMoreInteractions(connection,first,second);
 }
 @Test void invalidTargetsFailBeforeAnyStatementIsOpened() throws Exception {
  var connection=org.mockito.Mockito.mock(java.sql.Connection.class);
  assertThatThrownBy(()->MysqlIsolatedSchemaCleanup.cleanup(connection,java.util.List.of("feature_management_0123456789abcdef0123456789abcdef","lingdong_learning"))).isInstanceOf(IllegalArgumentException.class);
  org.mockito.Mockito.verifyNoInteractions(connection);
 }
}
