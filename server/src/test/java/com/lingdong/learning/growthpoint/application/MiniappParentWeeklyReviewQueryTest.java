package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.growthpoint.domain.GrowthReviewPeriodType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.mybatis.spring.SqlSessionTemplate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/** 本地 H2 验证家长小程序历史周报读取边界，不访问远程或消息服务。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MiniappParentWeeklyReviewQueryTest {
    private static final long BASE = 1874244142494708000L;
    private static final long PARENT = BASE + 1, STUDENT = BASE + 2, REVIEW = BASE + 3, SNAPSHOT = BASE + 4;
    @Autowired GrowthReviewQueryService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired SqlSessionTemplate session;
    private final AuthenticatedUser parent = new AuthenticatedUser(PARENT, BASE + 9, "weekly_parent", "周报家长", AuthClientType.MINIAPP, List.of("PARENT"));

    @BeforeEach void setup() {
        jdbc.update("insert into sys_user(id,username,display_name,user_type,status) values(?,'weekly_parent','周报家长','FAMILY','ENABLED')", PARENT);
        jdbc.update("insert into edu_student(id,student_name,status) values(?,'周报孩子','ENABLED')", STUDENT);
        jdbc.update("insert into sys_user_role(id,user_id,role_id,organization_scope_key) select ?,?,id,'GLOBAL' from sys_role where role_code='PARENT'", BASE + 5, PARENT);
        jdbc.update("""
                insert into edu_parent_student(id,parent_user_id,student_id,relation_role,status,primary_scope_key)
                values(?,?,?,'SECONDARY_GUARDIAN','ACTIVE','SECONDARY')
                """, BASE + 6, PARENT, STUDENT);
        jdbc.update("""
                insert into growth_review(id,student_id,period_type,period_start,period_end,status)
                values(?,?,'WEEK','2026-09-07','2026-09-13','FINAL')
                """, REVIEW, STUDENT);
        jdbc.update("""
                insert into growth_review_snapshot(id,review_id,content_version,task_total_count,completed_count,
                in_progress_count,pending_optimization_count,exempted_count,completion_rate,earned_points,pause_count,
                generation_source,fact_fingerprint,data_cutoff_at,generated_at)
                values(?,?,1,4,2,1,1,0,0.6667,12,1,'AUTO',?,'2026-09-14 00:00:00','2026-09-14 00:00:00')
                """, SNAPSHOT, REVIEW, "a".repeat(64));
        jdbc.update("update growth_review set current_snapshot_id=? where id=?", SNAPSHOT, REVIEW);
    }

    @Test void secondaryParentReadsWeeklyHistoryWithGenerationAndDeliveryDisabled() {
        assertThat(jdbc.update("update sys_feature_toggle set status='DISABLED' where feature_code in ('PERIODIC_GROWTH_REPORT','GROWTH_REVIEW_WEEKLY_SUBSCRIPTION')")).isEqualTo(2);
        assertThat(service.findMiniappChildren(parent)).containsExactly(new GrowthReviewQueryService.ChildOption(Long.toString(STUDENT), "周报孩子"));
        var page = service.findMiniappChildWeeklyReviews(parent, STUDENT, 1, 20);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).extracting(GrowthReviewSummaryView::periodType).containsExactly(GrowthReviewPeriodType.WEEK);
        assertThat(service.findMiniappChildWeeklyReview(parent, STUDENT, REVIEW).snapshotId()).isEqualTo(SNAPSHOT);
        assertThat(service.findMiniappChildWeeklyReviews(parent, STUDENT, 2, 20).items()).isEmpty();
    }

    @Test void rejectsOtherPeriodsAndInvalidPagination() {
        for (String period : List.of("DAY", "MONTH")) {
            jdbc.update("update growth_review set period_type=? where id=?", period, REVIEW);
            session.clearCache();
            assertThat(service.findMiniappChildWeeklyReviews(parent, STUDENT, 1, 20).items()).isEmpty();
            assertThatThrownBy(() -> service.findMiniappChildWeeklyReview(parent, STUDENT, REVIEW)).isInstanceOf(ResourceNotFoundException.class);
        }
        assertThatThrownBy(() -> service.findMiniappChildWeeklyReviews(parent, STUDENT, 0, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findMiniappChildWeeklyReviews(parent, STUDENT, 1, 101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findMiniappChildWeeklyReviews(parent, STUDENT, 1_000_001, 20)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsWrongClientAndDoesNotRelaxWebOrStudentRoutes() {
        var web = new AuthenticatedUser(PARENT, BASE + 9, "weekly_parent", "周报家长", AuthClientType.WEB, List.of("PARENT"));
        assertThatThrownBy(() -> service.findMiniappChildren(web)).isInstanceOf(SystemOperationAccessDeniedException.class);
        assertThatThrownBy(() -> service.findChildReview(parent, STUDENT, REVIEW)).isInstanceOf(SystemOperationAccessDeniedException.class);
        assertThatThrownBy(() -> service.findMyReviews(parent, GrowthReviewPeriodType.WEEK, 1, 20)).isInstanceOf(SystemOperationAccessDeniedException.class);
    }

    @Test void revokedRelationshipDisappearsAndRejectsDirectMessageLink() {
        assertThat(service.findMiniappChildren(parent)).hasSize(1);
        jdbc.update("update edu_parent_student set status='UNBOUND' where parent_user_id=?", PARENT);
        session.clearCache();
        assertThat(service.findMiniappChildren(parent)).isEmpty();
        assertThatThrownBy(() -> service.findMiniappChildWeeklyReview(parent, STUDENT, REVIEW)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.findMiniappChildWeeklyReviews(parent, STUDENT + 100, 1, 20)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void revokedMiniappPermissionCannotBeReplacedByExistingWebGrant() {
        assertThat(service.findMiniappChildren(parent)).hasSize(1);
        jdbc.update("update sys_permission set status='DISABLED' where permission_code='MINIAPP_GROWTH_REVIEW_READ_CHILD'");
        session.clearCache();
        assertThatThrownBy(() -> service.findMiniappChildren(parent)).isInstanceOf(SystemOperationAccessDeniedException.class);
        assertThatThrownBy(() -> service.findMiniappChildWeeklyReview(parent, STUDENT, REVIEW)).isInstanceOf(SystemOperationAccessDeniedException.class);
    }

    @Test void disabledAccountAndRevokedRoleAreRechecked() {
        jdbc.update("update sys_user set status='DISABLED' where id=?", PARENT);
        session.clearCache();
        assertThatThrownBy(() -> service.findMiniappChildren(parent)).isInstanceOf(SystemOperationAccessDeniedException.class);
        jdbc.update("update sys_user set status='ENABLED' where id=?", PARENT);
        jdbc.update("delete from sys_user_role where user_id=?", PARENT);
        session.clearCache();
        assertThatThrownBy(() -> service.findMiniappChildren(parent)).isInstanceOf(SystemOperationAccessDeniedException.class);
    }

    @Test void dynamicallyAddedAuditorRoleBlocksParentEvenWithStalePrincipal() {
        jdbc.update("insert into sys_user_role(id,user_id,role_id,organization_scope_key) select ?,?,id,'GLOBAL' from sys_role where role_code='SYS_AUDITOR'", BASE + 7, PARENT);
        session.clearCache();
        assertThatThrownBy(() -> service.findMiniappChildren(parent)).isInstanceOf(SystemOperationAccessDeniedException.class);
        assertThatThrownBy(() -> service.findMiniappChildWeeklyReview(parent, STUDENT, REVIEW)).isInstanceOf(SystemOperationAccessDeniedException.class);
    }
}
