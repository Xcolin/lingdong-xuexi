package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GrowthReviewSubscriptionTest {
    private static final long PARENT = 1874244142494661101L, STUDENT = 1874244142494661102L;
    @Autowired JdbcTemplate jdbc;
    @Autowired GrowthReviewSubscriptionService service;
    @Autowired GrowthReviewSubscriptionQueueService queue;
    @Autowired com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewDeliveryMapper deliveries;
    @Autowired org.mybatis.spring.SqlSessionTemplate session;
    private AuthenticatedUser parent() { return new AuthenticatedUser(PARENT, PARENT + 1, "subscription_parent", "测试家长", AuthClientType.WEB, List.of("PARENT")); }
    @BeforeEach void prepare() {
        jdbc.update("insert into sys_user(id,username,display_name,user_type,status) values (?, 'subscription_parent','测试家长','FAMILY','ENABLED')", PARENT);
        jdbc.update("insert into edu_student(id,student_name,status) values (?, '测试孩子','ENABLED')", STUDENT);
        jdbc.update("insert into sys_user_role(id,user_id,role_id,organization_scope_key) select ?,?,id,'GLOBAL' from sys_role where role_code='PARENT'", PARENT + 10, PARENT);
        jdbc.update("insert into edu_parent_student(id,parent_user_id,student_id,relation_role,status,primary_scope_key) values (?,?,?,'SECONDARY_GUARDIAN','ACTIVE','SECONDARY')", PARENT + 11, PARENT, STUDENT);
        jdbc.update("update sys_feature_toggle set status='ENABLED' where feature_code='GROWTH_REVIEW_WEEKLY_SUBSCRIPTION'");
    }
    @Test void defaultsOffAndChangesIdempotentlyWithNineteenDigitKey() {
        assertThat(service.get(parent(), STUDENT).enabled()).isFalse();
        var enabled = service.set(parent(), STUDENT, true, 0);
        assertThat(enabled.enabled()).isTrue();
        assertThat(enabled.version()).isEqualTo(1);
        assertThat(service.set(parent(), STUDENT, true, 0)).isEqualTo(enabled);
        assertThat(jdbc.queryForObject("select id from growth_review_subscription where parent_user_id=?", Long.class, PARENT).toString()).hasSize(19);
        assertThat(service.set(parent(), STUDENT, false, 1).version()).isEqualTo(2);
        assertThatThrownBy(() -> service.set(parent(), STUDENT, true, 0)).isInstanceOf(IllegalStateException.class);
    }
    @Test void canCancelAfterUnlinkingAndFeatureShutdownButCannotEnable() {
        service.set(parent(), STUDENT, true, 0);
        jdbc.update("delete from edu_parent_student where parent_user_id=?", PARENT);
        jdbc.update("update sys_feature_toggle set status='DISABLED' where feature_code='GROWTH_REVIEW_WEEKLY_SUBSCRIPTION'");
        session.clearCache();
        assertThat(service.set(parent(), STUDENT, false, 1).enabled()).isFalse();
        assertThatThrownBy(() -> service.set(parent(), STUDENT, true, 2)).isInstanceOf(RuntimeException.class);
    }
    @Test void cannotEnableForAnUnrelatedStudent() {
        jdbc.update("delete from edu_parent_student where parent_user_id=?", PARENT);
        assertThatThrownBy(() -> service.set(parent(), STUDENT, true, 0)).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);
        assertThat(jdbc.queryForObject("select count(*) from growth_review_subscription", Integer.class)).isZero();
    }
    @Test void otherParentReadsOnlyOwnPreferenceAndAuditorIsDenied() {
        service.set(parent(), STUDENT, true, 0);
        long otherId = PARENT + 30;
        jdbc.update("insert into sys_user(id,username,display_name,user_type,status) values (?, 'subscription_other','其他家长','FAMILY','ENABLED')", otherId);
        jdbc.update("insert into sys_user_role(id,user_id,role_id,organization_scope_key) select ?,?,id,'GLOBAL' from sys_role where role_code='PARENT'", PARENT + 31, otherId);
        var other = new AuthenticatedUser(otherId, otherId + 1, "subscription_other", "其他家长", AuthClientType.WEB, List.of("PARENT"));
        assertThat(service.get(other, STUDENT).enabled()).isFalse();
        jdbc.update("insert into sys_user_role(id,user_id,role_id,organization_scope_key) select ?,?,id,'GLOBAL' from sys_role where role_code='SYS_AUDITOR'", PARENT + 32, PARENT);
        session.clearCache();
        assertThatThrownBy(() -> service.get(parent(), STUDENT)).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);
    }
    @Test void queuesOnceAndCancelsAtomicallyThenReusesCancelledSlot() {
        createWeeklyReport();
        service.set(parent(), STUDENT, true, 0);
        var now = java.time.LocalDateTime.of(2026, 9, 7, 7, 0);
        assertThat(queue.prepare(PARENT, STUDENT, now)).isEqualTo(1);
        assertThat(queue.prepare(PARENT, STUDENT, now.plusMinutes(1))).isZero();
        assertThat(jdbc.queryForObject("select id from growth_review_delivery", Long.class).toString()).hasSize(19);
        service.set(parent(), STUDENT, false, 1);
        assertThat(jdbc.queryForObject("select status from growth_review_delivery", String.class)).isEqualTo("CANCELLED");
        assertThat(queue.prepare(PARENT, STUDENT, now)).isZero();
        service.set(parent(), STUDENT, true, 2);
        assertThat(queue.prepare(PARENT, STUDENT, now)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from growth_review_delivery", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select subscription_version from growth_review_delivery", Long.class)).isEqualTo(3);
    }
    @Test void refusesNightWrongDayMissingReportAndRevokedRelationship() {
        service.set(parent(), STUDENT, true, 0);
        var now = java.time.LocalDateTime.of(2026, 9, 7, 7, 0);
        assertThat(queue.prepare(PARENT, STUDENT, now)).isZero();
        createWeeklyReport();
        assertThat(queue.prepare(PARENT, STUDENT, now.minusMinutes(1))).isZero();
        assertThat(queue.prepare(PARENT, STUDENT, now.withHour(22))).isZero();
        assertThat(queue.prepare(PARENT, STUDENT, now.plusDays(1))).isZero();
        jdbc.update("delete from edu_parent_student where parent_user_id=?", PARENT);
        session.clearCache();
        assertThat(queue.prepare(PARENT, STUDENT, now)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from growth_review_delivery", Integer.class)).isZero();
    }
    private void createWeeklyReport() {
        long review = PARENT + 100, snapshot = PARENT + 101;
        jdbc.update("insert into growth_review(id,student_id,period_type,period_start,period_end,status) values (?,?,'WEEK','2026-08-31','2026-09-06','FINAL')", review, STUDENT);
        jdbc.update("""
                insert into growth_review_snapshot(id,review_id,content_version,task_total_count,completed_count,in_progress_count,
                  pending_optimization_count,exempted_count,completion_rate,earned_points,pause_count,generation_source,fact_fingerprint,data_cutoff_at,generated_at)
                values (?,?,1,1,1,0,0,0,1,10,0,'AUTO',?,current_timestamp,current_timestamp)
                """, snapshot, review, "a".repeat(64));
        jdbc.update("update growth_review set current_snapshot_id=? where id=?", snapshot, review);
        session.clearCache();
    }
    @Test void expiresAtNightAndCancelsPendingWhenFeatureIsDisabled() {
        createWeeklyReport();
        service.set(parent(), STUDENT, true, 0);
        var now = java.time.LocalDateTime.of(2026,9,7,7,0);
        queue.prepare(PARENT, STUDENT, now);
        assertThat(deliveries.cancelExpired(now.withHour(22))).isEqualTo(1);
        assertThat(queue.prepare(PARENT, STUDENT, now.withHour(22))).isZero();
        jdbc.update("update growth_review_delivery set status='PENDING'");
        jdbc.update("update sys_feature_toggle set status='DISABLED' where feature_code='GROWTH_REVIEW_WEEKLY_SUBSCRIPTION'");
        session.clearCache();
        assertThat(queue.prepare(PARENT, STUDENT, now)).isZero();
        assertThat(jdbc.queryForObject("select status from growth_review_delivery", String.class)).isEqualTo("CANCELLED");
    }
}
