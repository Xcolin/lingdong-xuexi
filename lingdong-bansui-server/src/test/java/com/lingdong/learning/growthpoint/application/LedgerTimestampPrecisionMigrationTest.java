package com.lingdong.learning.growthpoint.application;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class LedgerTimestampPrecisionMigrationTest {
 @Test void preservesSubsecondEventTime() throws Exception {
  var ds=new org.h2.jdbcx.JdbcDataSource();ds.setURL("jdbc:h2:mem:ledger_precision_"+System.nanoTime()+";MODE=MySQL");
  try(var c=ds.getConnection()){
   var db=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(c,true));
   db.execute("CREATE TABLE growth_point_ledger(id BIGINT PRIMARY KEY,occurred_at TIMESTAMP(0) NOT NULL)");
   db.update("INSERT INTO growth_point_ledger VALUES(1,TIMESTAMP '2026-10-21 09:00:00')");
   org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(c,new org.springframework.core.io.ClassPathResource("db/migration/V149__preserve_point_ledger_timestamp_precision.sql"));
   var event=java.time.LocalDateTime.of(2026,10,21,10,0,0,900000000);
   db.update("INSERT INTO growth_point_ledger VALUES(2,?)",event);
   assertThat(db.queryForObject("SELECT occurred_at FROM growth_point_ledger WHERE id=2",java.time.LocalDateTime.class)).isEqualTo(event);
   assertThat(db.queryForObject("SELECT COUNT(*) FROM growth_point_ledger",Integer.class)).isEqualTo(2);
  }
 }
}
