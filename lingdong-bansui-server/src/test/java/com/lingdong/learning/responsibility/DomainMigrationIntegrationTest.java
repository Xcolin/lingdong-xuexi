package com.lingdong.learning.responsibility;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

/** Opt-in real MySQL test. No Spring context, server, cleanup, or production configuration. */
class DomainMigrationIntegrationTest {
    private static JdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static ResponsibilityStore store;
    private static final AtomicLong IDS = new AtomicLong(760000000000000000L +
            Math.abs(UUID.randomUUID().getMostSignificantBits() % 1000000000000L));
    private static final String ACTIONS = "'STUDENT_TRAINING_REQUEST_CREATE'," +
            "'STUDENT_TRAINING_REQUEST_REVIEW','STUDENT_TRAINING_PROGRAM_MANAGE'," +
            "'STUDENT_RESPONSIBILITY_READ','STUDENT_RESPONSIBILITY_TRANSFER'";

    @BeforeAll
    static void migrateExplicitIsolatedDatabase() {
        String host = required("LINGDONG_TEST_DB_HOST");
        String port = required("LINGDONG_TEST_DB_PORT");
        String schema = required("LINGDONG_TEST_DB_NAME");
        // Fail before opening any connection; no fallback to application-local or test defaults.
        if (!(host.equals("127.0.0.1") || host.equals("localhost")) ||
                !port.equals("33306") || !schema.equals("ld_codex_domains_20261027")) {
            throw new IllegalStateException("Requires explicitly isolated localhost:33306/ld_codex_domains_20261027");
        }
        var ds = new DriverManagerDataSource("jdbc:mysql://" + host + ":" + port + "/" + schema +
                "?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8",
                required("LINGDONG_TEST_DB_USER"), required("LINGDONG_TEST_DB_PASSWORD"));
        jdbc = new JdbcTemplate(ds);
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(schema);
        Flyway flyway = Flyway.configure().dataSource(ds).locations("classpath:db/migration")
                .target("152").cleanDisabled(true).baselineOnMigrate(false).load();
        flyway.migrate();
        flyway.validate();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("152");
        transactions = new TransactionTemplate(new DataSourceTransactionManager(ds));
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        store = new ResponsibilityStore(jdbc);
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Explicit environment required: " + key);
        return value;
    }

