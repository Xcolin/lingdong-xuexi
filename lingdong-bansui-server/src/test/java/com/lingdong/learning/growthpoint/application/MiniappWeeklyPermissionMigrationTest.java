package com.lingdong.learning.growthpoint.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

/** 验证新增权限独立归属小程序，且不会向其他内置角色扩散。 */
@SpringBootTest
@ActiveProfiles("test")
class MiniappWeeklyPermissionMigrationTest {
    @Autowired JdbcTemplate jdbc;
    @Test void grantsMiniappReadOnlyPermissionExclusivelyToParent() {
        assertThat(jdbc.queryForList("""
                select client_type from sys_permission
                where permission_code='MINIAPP_GROWTH_REVIEW_READ_CHILD' and status='ENABLED'
                """, String.class)).containsExactly("MINIAPP");
        assertThat(jdbc.queryForList("""
                select r.role_code from sys_role r
                join sys_role_permission rp on rp.role_id=r.id
                join sys_permission p on p.id=rp.permission_id
                where p.permission_code='MINIAPP_GROWTH_REVIEW_READ_CHILD'
                """, String.class)).containsExactly("PARENT");
        assertThat(jdbc.queryForObject("""
                select client_type from sys_permission where permission_code='GROWTH_REVIEW_READ_CHILD'
                """, String.class)).isEqualTo("WEB");
    }
}
