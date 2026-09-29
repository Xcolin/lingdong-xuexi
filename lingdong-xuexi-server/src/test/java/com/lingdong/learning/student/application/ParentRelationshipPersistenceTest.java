package com.lingdong.learning.student.application;

import com.lingdong.learning.student.domain.ParentRelationship;
import com.lingdong.learning.student.domain.ParentRelationshipChange;
import com.lingdong.learning.student.domain.ParentRelationshipChangeType;
import com.lingdong.learning.student.domain.ParentRelationshipInvitation;
import com.lingdong.learning.student.domain.ParentRelationshipInvitationStatus;
import com.lingdong.learning.student.domain.ParentRelationshipInvitationType;
import com.lingdong.learning.student.domain.ParentRelationshipRole;
import com.lingdong.learning.student.infrastructure.persistence.ParentRelationshipChangeMapper;
import com.lingdong.learning.student.infrastructure.persistence.ParentRelationshipInvitationMapper;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ParentRelationshipPersistenceTest {
    private static final long PRIMARY_ID = 8910000000000000001L;
    private static final long SECONDARY_ID = 8910000000000000002L;
    private static final long STUDENT_ID = 8910000000000000003L;
    private static final long PRIMARY_RELATIONSHIP_ID = 8910000000000000004L;
    private static final long SECONDARY_RELATIONSHIP_ID = 8910000000000000005L;
    private static final long INVITATION_ID = 8910000000000000006L;
    private static final long CHANGE_ID = 8910000000000000007L;

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ParentStudentMapper mapper;
    @Autowired private ParentRelationshipInvitationMapper invitationMapper;
    @Autowired private ParentRelationshipChangeMapper changeMapper;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                insert into sys_user (id, username, display_name, mobile, user_type, status)
                values (?, 'relation_primary', '关系测试主家长', '13800000001', 'FAMILY', 'ENABLED'),
                       (?, 'relation_secondary', '关系测试副家长', '13800000002', 'FAMILY', 'ENABLED')
                """, PRIMARY_ID, SECONDARY_ID);
        jdbcTemplate.update("""
                insert into edu_student (id, student_name, grade_code, status)
                values (?, '关系测试学生', 'GRADE_3', 'ENABLED')
                """, STUDENT_ID);
        jdbcTemplate.update("""
                insert into edu_parent_student (
                    id, parent_user_id, student_id, relation_role, status,
                    primary_scope_key, bound_at
                ) values (?, ?, ?, 'PRIMARY_GUARDIAN', 'ACTIVE', 'PRIMARY', ?)
                """, PRIMARY_RELATIONSHIP_ID, PRIMARY_ID, STUDENT_ID,
                LocalDateTime.of(2026, 8, 1, 10, 0));
    }

    @Test
    void insertsLocksUnbindsAndReactivatesSecondaryRelationship() {
        LocalDateTime firstBoundAt = LocalDateTime.of(2026, 8, 2, 10, 0);
        assertThat(mapper.insertSecondary(
                SECONDARY_RELATIONSHIP_ID, SECONDARY_ID, STUDENT_ID, firstBoundAt)).isEqualTo(1);

        assertThat(mapper.findActiveStudentsByParent(PRIMARY_ID)).singleElement().satisfies(student -> {
            assertThat(student.studentId()).isEqualTo(STUDENT_ID);
            assertThat(student.studentName()).isEqualTo("关系测试学生");
            assertThat(student.relationshipRole()).isEqualTo(ParentRelationshipRole.PRIMARY_GUARDIAN);
        });
        assertThat(mapper.findActiveStudentsByParent(SECONDARY_ID)).singleElement().satisfies(student -> {
            assertThat(student.studentId()).isEqualTo(STUDENT_ID);
            assertThat(student.studentName()).isEqualTo("关系测试学生");
            assertThat(student.relationshipRole()).isEqualTo(ParentRelationshipRole.SECONDARY_GUARDIAN);
        });

        List<ParentRelationship> locked = mapper.findActiveByStudentIdForUpdate(STUDENT_ID);
        ParentRelationship earliest = mapper.findEarliestActiveSecondaryForUpdate(STUDENT_ID);

        assertThat(locked).hasSize(2);
        assertThat(earliest.id()).isEqualTo(SECONDARY_RELATIONSHIP_ID);
        assertThat(earliest.role()).isEqualTo(ParentRelationshipRole.SECONDARY_GUARDIAN);

        assertThat(mapper.unbind(
                SECONDARY_RELATIONSHIP_ID, "CLOSED:" + SECONDARY_RELATIONSHIP_ID,
                LocalDateTime.of(2026, 8, 3, 10, 0))).isEqualTo(1);
        assertThat(mapper.existsActiveByParentAndStudent(SECONDARY_ID, STUDENT_ID)).isFalse();

        LocalDateTime reboundAt = LocalDateTime.of(2026, 8, 4, 10, 0);
        assertThat(mapper.reactivateAsSecondary(SECONDARY_RELATIONSHIP_ID, reboundAt)).isEqualTo(1);
        ParentRelationship rebound = mapper.findByParentAndStudent(SECONDARY_ID, STUDENT_ID);

        assertThat(rebound.role()).isEqualTo(ParentRelationshipRole.SECONDARY_GUARDIAN);
        assertThat(rebound.status()).isEqualTo("ACTIVE");
        assertThat(rebound.primaryScopeKey()).isEqualTo("SECONDARY");
        assertThat(rebound.boundAt()).isEqualTo(reboundAt);
        assertThat(rebound.unboundAt()).isNull();
    }

    @Test
    void closesPendingInvitationOnceAndAppendsImmutableRelationshipAudit() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 5, 10, 0);
        ParentRelationshipInvitation invitation = new ParentRelationshipInvitation(
                INVITATION_ID, STUDENT_ID, PRIMARY_ID, SECONDARY_ID, "13800000002",
                ParentRelationshipInvitationType.SECONDARY_BIND,
                ParentRelationshipInvitationStatus.PENDING, "PENDING",
                createdAt.plusMinutes(5), null, null, createdAt, createdAt);

        assertThat(invitationMapper.insert(invitation)).isEqualTo(1);
        ParentRelationshipInvitation locked = invitationMapper.findByIdForUpdate(INVITATION_ID);
        assertThat(locked.status()).isEqualTo(ParentRelationshipInvitationStatus.PENDING);
        assertThat(locked.inviteeMobile()).isEqualTo("13800000002");

        LocalDateTime respondedAt = createdAt.plusMinutes(1);
        assertThat(invitationMapper.respondIfPending(
                INVITATION_ID, ParentRelationshipInvitationStatus.ACCEPTED,
                "CLOSED:" + INVITATION_ID, SECONDARY_ID, respondedAt)).isEqualTo(1);
        assertThat(invitationMapper.respondIfPending(
                INVITATION_ID, ParentRelationshipInvitationStatus.REJECTED,
                "CLOSED:" + INVITATION_ID, SECONDARY_ID, respondedAt)).isZero();

        assertThat(mapper.insertSecondary(
                SECONDARY_RELATIONSHIP_ID, SECONDARY_ID, STUDENT_ID, respondedAt)).isEqualTo(1);

        ParentRelationshipChange change = new ParentRelationshipChange(
                CHANGE_ID, STUDENT_ID, SECONDARY_RELATIONSHIP_ID, INVITATION_ID,
                ParentRelationshipChangeType.BIND_SECONDARY, PRIMARY_ID,
                null, SECONDARY_ID, null, ParentRelationshipRole.SECONDARY_GUARDIAN,
                respondedAt, respondedAt);
        assertThat(changeMapper.insert(change)).isEqualTo(1);

        List<ParentRelationshipChange> changes = changeMapper.findByStudentId(STUDENT_ID);
        assertThat(changes).singleElement().satisfies(saved -> {
            assertThat(saved.id()).isEqualTo(CHANGE_ID);
            assertThat(saved.operationType()).isEqualTo(ParentRelationshipChangeType.BIND_SECONDARY);
            assertThat(saved.newParentUserId()).isEqualTo(SECONDARY_ID);
        });
    }
}
