package com.lingdong.learning.student.application.transfer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import static org.junit.jupiter.api.Assertions.*;
class TransferMaterialRuleTest {
 @Test void actualRuleMigrationAddsOnlyTransferProofRuleAndExtensions(){
  var ds=new DriverManagerDataSource("jdbc:h2:mem:transfer_material_rule;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");var j=new JdbcTemplate(ds);j.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");
  new ResourceDatabasePopulator(new ClassPathResource("db/migration/V13__create_attachment_core_tables.sql"),new ClassPathResource("db/migration/V131__student_transfer_material_rule.sql")).execute(ds);
  assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM sys_attachment_rule WHERE module_code='STUDENT_TRANSFER' AND file_category='MATERIAL' AND max_batch_count=3 AND max_file_size_bytes=10485760 AND preview_enabled=0 AND download_scope='BUSINESS_AUTHORIZED' AND status='ENABLED'",Integer.class));
  assertEquals(java.util.List.of("jpeg","jpg","pdf","png"),j.queryForList("SELECT extension FROM sys_attachment_rule_extension ORDER BY extension",String.class));
 }
}
