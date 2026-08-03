package com.lingdong.learning.student.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.domain.OrganizationStatus;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.student.domain.ParentBindingInvitation;
import com.lingdong.learning.student.domain.ParentBindingInvitationStatus;
import com.lingdong.learning.student.infrastructure.persistence.ParentBindingInvitationMapper;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationMapper;
import com.lingdong.learning.student.infrastructure.security.InvitationTokenService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 机构管理员邀请既有家长账号绑定直管学生的应用服务。
 * 原始令牌只从创建命令的临时结果返回，任何日志和持久化对象均不保存它。
 */
@Service
public class ParentBindingInvitationApplicationService {
    private static final String PARENT_ROLE = "PARENT";
    private static final String ORGANIZATION_ADMIN_ROLE = "ORG_ADMIN";
    private static final int INVITATION_VALID_DAYS = 7;

    private final StudentMapper studentMapper;
    private final OrganizationMapper organizationMapper;
    private final OrganizationAdminMapper organizationAdminMapper;
    private final StudentOrganizationMapper studentOrganizationMapper;
    private final ParentStudentMapper parentStudentMapper;
    private final ParentBindingInvitationMapper parentBindingInvitationMapper;
    private final InvitationTokenService invitationTokenService;
    private final IdGenerator idGenerator;

    public ParentBindingInvitationApplicationService(
            StudentMapper studentMapper,
            OrganizationMapper organizationMapper,
            OrganizationAdminMapper organizationAdminMapper,
            StudentOrganizationMapper studentOrganizationMapper,
            ParentStudentMapper parentStudentMapper,
            ParentBindingInvitationMapper parentBindingInvitationMapper,
            InvitationTokenService invitationTokenService,
            IdGenerator idGenerator
    ) {
        this.studentMapper = studentMapper;
        this.organizationMapper = organizationMapper;
        this.organizationAdminMapper = organizationAdminMapper;
        this.studentOrganizationMapper = studentOrganizationMapper;
        this.parentStudentMapper = parentStudentMapper;
        this.parentBindingInvitationMapper = parentBindingInvitationMapper;
        this.invitationTokenService = invitationTokenService;
        this.idGenerator = idGenerator;
    }

    /** 创建有效期固定为七天的一次性家长绑定邀请。 */
    @Transactional
    public IssuedParentBindingInvitation create(
            AuthenticatedUser currentUser, Long studentId, CreateParentBindingInvitationCommand command
    ) {
        Objects.requireNonNull(currentUser, "当前登录用户不能为空");
        Objects.requireNonNull(command, "创建邀请请求不能为空");
        requireRole(currentUser, ORGANIZATION_ADMIN_ROLE, "仅机构管理员可创建家长绑定邀请");
        if (studentId == null) {
            throw new IllegalArgumentException("学生标识不能为空");
        }
        if (studentMapper.findById(studentId) == null) {
            throw new ResourceNotFoundException("学生档案不存在：" + studentId);
        }

        Long organizationId = command.organizationId();
        if (organizationId == null) {
            throw new IllegalArgumentException("机构标识不能为空");
        }
        Organization organization = organizationMapper.findById(organizationId);
        if (organization == null) {
            throw new ResourceNotFoundException("机构不存在：" + organizationId);
        }
        if (organization.status() != OrganizationStatus.ENABLED) {
            throw new IllegalStateException("机构已停用，不能创建家长绑定邀请");
        }
        if (!organizationAdminMapper.exists(currentUser.userId(), organizationId)
                || !studentOrganizationMapper.existsActiveByStudentAndOrganization(studentId, organizationId)) {
            throw new SystemOperationAccessDeniedException("当前机构管理员无权为该学生创建家长绑定邀请");
        }
        if (parentStudentMapper.existsActiveByStudentId(studentId)) {
            throw new IllegalStateException("学生已存在有效主家长关系，不能再次邀请绑定");
        }

        LocalDateTime now = LocalDateTime.now();
        parentBindingInvitationMapper.expirePendingByStudentId(studentId, now);
        if (parentBindingInvitationMapper.existsPendingByStudentId(studentId)) {
            throw new IllegalStateException("该学生已有未过期的待处理家长绑定邀请");
        }

        String acceptToken = invitationTokenService.newToken();
        ParentBindingInvitation invitation = ParentBindingInvitation.pending(
                idGenerator.nextId(), studentId, organizationId, currentUser.userId(),
                invitationTokenService.hash(acceptToken), now.plusDays(INVITATION_VALID_DAYS)
        );
        try {
            parentBindingInvitationMapper.insert(invitation);
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException("该学生已有未过期的待处理家长绑定邀请", exception);
        }
        ParentBindingInvitation persistedInvitation = parentBindingInvitationMapper.findById(invitation.id());
        if (persistedInvitation == null) {
            throw new IllegalStateException("家长绑定邀请写入后未找到");
        }
        return new IssuedParentBindingInvitation(persistedInvitation, acceptToken);
    }

