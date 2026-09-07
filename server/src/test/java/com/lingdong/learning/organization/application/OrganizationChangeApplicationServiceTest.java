package com.lingdong.learning.organization.application;

import com.lingdong.learning.audit.application.SystemTask;
import com.lingdong.learning.audit.application.SystemTaskStatus;
import com.lingdong.learning.audit.infrastructure.persistence.SystemTaskMapper;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.domain.OrganizationChange;
import com.lingdong.learning.organization.domain.OrganizationChangeExecutionStatus;
import com.lingdong.learning.organization.domain.OrganizationChangeType;
import com.lingdong.learning.organization.domain.OrganizationStatus;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationChangeMapper;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.user.application.AssignRoleToUserCommand;
import com.lingdong.learning.user.application.CreateUserCommand;
import com.lingdong.learning.user.application.UserAccessApplicationService;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class OrganizationChangeApplicationServiceTest {
    @Autowired
    private OrganizationChangeApplicationService organizationChangeApplicationService;

    @Autowired
    private OrganizationApplicationService organizationApplicationService;

    @Autowired
    private OrganizationChangeMapper organizationChangeMapper;

    @Autowired
    private OrganizationMapper organizationMapper;

    @Autowired
    private SystemTaskMapper systemTaskMapper;

    @Autowired
    private UserAccessApplicationService userAccessApplicationService;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IdGenerator idGenerator;

    @Test
    void createsPendingDisableRequestWithoutMutatingOrganization() {
        User administrator = createUserWithRole("org_change_admin_a", "组织变更管理员甲", "SYS_ADMIN");
        Organization organization = createRegion("REGION_CHANGE_A", "组织变更区域甲");

        OrganizationChange change = organizationChangeApplicationService.createAndSubmit(
                new CreateOrganizationChangeCommand(
                        administrator.id(), organization.id(), OrganizationChangeType.DISABLE,
                        null, organization.versionNo(), "该区域暂不继续开展业务"
                )
        );

        SystemTask task = systemTaskMapper.findById(change.taskId());
        Organization stored = organizationMapper.findById(organization.id());
        assertThat(Long.toString(change.id())).hasSize(19);
        assertThat(task.status()).isEqualTo(SystemTaskStatus.PENDING_REVIEW);
        assertThat(change.executionStatus()).isEqualTo(OrganizationChangeExecutionStatus.PENDING);
        assertThat(change.organizationCodeSnapshot()).isEqualTo(organization.code());
        assertThat(change.organizationNameSnapshot()).isEqualTo(organization.name());
        assertThat(stored.status()).isEqualTo(OrganizationStatus.ENABLED);
    }

    @Test
    void rejectsRequestFromAuditorAndDuplicateActiveRequest() {
        User administrator = createUserWithRole("org_change_admin_b", "组织变更管理员乙", "SYS_ADMIN");
        User auditor = createUserWithRole("org_change_auditor_b", "组织变更审核员乙", "SYS_AUDITOR");
        Organization organization = createRegion("REGION_CHANGE_B", "组织变更区域乙");
        CreateOrganizationChangeCommand command = new CreateOrganizationChangeCommand(
                administrator.id(), organization.id(), OrganizationChangeType.DISABLE,
                null, organization.versionNo(), "区域业务暂停"
        );
        organizationChangeApplicationService.createAndSubmit(command);

        assertThatThrownBy(() -> organizationChangeApplicationService.createAndSubmit(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("待处理");
        assertThatThrownBy(() -> organizationChangeApplicationService.createAndSubmit(
                new CreateOrganizationChangeCommand(
                        auditor.id(), organization.id(), OrganizationChangeType.DELETE,
                        null, organization.versionNo(), "审核员不能发起"
                )
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("系统管理员");
    }

    @Test
    void requiresReasonAndCurrentVersionWhenCreatingRequest() {
        User administrator = createUserWithRole("org_change_admin_c", "组织变更管理员丙", "SYS_ADMIN");
        Organization organization = createRegion("REGION_CHANGE_C", "组织变更区域丙");

        assertThatThrownBy(() -> organizationChangeApplicationService.createAndSubmit(
                new CreateOrganizationChangeCommand(
                        administrator.id(), organization.id(), OrganizationChangeType.DELETE,
                        null, organization.versionNo(), " "
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("原因");

        assertThatThrownBy(() -> organizationChangeApplicationService.createAndSubmit(
                new CreateOrganizationChangeCommand(
                        administrator.id(), organization.id(), OrganizationChangeType.DISABLE,
                        null, organization.versionNo() + 1, "使用过期页面提交"
                )
        )).isInstanceOf(OrganizationVersionConflictException.class);
    }

    @Test
    void requiresTargetParentOnlyForMoveRequest() {
        User administrator = createUserWithRole("org_change_admin_d", "组织变更管理员丁", "SYS_ADMIN");
        Organization organization = createRegion("REGION_CHANGE_D", "组织变更区域丁");

        assertThatThrownBy(() -> organizationChangeApplicationService.createAndSubmit(
                new CreateOrganizationChangeCommand(
                        administrator.id(), organization.id(), OrganizationChangeType.MOVE,
                        null, organization.versionNo(), "调整组织归属"
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("目标父级");
    }

    @Test
    void rejectsReviewFromNonAuditorAndRequiresRejectionComment() {
        User administrator = createUserWithRole("org_review_admin_a", "组织审核管理员甲", "SYS_ADMIN");
        User auditor = createUserWithRole("org_review_auditor_a", "组织审核员甲", "SYS_AUDITOR");
        User ordinaryUser = userAccessApplicationService.createUser(
                new CreateUserCommand("org_review_user_a", "普通平台用户甲", null, UserType.PLATFORM));
        Organization organization = createRegion("REGION_REVIEW_A", "组织审核区域甲");
        OrganizationChange change = createDisableRequest(administrator, organization, "等待审核停用");

        assertThatThrownBy(() -> organizationChangeApplicationService.approveAndApply(
                change.taskId(), ordinaryUser.id(), "无权审核"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("系统审核员");
        assertThatThrownBy(() -> organizationChangeApplicationService.reject(
                change.taskId(), auditor.id(), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("审批意见");

        SystemTask rejected = organizationChangeApplicationService.reject(
                change.taskId(), auditor.id(), "当前不具备停用条件");
        assertThat(rejected.status()).isEqualTo(SystemTaskStatus.REJECTED);
        Integer auditCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_organization_change_audit
                WHERE task_id = ? AND event_type = 'REJECT' AND operator_user_id = ?
                """, Integer.class, change.taskId(), auditor.id());
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void recordsFailedExecutionWithoutChangingOrganizationWhenVersionDrifts() {
        User administrator = createUserWithRole("org_review_admin_b", "组织审核管理员乙", "SYS_ADMIN");
        User auditor = createUserWithRole("org_review_auditor_b", "组织审核员乙", "SYS_AUDITOR");
        Organization organization = createRegion("REGION_REVIEW_B", "组织审核区域乙");
        OrganizationChange change = createDisableRequest(administrator, organization, "停用版本漂移区域");
        organizationApplicationService.updateOrganization(
                administrator.id(),
                new UpdateOrganizationCommand(
                        organization.id(), "审批前已更新区域", 20, organization.versionNo())
        );

        assertThatThrownBy(() -> organizationChangeApplicationService.approveAndApply(
                change.taskId(), auditor.id(), "同意停用"))
                .isInstanceOf(OrganizationChangeExecutionException.class)
                .hasMessageContaining("执行失败");

        SystemTask approved = systemTaskMapper.findById(change.taskId());
        OrganizationChange failed = organizationChangeMapper.findByTaskId(change.taskId());
        Organization stored = organizationMapper.findById(organization.id());
        assertThat(approved.status()).isEqualTo(SystemTaskStatus.APPROVED);
        assertThat(failed.executionStatus()).isEqualTo(OrganizationChangeExecutionStatus.FAILED);
        assertThat(failed.failureReason()).contains("组织数据已变化");
        assertThat(stored.status()).isEqualTo(OrganizationStatus.ENABLED);
        assertThat(stored.name()).isEqualTo("审批前已更新区域");
    }

    @Test
    void approvesAndAppliesDisableToOrganizationSubtree() {
        User administrator = createUserWithRole("org_review_admin_c", "组织审核管理员丙", "SYS_ADMIN");
        User auditor = createUserWithRole("org_review_auditor_c", "组织审核员丙", "SYS_AUDITOR");
        Organization region = createRegion("REGION_REVIEW_C", "组织审核区域丙");
        Organization school = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand(
                        "SCHOOL_REVIEW_C", "组织审核学校丙", "SCHOOL", region.id(), 10));
        OrganizationChange change = createDisableRequest(administrator, region, "区域整体暂停业务");

        SystemTask effective = organizationChangeApplicationService.approveAndApply(
                change.taskId(), auditor.id(), "同意停用");

        OrganizationChange applied = organizationChangeMapper.findByTaskId(change.taskId());
        Organization storedRegion = organizationMapper.findById(region.id());
        Organization storedSchool = organizationMapper.findById(school.id());
        assertThat(effective.status()).isEqualTo(SystemTaskStatus.EFFECTIVE);
        assertThat(applied.executionStatus()).isEqualTo(OrganizationChangeExecutionStatus.APPLIED);
        assertThat(storedRegion.status()).isEqualTo(OrganizationStatus.DISABLED);
        assertThat(storedRegion.effectiveStatus().name()).isEqualTo("DISABLED");
        assertThat(storedSchool.status()).isEqualTo(OrganizationStatus.ENABLED);
        assertThat(storedSchool.effectiveStatus().name()).isEqualTo("DISABLED");
        assertThat(storedSchool.versionNo()).isEqualTo(school.versionNo() + 1);
    }

    @Test
    @Transactional
    void approvedClassDisableInvalidatesUnfinishedInstitutionAssignment() {
        User administrator = createUserWithRole(
                "class_disable_admin", "班级停用管理员", "SYS_ADMIN");
        User auditor = createUserWithRole(
                "class_disable_auditor", "班级停用审核员", "SYS_AUDITOR");
        Organization school = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand(
                        "SCHOOL_CLASS_DISABLE", "班级停用学校", "SCHOOL", null, 10));
        Organization clazz = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand(
                        "CLASS_DISABLE_REVIEW", "审批停用班级", "CLASS", school.id(), 10));
        long assignmentId = insertUnfinishedOrganizationAssignment(
                administrator.id(), clazz.id());
        OrganizationChange change = createDisableRequest(
                administrator, clazz, "该班级停止开展教学任务");

        SystemTask effective = organizationChangeApplicationService.approveAndApply(
                change.taskId(), auditor.id(), "同意停用班级");

        assertThat(effective.status()).isEqualTo(SystemTaskStatus.EFFECTIVE);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_status FROM learn_task_assignment WHERE id = ?",
                String.class, assignmentId)).isEqualTo("INVALIDATED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM learn_task_assignment_event
                WHERE assignment_id = ? AND event_type = 'CLASS_INVALIDATED'
                  AND operator_user_id = ?
                """, Integer.class, assignmentId, auditor.id())).isEqualTo(1);
    }

    @Test
    void rejectsMovingOrganizationToItselfBeforeSubmission() {
        User administrator = createUserWithRole("org_move_admin_a", "组织移动管理员甲", "SYS_ADMIN");
        Organization organization = createRegion("REGION_MOVE_A", "组织移动区域甲");

        assertThatThrownBy(() -> organizationChangeApplicationService.createAndSubmit(
                new CreateOrganizationChangeCommand(
                        administrator.id(), organization.id(), OrganizationChangeType.MOVE,
                        organization.id(), organization.versionNo(), "不能移动到自身"
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("自身");
    }

    @Test
    void failsMoveWhenTargetIsDescendantOrNotOperational() {
        User administrator = createUserWithRole("org_move_admin_b", "组织移动管理员乙", "SYS_ADMIN");
        User auditor = createUserWithRole("org_move_auditor_b", "组织移动审核员乙", "SYS_AUDITOR");
        Organization region = createRegion("REGION_MOVE_B", "组织移动区域乙");
        Organization school = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("SCHOOL_MOVE_B", "组织移动学校乙", "SCHOOL", region.id(), 10));

        assertThatThrownBy(() -> organizationChangeApplicationService.createAndSubmit(
                new CreateOrganizationChangeCommand(
                        administrator.id(), region.id(), OrganizationChangeType.MOVE,
                        school.id(), region.versionNo(), "不能移入自身后代"
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("后代");

        Organization source = createRegion("REGION_MOVE_B_SOURCE", "组织移动源区域乙");
        Organization target = createRegion("REGION_MOVE_B_TARGET", "组织移动目标区域乙");
        OrganizationChange change = createMoveRequest(
                administrator, source, target, "移动到随后停用的目标");
        jdbcTemplate.update("""
                UPDATE sys_organization
                SET status = 'DISABLED', effective_status = 'DISABLED'
                WHERE id = ?
                """, target.id());

        assertThatThrownBy(() -> organizationChangeApplicationService.approveAndApply(
                change.taskId(), auditor.id(), "同意移动"))
                .isInstanceOf(OrganizationChangeExecutionException.class);
        assertThat(organizationMapper.findById(source.id()).parentId()).isNull();
    }

    @Test
    void failsMoveForDuplicateSiblingNameAndExcessiveSubtreePath() {
        User administrator = createUserWithRole("org_move_admin_c", "组织移动管理员丙", "SYS_ADMIN");
        User auditor = createUserWithRole("org_move_auditor_c", "组织移动审核员丙", "SYS_AUDITOR");
        Organization sourceParent = createRegion("REGION_MOVE_C_SOURCE", "组织移动源父级丙");
        Organization targetParent = createRegion("REGION_MOVE_C_TARGET", "组织移动目标父级丙");
        Organization source = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("SCHOOL_MOVE_C_SOURCE", "同名移动学校", "SCHOOL", sourceParent.id(), 10));
        organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("SCHOOL_MOVE_C_EXISTING", "同名移动学校", "SCHOOL", targetParent.id(), 10));
        OrganizationChange duplicateChange = createMoveRequest(
                administrator, source, targetParent, "验证同级重名");

        assertThatThrownBy(() -> organizationChangeApplicationService.approveAndApply(
                duplicateChange.taskId(), auditor.id(), "同意移动"))
                .isInstanceOf(OrganizationChangeExecutionException.class);

        Organization longPathSource = createRegion("REGION_MOVE_C_LONG_SOURCE", "长路径移动源区域");
        Organization longPathTarget = createRegion("REGION_MOVE_C_LONG_TARGET", "长路径移动目标区域");
        String longPath = "/" + "A".repeat(1018) + "/";
        jdbcTemplate.update("UPDATE sys_organization SET organization_path = ? WHERE id = ?",
                longPath, longPathTarget.id());
        OrganizationChange longPathChange = createMoveRequest(
                administrator, longPathSource, organizationMapper.findById(longPathTarget.id()), "验证路径长度");

        assertThatThrownBy(() -> organizationChangeApplicationService.approveAndApply(
                longPathChange.taskId(), auditor.id(), "同意移动"))
                .isInstanceOf(OrganizationChangeExecutionException.class);
        assertThat(organizationMapper.findById(longPathSource.id()).path())
                .isEqualTo(longPathSource.path());
    }

    @Test
    void movesWholeSubtreeAndRecalculatesPathsAndEffectiveStatuses() {
        User administrator = createUserWithRole("org_move_admin_d", "组织移动管理员丁", "SYS_ADMIN");
        User auditor = createUserWithRole("org_move_auditor_d", "组织移动审核员丁", "SYS_AUDITOR");
        Organization sourceParent = createRegion("REGION_MOVE_D_SOURCE", "组织移动源父级丁");
        Organization targetParent = createRegion("REGION_MOVE_D_TARGET", "组织移动目标父级丁");
        Organization school = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("SCHOOL_MOVE_D", "组织移动学校丁", "SCHOOL", sourceParent.id(), 10));
        Organization clazz = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("CLASS_MOVE_D", "组织移动班级丁", "CLASS", school.id(), 10));
        OrganizationChange change = createMoveRequest(
                administrator, school, targetParent, "调整学校所属区域");

        SystemTask effective = organizationChangeApplicationService.approveAndApply(
                change.taskId(), auditor.id(), "同意移动");

        Organization movedSchool = organizationMapper.findById(school.id());
        Organization movedClass = organizationMapper.findById(clazz.id());
        assertThat(effective.status()).isEqualTo(SystemTaskStatus.EFFECTIVE);
        assertThat(movedSchool.parentId()).isEqualTo(targetParent.id());
        assertThat(movedSchool.parentScopeKey()).isEqualTo("PARENT:" + targetParent.id());
        assertThat(movedSchool.path()).isEqualTo(targetParent.path() + school.code() + "/");
        assertThat(movedClass.path()).isEqualTo(movedSchool.path() + clazz.code() + "/");
        assertThat(movedSchool.versionNo()).isEqualTo(school.versionNo() + 1);
        assertThat(movedClass.versionNo()).isEqualTo(clazz.versionNo() + 1);
    }

    @Test
    void refusesDeletingNonLeafOrReferencedOrganization() {
        User administrator = createUserWithRole("org_delete_admin_a", "组织删除管理员甲", "SYS_ADMIN");
        User auditor = createUserWithRole("org_delete_auditor_a", "组织删除审核员甲", "SYS_AUDITOR");
        Organization parent = createRegion("REGION_DELETE_A_PARENT", "组织删除父级区域甲");
        organizationApplicationService.createOrganization(
                new CreateOrganizationCommand(
                        "SCHOOL_DELETE_A_CHILD", "组织删除子级学校甲", "SCHOOL", parent.id(), 10));
        OrganizationChange nonLeafChange = createDeleteRequest(
                administrator, parent, "尝试删除非叶子节点");

        assertThatThrownBy(() -> organizationChangeApplicationService.approveAndApply(
                nonLeafChange.taskId(), auditor.id(), "同意删除"))
                .isInstanceOf(OrganizationChangeExecutionException.class);
        assertThat(organizationMapper.findById(parent.id())).isNotNull();

        Organization referenced = createRegion("REGION_DELETE_A_REF", "组织删除引用区域甲");
        jdbcTemplate.update("""
                INSERT INTO sys_organization_admin (id, organization_id, user_id)
                VALUES (8890000000000000001, ?, ?)
                """, referenced.id(), administrator.id());
        OrganizationChange referencedChange = createDeleteRequest(
                administrator, referenced, "尝试删除存在管理员引用的节点");

        assertThatThrownBy(() -> organizationChangeApplicationService.approveAndApply(
                referencedChange.taskId(), auditor.id(), "同意删除"))
                .isInstanceOf(OrganizationChangeExecutionException.class);
        assertThat(organizationMapper.findById(referenced.id())).isNotNull();
    }

    @Test
    void deletesUnreferencedLeafAndRetainsChangeSnapshotAndAudit() {
        User administrator = createUserWithRole("org_delete_admin_b", "组织删除管理员乙", "SYS_ADMIN");
        User auditor = createUserWithRole("org_delete_auditor_b", "组织删除审核员乙", "SYS_AUDITOR");
        Organization organization = createRegion("REGION_DELETE_B", "可删除叶子区域乙");
        OrganizationChange change = createDeleteRequest(administrator, organization, "删除空叶子节点");

        SystemTask effective = organizationChangeApplicationService.approveAndApply(
                change.taskId(), auditor.id(), "同意删除");

        OrganizationChange applied = organizationChangeMapper.findByTaskId(change.taskId());
        assertThat(effective.status()).isEqualTo(SystemTaskStatus.EFFECTIVE);
        assertThat(organizationMapper.findById(organization.id())).isNull();
        assertThat(applied.executionStatus()).isEqualTo(OrganizationChangeExecutionStatus.APPLIED);
        assertThat(applied.organizationCodeSnapshot()).isEqualTo(organization.code());
        Integer auditCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_organization_change_audit
                WHERE organization_id = ? AND task_id = ? AND event_type = 'APPLY'
                """, Integer.class, organization.id(), change.taskId());
        assertThat(auditCount).isEqualTo(1);
    }

    private OrganizationChange createDisableRequest(
            User administrator,
            Organization organization,
            String reason
    ) {
        return organizationChangeApplicationService.createAndSubmit(
                new CreateOrganizationChangeCommand(
                        administrator.id(), organization.id(), OrganizationChangeType.DISABLE,
                        null, organization.versionNo(), reason));
    }

    private OrganizationChange createMoveRequest(
            User administrator,
            Organization organization,
            Organization targetParent,
            String reason
    ) {
        return organizationChangeApplicationService.createAndSubmit(
                new CreateOrganizationChangeCommand(
                        administrator.id(), organization.id(), OrganizationChangeType.MOVE,
                        targetParent.id(), organization.versionNo(), reason));
    }

    private OrganizationChange createDeleteRequest(
            User administrator,
            Organization organization,
            String reason
    ) {
        return organizationChangeApplicationService.createAndSubmit(
                new CreateOrganizationChangeCommand(
                        administrator.id(), organization.id(), OrganizationChangeType.DELETE,
                        null, organization.versionNo(), reason));
    }

    private Organization createRegion(String code, String name) {
        return organizationApplicationService.createOrganization(
                new CreateOrganizationCommand(code, name, "REGION", null, 10));
    }

    private long insertUnfinishedOrganizationAssignment(Long operatorUserId, Long classId) {
        long studentId = idGenerator.nextId();
        long taskId = idGenerator.nextId();
        long assignmentId = idGenerator.nextId();
        LocalDate scheduledDate = LocalDate.now();
        jdbcTemplate.update("""
                INSERT INTO edu_student (id, student_name, status)
                VALUES (?, '审批停用班级学生', 'ENABLED')
                """, studentId);
        jdbcTemplate.update("""
                INSERT INTO edu_student_organization (
                    id, student_id, organization_id, relation_type, status
                ) VALUES (?, ?, ?, 'CLASS', 'ACTIVE')
                """, idGenerator.nextId(), studentId, classId);
        jdbcTemplate.update("""
                INSERT INTO learn_task (
                    id, source_type, source_organization_id, creator_user_id, title,
                    difficulty_level, base_points, duration_minutes, scheduled_date,
                    reviewer_user_id, status
                ) VALUES (?, 'ORGANIZATION', ?, ?, '审批停用联动任务',
                          1, 10, 30, ?, ?, 'PUBLISHED')
                """, taskId, classId, operatorUserId, scheduledDate, operatorUserId);
        jdbcTemplate.update("""
                INSERT INTO learn_task_assignment (
                    id, task_id, student_id, source_type, source_organization_id,
                    current_status, current_reviewer_id, scheduled_date, due_at,
                    last_transition_at
                ) VALUES (?, ?, ?, 'ORGANIZATION', ?, 'PENDING_CLAIM', ?, ?, ?, ?)
                """, assignmentId, taskId, studentId, classId, operatorUserId,
                scheduledDate, scheduledDate.atTime(23, 59), LocalDateTime.now());
        return assignmentId;
    }

    private User createUserWithRole(String username, String displayName, String roleCode) {
        User user = userAccessApplicationService.createUser(
                new CreateUserCommand(username, displayName, null, UserType.PLATFORM));
        Role role = roleMapper.findByCode(roleCode);
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
        return user;
    }
}