    @Test
    void allFiveTablesAndActionsHaveRealPageParents() {
        for (String table : new String[]{"org_training_program_registration", "edu_institution_training_request",
                "edu_training_participation", "edu_student_institution_responsibility", "edu_student_responsibility_transfer"}) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",
                    Integer.class, table)).as(table).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_permission WHERE permission_code IN (" + ACTIONS + ")", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE code IN (" + ACTIONS + ")", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_permission p JOIN sys_permission parent ON parent.id=p.parent_id " +
                "JOIN sys_menu m ON m.code=p.permission_code JOIN sys_menu mp ON mp.id=m.parent_id " +
                "WHERE p.permission_code IN (" + ACTIONS + ") AND p.resource_type='BUTTON' AND p.client_type='BOTH' " +
                "AND parent.resource_type='PAGE' AND mp.type='PAGE' AND mp.code=parent.permission_code " +
                "AND m.type='BUTTON' AND m.grantable=1", Integer.class)).isEqualTo(5);
    }

    @Test
    void mysqlEnforcesPendingAndActiveUniquenessButAllowsHistoricalRows() {
        Fixture f = fixture();
        long pending = request(f, "PENDING", 1);
        assertThatThrownBy(() -> request(f, "PENDING", 1)).isInstanceOf(DataIntegrityViolationException.class);
        request(f, "REJECTED", null);
        request(f, "REJECTED", null);
        assertThatThrownBy(() -> request(f, "PENDING", null))
                .satisfies(error -> assertCheckViolation(error, "edu_institution_training_request_chk_3"));
        long approved = request(f, "APPROVED", null);
        participation(f, approved, "ACTIVE", 1);
        long second = request(f, "APPROVED", null);
        assertThatThrownBy(() -> participation(f, second, "ACTIVE", 1)).isInstanceOf(DataIntegrityViolationException.class);
        participation(f, second, "LEFT", null);
        participation(f, request(f, "APPROVED", null), "LEFT", null);
        long invalidActive = request(f, "APPROVED", null);
        assertThatThrownBy(() -> participation(f, invalidActive, "ACTIVE", null))
                .satisfies(error -> assertCheckViolation(error, "edu_training_participation_chk_2"));
        assertThat(jdbc.queryForObject("SELECT reason FROM edu_institution_training_request WHERE id=?", String.class, pending))
                .isEqualTo("中文培训申请测试");
    }

    @Test
    void realStoreFirstAssignmentAndCasAndIndependentRollback() {
        Fixture f = fixture();
        assertThat(store.row(f.student, f.institution)).isNull();
        transactions.executeWithoutResult(tx -> {
            store.insert(f.student, f.institution, f.actor, f.actor);
            store.history(f.student, f.institution, null, f.actor, "OTHER", "首次指定负责人", f.actor, 0, 1);
        });
        assertThat(store.row(f.student, f.institution).version()).isEqualTo(1);
        assertThat(store.historyPage(f.student, f.institution, 10, 0)).singleElement().satisfies(h -> {
            assertThat(h.beforeVersionNo()).isZero();
            assertThat(h.afterVersionNo()).isEqualTo(1);
            assertThat(h.reasonText()).isEqualTo("首次指定负责人");
        });
        transactions.executeWithoutResult(tx -> {
            boolean changed = store.update(f.student, f.institution, f.actor, f.actor, 0);
            assertThat(changed).isFalse();
            // Same CAS gate as ResponsibilityService: never append history on a lost update.
            if (changed) store.history(f.student, f.institution, f.actor, f.actor, "OTHER", "过期请求", f.actor, 0, 1);
        });
        assertThat(store.historyCount(f.student, f.institution)).isEqualTo(1);
        assertThatThrownBy(() -> transactions.executeWithoutResult(tx -> {
            assertThat(store.update(f.student, f.institution, f.actor, f.actor, 1)).isTrue();
            // Real MySQL CHECK failure after the update must roll the entire independent transaction back.
            store.history(f.student, f.institution, f.actor, f.actor, "OTHER", " ", f.actor, 1, 2);
        })).satisfies(error -> assertCheckViolation(error, "ck_responsibility_other"));
        assertThat(store.row(f.student, f.institution).version()).isEqualTo(1);
        assertThat(store.historyCount(f.student, f.institution)).isEqualTo(1);
        transactions.executeWithoutResult(tx -> {
            assertThat(store.update(f.student, f.institution, f.actor, f.actor, 1)).isTrue();
            store.history(f.student, f.institution, f.actor, f.actor, "OTHER", "事务恢复后转交", f.actor, 1, 2);
        });
        assertThat(store.row(f.student, f.institution).version()).isEqualTo(2);
        assertThat(store.historyCount(f.student, f.institution)).isEqualTo(2);
    }

    private record Fixture(long actor, long student, long family, long institution, long program) {}

    private static void assertCheckViolation(Throwable error, String constraint) {
        assertThat(error).isInstanceOf(DataAccessException.class);
        Throwable cause = error;
        while (cause.getCause() != null && !(cause instanceof SQLException)) cause = cause.getCause();
        assertThat(cause).isInstanceOf(SQLException.class);
        SQLException sql = (SQLException) cause;
        assertThat(sql.getErrorCode()).isEqualTo(3819);
        assertThat(sql.getMessage()).contains("Check constraint '" + constraint + "' is violated");
    }

    private Fixture fixture() {
        long actor = IDS.incrementAndGet(), student = IDS.incrementAndGet();
        long family = organization("FAMILY", "中文测试家庭"), institution = organization("TRAINING_INSTITUTION", "中文测试培训机构");
        long program = organization("CLASS", "中文测试培训课程");
        jdbc.update("INSERT INTO sys_user(id,username,display_name,user_type) VALUES(?,?,?,'PLATFORM')", actor, "domain_" + actor, "中文测试负责人");
        jdbc.update("INSERT INTO edu_student(id,student_name) VALUES(?,?)", student, "中文测试学员");
        jdbc.update("INSERT INTO org_training_program_registration(program_organization_id,institution_organization_id,program_type,updated_by) VALUES(?,?,'OFFICIAL_COURSE',?)", program, institution, actor);
        return new Fixture(actor, student, family, institution, program);
    }

    private long organization(String type, String name) {
        long id = IDS.incrementAndGet();
        jdbc.update("INSERT INTO sys_organization(id,organization_code,organization_name,organization_type,organization_path) VALUES(?,?,?,?,?)",
                id, "DOMAIN_" + id, name + id, type, "/" + id + "/");
        return id;
    }

    private long request(Fixture f, String status, Integer guard) {
        long id = IDS.incrementAndGet();
        jdbc.update("INSERT INTO edu_institution_training_request(id,student_id,family_organization_id,institution_organization_id,program_organization_id,request_type,reason,status,pending_guard,requested_by) VALUES(?,?,?,?,?,'OFFICIAL_COURSE',?,?,?,?)",
                id, f.student, f.family, f.institution, f.program, "中文培训申请测试", status, guard, f.actor);
        return id;
    }

    private void participation(Fixture f, long request, String status, Integer guard) {
        jdbc.update("INSERT INTO edu_training_participation(id,training_request_id,student_id,program_organization_id,institution_organization_id,status,active_guard,joined_at) VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                IDS.incrementAndGet(), request, f.student, f.program, f.institution, status, guard);
    }
}
