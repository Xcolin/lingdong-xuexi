package com.lingdong.learning.growthpoint.application;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V100 家庭奖励分类/库存/图片补列在 H2 MySQL 模式下的迁移与约束验证。 */
class GrowthRewardCategoryStockMigrationTest {

    private JdbcTemplate db() throws Exception {
        var ds = new DriverManagerDataSource(
                "jdbc:h2:mem:reward_category_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection c = ds.getConnection()) {
            var jdbc = new JdbcTemplate(ds);
            jdbc.execute("CREATE TABLE edu_student (id BIGINT PRIMARY KEY)");
            jdbc.execute("CREATE TABLE sys_user (id BIGINT PRIMARY KEY)");
            jdbc.execute("CREATE TABLE growth_point_ledger (id BIGINT PRIMARY KEY, change_type VARCHAR(32), "
                    + "source_assignment_id BIGINT, source_type VARCHAR(32), source_organization_id BIGINT, "
                    + "reviewer_user_id BIGINT, amount BIGINT, available_delta BIGINT, correction_of_id BIGINT, "
                    + "CONSTRAINT ck_growth_point_ledger_amount CHECK (amount <> 0))");
            jdbc.execute("CREATE TABLE sys_feature_toggle (id BIGINT PRIMARY KEY, feature_code VARCHAR(64), "
                    + "feature_name VARCHAR(128), scope_type VARCHAR(32), scope_key VARCHAR(64), "
                    + "status VARCHAR(16), built_in INT, description VARCHAR(500))");
            jdbc.execute("CREATE TABLE sys_permission (id BIGINT PRIMARY KEY, permission_code VARCHAR(64), "
                    + "permission_name VARCHAR(128), resource_type VARCHAR(32), client_type VARCHAR(16), "
                    + "parent_id BIGINT, sort_order INT, status VARCHAR(16), description VARCHAR(500))");
            jdbc.execute("CREATE TABLE sys_role_permission (id BIGINT PRIMARY KEY, role_id BIGINT, permission_id BIGINT)");
            jdbc.execute("INSERT INTO edu_student (id) VALUES (100)");
            jdbc.execute("INSERT INTO sys_user (id) VALUES (200)");
            ScriptUtils.executeSqlScript(c, new ClassPathResource("db/migration/V27__add_reward_exchange.sql"));
            // 模拟存量数据：V100 回填只作用于迁移时已存在的行
            jdbc.execute("INSERT INTO growth_reward (id, student_id, created_by_parent_id, reward_name, "
                    + "required_points, status, version_no) VALUES (2, 100, 200, '旧数据', 50, 'ONLINE', 0)");
            ScriptUtils.executeSqlScript(c, new ClassPathResource(
                    "db/migration/V141__growth_reward_category_stock_image.sql"));
        }
        return new JdbcTemplate(ds);
    }

    private void insertReward(JdbcTemplate db, long id, String category, Integer stock, String imageUrl) {
        db.update(
                "INSERT INTO growth_reward (id, student_id, created_by_parent_id, reward_name, "
                        + "required_points, category, stock_quantity, image_url, status, version_no) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ONLINE', 0)",
                id, 100L, 200L, "测试奖励", 100L, category, stock, imageUrl);
    }

    @Test
    void newColumnsExistWithDefaultsAndBackfill() throws Exception {
        JdbcTemplate db = db();
        insertReward(db, 1L, "STUDY_SUPPLIES", 20, "https://cdn.example.com/a.png");

        Map<String, Object> row = db.queryForMap(
                "SELECT category, stock_quantity, image_url FROM growth_reward WHERE id = 1");
        assertThat(row.get("category")).isEqualTo("STUDY_SUPPLIES");
        assertThat(((Number) row.get("stock_quantity")).intValue()).isEqualTo(20);
        assertThat(row.get("image_url")).isEqualTo("https://cdn.example.com/a.png");

        Map<String, Object> legacy = db.queryForMap(
                "SELECT category, stock_quantity, image_url FROM growth_reward WHERE id = 2");
        assertThat(legacy.get("category")).isEqualTo("LIFE_GOODS");
        assertThat(((Number) legacy.get("stock_quantity")).intValue()).isZero();
        assertThat(legacy.get("image_url")).isNull();
    }

    @Test
    void rejectsUnknownCategoryAndNegativeStock() throws Exception {
        JdbcTemplate db = db();
        assertThatThrownBy(() -> insertReward(db, 3L, "TOY", 10, null))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertReward(db, 4L, "STUDY_SUPPLIES", -1, null))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(db.queryForObject(
                "SELECT COUNT(*) FROM growth_reward WHERE id IN (3, 4)", Long.class)).isZero();
    }

    @Test
    void onlineRewardsQueryableWithNewColumns() throws Exception {
        JdbcTemplate db = db();
        insertReward(db, 5L, "EXPERIENCE", 3, null);
        List<Map<String, Object>> rows = db.queryForList(
                "SELECT id, category, stock_quantity FROM growth_reward "
                        + "WHERE student_id = 100 AND status = 'ONLINE' AND category = 'EXPERIENCE' "
                        + "AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP) "
                        + "ORDER BY required_points");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("category")).isEqualTo("EXPERIENCE");
        assertThat(((Number) rows.get(0).get("stock_quantity")).intValue()).isEqualTo(3);
    }
}
