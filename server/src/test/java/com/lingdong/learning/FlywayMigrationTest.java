package com.lingdong.learning;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsSystemConfigurationTableThroughFlyway() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'sys_config'",
                Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void createsSixBuiltInRolesThroughFlyway() {
        Integer builtInRoleCount = jdbcTemplate.queryForObject(
                "select count(*) from sys_role where role_type = 'BUILT_IN' and status = 'ENABLED'",
                Integer.class);
        String auditorName = jdbcTemplate.queryForObject(
                "select role_name from sys_role where role_code = 'SYS_AUDITOR'",
                String.class);

        assertThat(builtInRoleCount).isEqualTo(6);
        assertThat(auditorName).isEqualTo("系统审核员");
    }

    @Test
    void createsFiveBuiltInOrganizationTypesThroughFlyway() {
        Integer organizationTypeCount = jdbcTemplate.queryForObject(
                "select count(*) from sys_organization_type where built_in = 1 and status = 'ENABLED'",
                Integer.class);

        assertThat(organizationTypeCount).isEqualTo(5);
    }

    @Test
    void createsUserOrganizationRelationTableThroughFlyway() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'sys_user_organization'",
                Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void createsSystemTaskAuditTableThroughFlyway() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'sys_system_task'",
                Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void createsDictionaryTablesThroughFlyway() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name in ('sys_dictionary_type', 'sys_dictionary_item')",
                Integer.class);

        assertThat(count).isEqualTo(2);
    }
}
