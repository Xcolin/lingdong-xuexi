package com.lingdong.learning.user.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserMobileChangePersistenceTest {
    @Autowired private UserMapper userMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void followsMobileForDefaultUsernameAndPreservesCustomUsername() {
        insertUser(1874244142494646711L, "13800138011", "13800138011");
        insertUser(1874244142494646712L, "parent-custom-name", "13800138012");

        assertThat(userMapper.updateMobileIfExpected(
                1874244142494646711L, "13800138011", "13900139011")).isEqualTo(1);
        assertThat(userMapper.updateMobileIfExpected(
                1874244142494646712L, "13800138012", "13900139012")).isEqualTo(1);

        assertThat(username(1874244142494646711L)).isEqualTo("13900139011");
        assertThat(username(1874244142494646712L)).isEqualTo("parent-custom-name");
    }

    private void insertUser(Long id, String username, String mobile) {
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, username, display_name, mobile, user_type, status)
                VALUES (?, ?, '家长用户', ?, 'FAMILY', 'ENABLED')
                """, id, username, mobile);
    }

    private String username(Long id) {
        return jdbcTemplate.queryForObject(
                "SELECT username FROM sys_user WHERE id = ?", String.class, id);
    }
}
