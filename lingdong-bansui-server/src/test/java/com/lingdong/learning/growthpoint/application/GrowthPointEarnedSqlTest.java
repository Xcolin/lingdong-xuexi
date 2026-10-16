package com.lingdong.learning.growthpoint.application;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;
class GrowthPointEarnedSqlTest {
 @Test void sumsOnlyPositiveTaskRewardsForStudentAndHalfOpenPeriod() throws Exception {
  String xml=Files.readString(Path.of("src/main/resources/mapper/growthpoint/GrowthPointQueryMapper.xml"));
  String sql=xml.split("<select id=\"sumTaskRewards\" resultType=\"long\">")[1].split("</select>")[0].replace("&gt;",">").replace("&lt;","<").replace("#{studentId}","1").replace("#{start}","'2026-02-01 00:00:00'").replace("#{end}","'2026-02-02 00:00:00'");
  try(var c=DriverManager.getConnection("jdbc:h2:mem:point_earnings");var st=c.createStatement()) {
   st.execute("CREATE TABLE growth_point_ledger(student_id BIGINT,change_type VARCHAR(30),amount BIGINT,occurred_at TIMESTAMP)");
   st.execute("INSERT INTO growth_point_ledger VALUES (1,'TASK_REWARD',20,'2026-02-01 00:00:00'),(1,'TASK_REWARD',-5,'2026-02-01 12:00:00'),(1,'CORRECTION',99,'2026-02-01 12:00:00'),(1,'REDEMPTION',-10,'2026-02-01 12:00:00'),(1,'DORMANCY_CLEAR',-10,'2026-02-01 12:00:00'),(2,'TASK_REWARD',100,'2026-02-01 12:00:00'),(1,'TASK_REWARD',30,'2026-02-02 00:00:00')");
   try(var r=st.executeQuery(sql)){r.next();assertThat(r.getLong(1)).isEqualTo(20);}
   st.execute("DELETE FROM growth_point_ledger");
   try(var r=st.executeQuery(sql)){r.next();assertThat(r.getLong(1)).isZero();}
  }
 }
}
