package com.lingdong.learning.iam.application;

import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.domain.RoleDataScope;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class RoleApplicationServiceTest {
    @Autowired
    private RoleApplicationService roleApplicationService;

    @Autowired
    private RoleMapper roleMapper;

    @Test
    void createsCustomRoleWithConfiguredDataScope() {
        roleApplicationService.createCustomRole(new CreateCustomRoleCommand(
                "OPS_VIEWER",
                "运维查看角色",
                "可查看已授权范围内的运维信息",
                RoleDataScope.CUSTOM
        ));

        Role role = roleMapper.findByCode("OPS_VIEWER");

        assertThat(role.code()).isEqualTo("OPS_VIEWER");
        assertThat(Long.toString(role.id())).hasSize(19);
        assertThat(role.dataScope()).isEqualTo(RoleDataScope.CUSTOM);
        assertThat(role.builtIn()).isFalse();
    }

    @Test
    void rejectsDuplicateCustomRoleCode() {
        CreateCustomRoleCommand command = new CreateCustomRoleCommand(
                "OPS_AUDITOR",
                "运维审计角色",
                "可查看已授权范围内的运维信息",
                RoleDataScope.CUSTOM
        );

        roleApplicationService.createCustomRole(command);

        assertThatThrownBy(() -> roleApplicationService.createCustomRole(command))
                .isInstanceOf(DuplicateRoleCodeException.class);
    }

    @Test
    void rejectsRoleCodeOutsideThePlatformConvention() {
        CreateCustomRoleCommand command = new CreateCustomRoleCommand(
                "ops-viewer",
                "非法编码角色",
                null,
                RoleDataScope.SELF
        );

        assertThatThrownBy(() -> roleApplicationService.createCustomRole(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("大写字母");
    }
}