    /** 家长接受有效邀请后，在同一事务内建立唯一主家长关系。 */
    @Transactional
    public void accept(AuthenticatedUser currentUser, RespondParentBindingInvitationCommand command) {
        respond(currentUser, command, ParentBindingInvitationStatus.ACCEPTED);
    }

    /** 家长拒绝有效邀请，仅关闭邀请，不改变学生已有关系。 */
    @Transactional
    public void reject(AuthenticatedUser currentUser, RespondParentBindingInvitationCommand command) {
        respond(currentUser, command, ParentBindingInvitationStatus.REJECTED);
    }

    private void respond(
            AuthenticatedUser currentUser,
            RespondParentBindingInvitationCommand command,
            ParentBindingInvitationStatus targetStatus
    ) {
        Objects.requireNonNull(currentUser, "当前登录用户不能为空");
        Objects.requireNonNull(command, "响应邀请请求不能为空");
        requireRole(currentUser, PARENT_ROLE, "仅家长可响应家长绑定邀请");
        if (command.invitationId() == null) {
            throw new IllegalArgumentException("邀请标识不能为空");
        }
        String acceptToken = requiredToken(command.acceptToken());
        ParentBindingInvitation invitation = parentBindingInvitationMapper.findById(command.invitationId());
        if (invitation == null) {
            throw new ResourceNotFoundException("家长绑定邀请不存在：" + command.invitationId());
        }
        if (invitation.status() != ParentBindingInvitationStatus.PENDING) {
            throw new IllegalStateException("家长绑定邀请已处理，不能重复响应");
        }

        LocalDateTime now = LocalDateTime.now();
        if (!invitation.expiresAt().isAfter(now)) {
            parentBindingInvitationMapper.expirePendingByStudentId(invitation.studentId(), now);
            throw new IllegalStateException("家长绑定邀请已过期");
        }
        if (!invitationTokenService.hash(acceptToken).equals(invitation.tokenHash())) {
            throw new SystemOperationAccessDeniedException("家长绑定邀请令牌无效");
        }
        if (targetStatus == ParentBindingInvitationStatus.ACCEPTED
                && parentStudentMapper.existsActiveByStudentId(invitation.studentId())) {
            throw new IllegalStateException("学生已存在有效主家长关系，不能接受该邀请");
        }

        int affectedRows = parentBindingInvitationMapper.respondIfPending(
                invitation.id(), targetStatus, closedScopeKey(invitation.id()), currentUser.userId(), now
        );
        if (affectedRows != 1) {
            throw new IllegalStateException("家长绑定邀请状态已变化，请刷新后重试");
        }
        if (targetStatus == ParentBindingInvitationStatus.ACCEPTED) {
            try {
                parentStudentMapper.insertPrimary(idGenerator.nextId(), currentUser.userId(), invitation.studentId());
            } catch (DataIntegrityViolationException exception) {
                throw new IllegalStateException("学生已存在有效主家长关系，不能接受该邀请", exception);
            }
        }
    }

    private void requireRole(AuthenticatedUser currentUser, String roleCode, String message) {
        if (!currentUser.roleCodes().contains(roleCode)) {
            throw new SystemOperationAccessDeniedException(message);
        }
    }

    private String requiredToken(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("邀请令牌不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("邀请令牌长度不能超过 128 个字符");
        }
        return normalized;
    }

    private String closedScopeKey(Long invitationId) {
        return "CLOSED:" + invitationId;
    }
}
