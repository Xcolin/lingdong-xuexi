package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.infrastructure.persistence.StudentWechatBindingMapper;
import com.lingdong.learning.auth.infrastructure.security.SessionTokenService;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.student.domain.ParentRelationship;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.domain.StudentStatus;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

/** 编排学生微信身份交换、双重绑定和快捷登录，绝不创建学生账号或家长关系。 */
@Service
public class StudentWechatAuthenticationService {
    private static final String FEATURE_CODE = "STUDENT_WECHAT_AUTH";
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private final ParentWechatIdentityGateway identityGateway;
    private final ParentWechatIntegrationAccess integrationAccess;
    private final StudentWechatIdentityTicketService identityTicketService;
    private final StudentWechatVerificationTicketService verificationTicketService;
    private final StudentWechatBindingMapper bindingMapper;
    private final StudentCodeLoginApplicationService codeLoginService;
    private final ParentStudentMapper relationshipMapper;
    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final ParentSmsVerificationService smsService;
    private final AuthenticationApplicationService sessionService;
    private final FeatureAccessService featureAccessService;
    private final SessionTokenService tokenService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public StudentWechatAuthenticationService(
            ParentWechatIdentityGateway identityGateway,
            ParentWechatIntegrationAccess integrationAccess,
            StudentWechatIdentityTicketService identityTicketService,
            StudentWechatVerificationTicketService verificationTicketService,
            StudentWechatBindingMapper bindingMapper,
            StudentCodeLoginApplicationService codeLoginService,
            ParentStudentMapper relationshipMapper,
            StudentMapper studentMapper,
            UserMapper userMapper,
            ParentSmsVerificationService smsService,
            AuthenticationApplicationService sessionService,
            FeatureAccessService featureAccessService,
            SessionTokenService tokenService,
            IdGenerator idGenerator,
            Clock clock
    ) {
        this.identityGateway = identityGateway;
        this.integrationAccess = integrationAccess;
        this.identityTicketService = identityTicketService;
        this.verificationTicketService = verificationTicketService;
        this.bindingMapper = bindingMapper;
        this.codeLoginService = codeLoginService;
        this.relationshipMapper = relationshipMapper;
        this.studentMapper = studentMapper;
        this.userMapper = userMapper;
        this.smsService = smsService;
        this.sessionService = sessionService;
        this.featureAccessService = featureAccessService;
        this.tokenService = tokenService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public StudentWechatSessionExchange exchange(StudentWechatSessionCommand command) {
        Objects.requireNonNull(command, "学生微信登录请求不能为空");
        requireFeatureAndIntegration();
        String temporaryCode = required(command.temporaryCode(), "微信临时凭证", 128);
        String deviceId = required(command.deviceId(), "设备标识", 128);
        String deviceName = required(command.deviceName(), "设备名称", 100);
        ParentWechatIdentity identity = identityGateway.exchange(temporaryCode);
        StudentWechatBinding binding = bindingMapper.findByIdentity(identity.appId(), identity.openId());
        if (binding == null) {
            return StudentWechatSessionExchange.bindingRequired(identityTicketService.issue(identity));
        }

        Student student = studentMapper.findById(binding.studentId());
        User studentUser = userMapper.findById(binding.studentUserId());
        requireEnabledStudent(binding, student, studentUser);
        LocalDateTime now = LocalDateTime.now(clock);
        if (bindingMapper.touchLastLogin(binding.id(), now) != 1) {
            throw new StudentAuthenticationFailedException();
        }
        AuthenticatedSession session = sessionService.createSession(
                studentUser.id(), AuthClientType.MINIAPP, deviceId, deviceName);
        return StudentWechatSessionExchange.authenticated(
                new StudentWechatAuthenticatedSession(session, studentUser.username()));
    }

    @Transactional(noRollbackFor = {
            StudentAuthenticationFailedException.class,
            CaptchaRequiredException.class,
            StudentAccountLockedException.class
    })
    public StudentWechatBindingCodeChallenge issueBindingCode(StudentWechatBindingCodeCommand command) {
        Objects.requireNonNull(command, "学生微信绑定验证码请求不能为空");
        requireFeatureAndIntegration();
        String deviceId = required(command.deviceId(), "设备标识", 128);
        String deviceName = required(command.deviceName(), "设备名称", 100);
        String bindingTicket = required(command.bindingTicket(), "微信绑定凭证", 256);
        ParentWechatIdentity identity = identityTicketService.find(bindingTicket);
        if (bindingMapper.findByIdentity(identity.appId(), identity.openId()) != null) {
            throw new StudentAuthenticationFailedException();
        }

        VerifiedStudentIdentity verified = codeLoginService.verifyForBinding(new StudentCodeLoginCommand(
                command.studentAccount(), command.loginCode(), deviceId, deviceName,
                command.captchaChallengeId(), command.captchaAnswer(), command.sourceAddress()));
        ParentWechatIdentity consumedIdentity = identityTicketService.consume(bindingTicket);
        if (!identity.equals(consumedIdentity)) {
            throw new StudentAuthenticationFailedException();
        }
        if (bindingMapper.findByStudentId(verified.studentId()) != null) {
            throw new StudentAuthenticationFailedException();
        }
        ParentRelationship primary = relationshipMapper.findActivePrimaryByStudentIdForUpdate(verified.studentId());
        User parent = primary == null ? null : userMapper.findByIdForUpdate(primary.parentUserId());
        requireEnabledPrimaryParent(parent);

        IssuedParentSmsCode issued = smsService.issue(
                parent.mobile(), ParentSmsPurpose.STUDENT_WECHAT_BIND, AuthClientType.MINIAPP,
                tokenService.hash(normalizedSource(command.sourceAddress())));
        String verificationTicket = verificationTicketService.issue(new StudentWechatVerificationTicket(
                consumedIdentity.withoutSessionKey(), verified.studentId(), verified.studentUserId(),
                parent.id(), parent.mobile(), deviceId));
        return new StudentWechatBindingCodeChallenge(
                verificationTicket, maskMobile(parent.mobile()), issued.expiresAt(), issued.retryAfterSeconds());
    }

    @Transactional
    public StudentWechatAuthenticatedSession bind(StudentWechatBindingCommand command) {
        Objects.requireNonNull(command, "学生微信绑定请求不能为空");
        requireFeatureAndIntegration();
        String deviceId = required(command.deviceId(), "设备标识", 128);
        String deviceName = required(command.deviceName(), "设备名称", 100);
        StudentWechatVerificationTicket verification = verificationTicketService.consume(
                required(command.verificationTicket(), "微信绑定校验凭证", 256));
        if (!deviceId.equals(verification.deviceId())) {
            throw new StudentAuthenticationFailedException();
        }

        Student student = studentMapper.findByIdForUpdate(verification.studentId());
        User studentUser = userMapper.findByIdForUpdate(verification.studentUserId());
        requireEnabledStudent(null, student, studentUser);
        ParentRelationship primary = relationshipMapper.findActivePrimaryByStudentIdForUpdate(student.id());
        User parent = primary == null ? null : userMapper.findByIdForUpdate(primary.parentUserId());
        requireEnabledPrimaryParent(parent);
        if (!parent.id().equals(verification.primaryParentUserId())
                || !parent.mobile().equals(verification.primaryParentMobile())) {
            throw new StudentWechatBindingUnavailableException();
        }
        if (bindingMapper.findByStudentIdForUpdate(student.id()) != null
                || bindingMapper.findByIdentity(
                verification.identity().appId(), verification.identity().openId()) != null) {
            throw new StudentAuthenticationFailedException();
        }

        smsService.verifyAndConsume(parent.mobile(), ParentSmsPurpose.STUDENT_WECHAT_BIND,
                AuthClientType.MINIAPP, command.smsCode());
        LocalDateTime now = LocalDateTime.now(clock);
        StudentWechatBinding binding = new StudentWechatBinding(
                idGenerator.nextId(), student.id(), studentUser.id(), verification.identity().appId(),
                verification.identity().openId(), verification.identity().unionId(), now, now);
        try {
            if (bindingMapper.insert(binding) != 1) {
                throw new StudentAuthenticationFailedException();
            }
        } catch (DataIntegrityViolationException exception) {
            throw new StudentAuthenticationFailedException();
        }
        insertAudit(binding, StudentWechatBindingAuditEvent.BIND,
                studentUser.id(), AuthClientType.MINIAPP, now);
        AuthenticatedSession session = sessionService.createSession(
                studentUser.id(), AuthClientType.MINIAPP, deviceId, deviceName);
        return new StudentWechatAuthenticatedSession(session, studentUser.username());
    }

    private void insertAudit(
            StudentWechatBinding binding,
            StudentWechatBindingAuditEvent event,
            Long operatorUserId,
            AuthClientType clientType,
            LocalDateTime occurredAt
    ) {
        StudentWechatBindingAudit audit = new StudentWechatBindingAudit(
                idGenerator.nextId(), binding.id(), binding.studentId(), binding.studentUserId(),
                event, operatorUserId, clientType, occurredAt);
        if (bindingMapper.insertAudit(audit) != 1) {
            throw new IllegalStateException("学生微信绑定审计保存失败");
        }
    }

    private void requireFeatureAndIntegration() {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        integrationAccess.requireAvailable();
    }

    private void requireEnabledStudent(StudentWechatBinding binding, Student student, User user) {
        boolean matchesBinding = binding == null || (binding.studentId().equals(student == null ? null : student.id())
                && binding.studentUserId().equals(user == null ? null : user.id()));
        if (!matchesBinding || student == null || student.status() != StudentStatus.ENABLED
                || user == null || user.type() != UserType.STUDENT || user.status() != UserStatus.ENABLED
                || !user.id().equals(student.studentUserId())) {
            throw new StudentAuthenticationFailedException();
        }
    }

    private void requireEnabledPrimaryParent(User parent) {
        if (parent == null || parent.type() != UserType.FAMILY || parent.status() != UserStatus.ENABLED
                || parent.mobile() == null || !MOBILE_PATTERN.matcher(parent.mobile()).matches()) {
            throw new StudentWechatBindingUnavailableException();
        }
    }

    private String normalizedSource(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }

    private String maskMobile(String mobile) {
        return mobile.substring(0, 3) + "****" + mobile.substring(7);
    }

    private String required(String value, String fieldName, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过" + maxLength + "个字符");
        }
        return normalized;
    }
}
