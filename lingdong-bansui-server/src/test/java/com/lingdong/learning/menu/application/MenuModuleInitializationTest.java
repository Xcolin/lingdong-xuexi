package com.lingdong.learning.menu.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest @ActiveProfiles("test")
class MenuModuleInitializationTest {
    @Autowired JdbcTemplate jdbc;
    @Test void modulesContainRequestedPagesAndNoObsoletePermissionCreation() {
        assertThat(jdbc.queryForList("SELECT m.route FROM sys_menu m JOIN sys_menu p ON p.id=m.parent_id WHERE p.code='permission-management' ORDER BY m.sort_order",String.class))
            .containsExactly("/organizations","/users","/iam","/menu-management");
        assertThat(jdbc.queryForList("SELECT m.route FROM sys_menu m JOIN sys_menu p ON p.id=m.parent_id WHERE p.code='system-settings' ORDER BY m.sort_order",String.class))
            .containsExactly("/dictionaries","/cache-management","/feature-management");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu m JOIN sys_menu p ON p.id=m.parent_id WHERE p.code='import-export-management'",Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE code IN ('IAM_PERMISSION_CREATE','iam.iam-management-page.2','iam.iam-management-page.8','iam.iam-management-page.9','organizations.node.members')",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_permission WHERE permission_code='IAM_PERMISSION_CREATE'",Integer.class)).isZero();
    }
    @Test void everyPageAndButtonHasMatchingPermissionMetadataAndParent() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu m LEFT JOIN sys_permission p ON p.permission_code=m.code WHERE m.type IN ('PAGE','BUTTON') AND (p.id IS NULL OR m.permission_code<>m.code OR m.grantable<>1 OR p.permission_name<>m.name OR p.resource_type<>m.type OR p.status<>m.status)",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu m JOIN sys_menu pm ON pm.id=m.parent_id JOIN sys_permission p ON p.permission_code=m.code JOIN sys_permission pp ON pp.permission_code=pm.code WHERE m.type='BUTTON' AND (p.parent_id IS NULL OR p.parent_id<>pp.id)",Integer.class)).isZero();
    }
}
