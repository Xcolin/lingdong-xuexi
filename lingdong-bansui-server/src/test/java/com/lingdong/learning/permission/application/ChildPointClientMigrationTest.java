package com.lingdong.learning.permission.application;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ChildPointClientMigrationTest {
 @Test void enablesOnlyChildReadForBothClientsWithoutChangingGrants() throws Exception {
  var ds=new org.h2.jdbcx.JdbcDataSource();ds.setURL("jdbc:h2:mem:child_client_"+System.nanoTime()+";MODE=MySQL");
  try(var connection=ds.getConnection()){
   var db=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));
   db.execute("CREATE TABLE sys_permission(permission_code VARCHAR(100) PRIMARY KEY,client_type VARCHAR(20),status VARCHAR(20))");
   db.update("INSERT INTO sys_permission VALUES('GROWTH_POINT_READ_CHILD','WEB','ENABLED'),('GROWTH_POINT_CORRECT_CHILD','WEB','ENABLED'),('OTHER','MINIAPP','DISABLED')");
   var script=new org.springframework.core.io.ClassPathResource("db/migration/V148__child_point_read_miniapp_client.sql");
   org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,script);
   org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,script);
   assertThat(db.queryForObject("SELECT client_type FROM sys_permission WHERE permission_code='GROWTH_POINT_READ_CHILD'",String.class)).isEqualTo("BOTH");
   assertThat(db.queryForObject("SELECT client_type FROM sys_permission WHERE permission_code='GROWTH_POINT_CORRECT_CHILD'",String.class)).isEqualTo("WEB");
   assertThat(db.queryForObject("SELECT status FROM sys_permission WHERE permission_code='OTHER'",String.class)).isEqualTo("DISABLED");
  }
 }
}
