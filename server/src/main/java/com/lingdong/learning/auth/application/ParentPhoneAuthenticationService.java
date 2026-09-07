package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.infrastructure.persistence.ParentAuthenticationMapper;
import com.lingdong.learning.auth.infrastructure.config.ParentSmsProperties;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.domain.RoleStatus;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

/** 在单个事务中完成家长短信登录、首次注册、协议留痕和会话创建。 */
@Service
public class ParentPhoneAuthenticationService {
    private static final String FEATURE_CODE = "PARENT_PHONE_AUTH";
    private static final String WECHAT_FEATURE_CODE = "PARENT_WECHAT_AUTH";
    private static final String PARENT_ROLE_CODE = "PARENT";
    private static final String GLOBAL_SCOPE_KEY = "GLOBAL";

    private final ParentSmsVerificationService smsVerificationService;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final ParentAuthenticationMapper parentMapper;
    private final AuthenticationApplicationService authenticationService;
    private final FeatureAccessService featureAccessService;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final ParentSmsProperties smsProperties;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;

    public ParentPhoneAuthenticationService(
            ParentSmsVerificationService smsVerificationService,
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            RoleMapper roleMapper,
            ParentAuthenticationMapper parentMapper,
            AuthenticationApplicationService authenticationService,
            FeatureAccessService featureAccessService,
            IdGenerator idGenerator,
            Clock clock,
            ParentSmsProperties smsProperties,
            PasswordPolicy passwordPolicy,
            PasswordEncoder passwordEncoder
    ) {
        this.smsVerificationService = smsVerificationService;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.parentMapper = parentMapper;
        this.authenticationService = authenticationService;
        this.featureAccessService = featureAccessService;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.smsProperties = smsProperties;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
    }

    public ParentAuthContext getPublicContext() {
        return new ParentAuthContext(
                featureAccessService.isEnabled(FEATURE_CODE, null),
                featureAccessService.isEnabled(WECHAT_FEATURE_CODE, null),
                requiredCurrentAgreementVersion(),
                smsProperties.getCodeTtl().toSeconds(),
                smsProperties.getIssueRateWindow().toSeconds());
    }

    public IssuedParentSmsCode issueSmsCode(
            String mobile,
            ParentSmsPurpose purpose,
            com.lingdong.learning.auth.domain.AuthClientType clientType,
            String sourceDigest
    ) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        requirePublicSmsPurpose(purpose);
        return smsVerificationService.issue(mobile, purpose, clientType, sourceDigest);
    }

    private void requirePublicSmsPurpose(ParentSmsPurpose purpose) {
        if (purpose == ParentSmsPurpose.CHANGE_MOBILE_CURRENT
                || purpose == ParentSmsPurpose.CHANGE_MOBILE_NEW
                || purpose == ParentSmsPurpose.ACCOUNT_CANCELLATION
                || purpose == ParentSmsPurpose.MANUAL_MOBILE_RECOVERY_NEW) {
            throw new IllegalArgumentException("该验证码用途仅允许登录后使用");
        }
    }

    public ParentAuthState getParentState(Long userId) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        if (userId == null) {
            throw new AuthenticationFailedException();
        }
        User user = userMapper.findById(userId);
        ParentProfile profile = requireExistingParent(user);
        return buildParentAuthState(user, profile);
    }

    /** 仅对纳入手机号认证档案的家长返回准入状态，兼容历史家长账号。 */
    public ParentAuthState getParentAccessState(Long userId) {
        if (userId == null || !featureAccessService.isEnabled(FEATURE_CODE, null)) {
            return null;
        }
        User user = userMapper.findById(userId);
        ParentProfile profile = user == null ? null : parentMapper.findProfileByUserId(user.id());
        if (profile == null) {
            return null;
        }
        if (!isEnabledParent(user, profile)) {
            throw new AuthenticationFailedException();
        }
        return buildParentAuthState(user, profile);
    }

    private ParentAuthState buildParentAuthState(User user, ParentProfile profile) {
        String currentVersion = requiredCurrentAgreementVersion();
        return new ParentAuthState(
                profile.onboardingStatus() != ParentOnboardingStatus.COMPLETED,
                !parentMapper.hasAgreementAcceptance(user.id(), currentVersion),
                currentVersion);
    }

    @Transactional
    public ParentPhoneAuthenticatedSession loginBySms(ParentSmsLoginCommand command) {
        Objects.requireNonNull(command, "家长短信登录请求不能为空");
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        String deviceId = requiredText(command.deviceId(), "设备标识", 128);
        String deviceName = requiredText(command.deviceName(), "设备名称", 100);
        String sourceAddressHash = optionalText(command.sourceAddressHash(), "来源地址摘要", 64);
        smsVerificationService.verifyAndConsume(
                command.mobile(), ParentSmsPurpose.REGISTER_OR_LOGIN, command.clientType(), command.code());

        VerifiedParentAccount parent = findOrRegisterVerifiedParent(command, sourceAddressHash);

        AuthenticatedSession session = authenticationService.createSession(
                parent.userId(), command.clientType(), deviceId, deviceName);
        return new ParentPhoneAuthenticatedSession(
                session,
                parent.onboardingRequired(),
                false,
                parent.currentAgreementVersion());
    }

    /** 家长密码登录按调用端建立独立会话，并返回统一的协议与引导状态。 */
    @Transactional
    public ParentPhoneAuthenticatedSession loginByPassword(ParentPasswordLoginCommand command) {
        Objects.requireNonNull(command, "家长密码登录请求不能为空");
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        String mobile = requiredText(command.mobile(), "手机号", 32);
        String deviceId = requiredText(command.deviceId(), "设备标识", 128);
        String deviceName = requiredText(command.deviceName(), "设备名称", 100);
        if (command.clientType() == null) {
            throw new IllegalArgumentException("客户端类型不能为空");
        }

        User user = userMapper.findByUsername(mobile);
        ParentProfile profile = user == null ? null : parentMapper.findProfileByUserId(user.id());
        if (!isEnabledParent(user, profile) || !matchesPassword(command.password(), user.passwordHash())) {
            throw new AuthenticationFailedException();
        }

        String currentAgreementVersion = requiredCurrentAgreementVersion();
        AuthenticatedSession session = authenticationService.createSession(
                user.id(), command.clientType(), deviceId, deviceName);
        return new ParentPhoneAuthenticatedSession(
                session,
                profile.onboardingStatus() != ParentOnboardingStatus.COMPLETED,
                !parentMapper.hasAgreementAcceptance(user.id(), currentAgreementVersion),
                currentAgreementVersion);
    }

    /** 已登录家长接受后台当前版本协议，重复提交不产生重复事实。 */
    @Transactional
    public void acceptCurrentAgreement(
            Long userId,
            com.lingdong.learning.auth.domain.AuthClientType clientType,
            String agreementVersion,
            String sourceAddressHash
    ) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        User parent = requireParentById(userId);
        String currentVersion = requiredCurrentAgreementVersion();
        if (!currentVersion.equals(agreementVersion)) {
            throw new ParentAgreementAcceptanceRequiredException(currentVersion);
        }
        if (parentMapper.hasAgreementAcceptance(parent.id(), currentVersion)) {
            return;
        }
        insertAgreementAcceptance(
                parent.id(), clientType, currentVersion,
                optionalText(sourceAddressHash, "来源地址摘要", 64), LocalDateTime.now(clock));
    }

    /** 将家长首次引导从待完成原子推进为已完成，重复提交保持幂等。 */
    @Transactional
    public void completeOnboarding(Long userId) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        if (userId == null) {
            throw new AuthenticationFailedException();
        }
        User parent = userMapper.findByIdForUpdate(userId);
        ParentProfile profile = requireExistingParent(parent);
        if (profile.onboardingStatus() == ParentOnboardingStatus.COMPLETED) {
            return;
        }
        if (parentMapper.completeOnboarding(parent.id(), LocalDateTime.now(clock)) != 1) {
            throw new IllegalStateException("家长首次引导状态更新失败");
        }
    }

    /** 已登录家长设置或更新密码，数据库仅保存密码编码摘要。 */
    @Transactional
    public void setPassword(Long userId, String password) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        User parent = requireParentById(userId);
        passwordPolicy.validate(password);
        if (userMapper.updatePasswordHash(parent.id(), passwordEncoder.encode(password)) != 1) {
            throw new IllegalStateException("家长密码保存失败");
        }
    }

    /** 通过手机号短信验证重置家长密码，账号状态统一使用中性认证失败。 */
    @Transactional
    public void resetPassword(ParentPasswordResetCommand command) {
        Objects.requireNonNull(command, "家长密码重置请求不能为空");
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        passwordPolicy.validate(command.newPassword());
        smsVerificationService.verifyAndConsume(
                command.mobile(), ParentSmsPurpose.RESET_PASSWORD, command.clientType(), command.code());
        User user = userMapper.findByMobileForUpdate(command.mobile());
        ParentProfile profile = user == null ? null : parentMapper.findProfileByUserId(user.id());
        if (!isEnabledParent(user, profile)) {
            throw new AuthenticationFailedException();
        }
        if (userMapper.updatePasswordHash(user.id(), passwordEncoder.encode(command.newPassword())) != 1) {
            throw new IllegalStateException("家长密码重置失败");
        }
    }

    /** 微信首次绑定必须先消费独立短信验证码，再复用手机号唯一家长注册规则。 */
    @Transactional
    public VerifiedParentAccount verifyWechatBindingMobile(ParentWechatMobileBindingCommand command) {
        Objects.requireNonNull(command, "微信手机号绑定请求不能为空");
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        smsVerificationService.verifyAndConsume(
                command.mobile(), ParentSmsPurpose.WECHAT_BIND, AuthClientType.MINIAPP, command.smsCode());

        ParentSmsLoginCommand accountCommand = new ParentSmsLoginCommand(
                command.mobile(), command.smsCode(), AuthClientType.MINIAPP,
                command.deviceId(), command.deviceName(), command.agreementAccepted(),
                command.agreementVersion(), command.sourceAddressHash());
        return findOrRegisterVerifiedParent(
                accountCommand, optionalText(command.sourceAddressHash(), "来源地址摘要", 64));
    }

    /** 关系邀请仅核验并返回唯一家长账号，不在此处创建关系或登录会话。 */
    @Transactional
    public VerifiedParentAccount verifyRelationshipInvitationMobile(
            ParentRelationshipMobileVerificationCommand command
    ) {
        Objects.requireNonNull(command, "家长关系手机号核验请求不能为空");
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        requireRelationshipPurpose(command.purpose());
        smsVerificationService.verifyAndConsume(
                command.mobile(), command.purpose(), command.clientType(), command.smsCode());
        ParentSmsLoginCommand accountCommand = new ParentSmsLoginCommand(
                command.mobile(), command.smsCode(), command.clientType(), null, null,
                command.agreementAccepted(), command.agreementVersion(), command.sourceAddressHash());
        return findOrRegisterVerifiedParent(
                accountCommand, optionalText(command.sourceAddressHash(), "来源地址摘要", 64));
    }

    /** 拒绝关系邀请时只证明手机号控制权，不查找或创建家长账号。 */
    public void verifyRelationshipInvitationCodeOnly(
            String mobile,
            String smsCode,
            ParentSmsPurpose purpose,
            com.lingdong.learning.auth.domain.AuthClientType clientType
    ) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        requireRelationshipPurpose(purpose);
        smsVerificationService.verifyAndConsume(mobile, purpose, clientType, smsCode);
    }

    /** 为已完成关系手机号核验的家长创建指定客户端独立会话。 */
    @Transactional
    public ParentPhoneAuthenticatedSession createRelationshipSession(
            VerifiedParentAccount account,
            com.lingdong.learning.auth.domain.AuthClientType clientType,
            String deviceId,
            String deviceName
    ) {
        Objects.requireNonNull(account, "已验证家长账号不能为空");
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        if (clientType == null) {
            throw new IllegalArgumentException("客户端类型不能为空");
        }
        AuthenticatedSession session = authenticationService.createSession(
                account.userId(), clientType,
                requiredText(deviceId, "设备标识", 128),
                requiredText(deviceName, "设备名称", 100));
        return new ParentPhoneAuthenticatedSession(
                session, account.onboardingRequired(), false, account.currentAgreementVersion());
    }

    private void requireRelationshipPurpose(ParentSmsPurpose purpose) {
        if (purpose != ParentSmsPurpose.SECONDARY_PARENT_BIND
                && purpose != ParentSmsPurpose.PRIMARY_PARENT_TRANSFER) {
            throw new IllegalArgumentException("验证码用途不属于家长关系管理");
        }
    }

    private VerifiedParentAccount findOrRegisterVerifiedParent(
            ParentSmsLoginCommand command,
            String sourceAddressHash
    ) {
        String currentAgreementVersion = requiredCurrentAgreementVersion();
        User user = userMapper.findByMobileForUpdate(command.mobile());
        ParentProfile profile;
        if (user == null) {
            requireCurrentAgreementAcceptance(command, currentAgreementVersion);
            RegisteredParent registered = registerParent(command, currentAgreementVersion, sourceAddressHash);
            user = registered.user();
            profile = registered.profile();
        } else {
            profile = requireExistingParent(user);
            ensureCurrentAgreementAccepted(user.id(), command, currentAgreementVersion, sourceAddressHash);
        }
        return new VerifiedParentAccount(
                user.id(), profile.onboardingStatus() != ParentOnboardingStatus.COMPLETED,
                currentAgreementVersion);
    }

    private RegisteredParent registerParent(
            ParentSmsLoginCommand command,
            String agreementVersion,
            String sourceAddressHash
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        User user = User.create(idGenerator.nextId(), command.mobile(), "家长用户", command.mobile(), UserType.FAMILY);
        if (userMapper.insert(user) != 1) {
            throw new IllegalStateException("家长账号保存失败");
        }
        Role parentRole = roleMapper.findByCode(PARENT_ROLE_CODE);
        if (parentRole == null || parentRole.status() != RoleStatus.ENABLED) {
            throw new IllegalStateException("内置家长角色未启用");
        }
        if (userRoleMapper.insert(
                idGenerator.nextId(), user.id(), parentRole.id(), null, GLOBAL_SCOPE_KEY) != 1) {
            throw new IllegalStateException("家长角色授权失败");
        }
        ParentProfile profile = new ParentProfile(
                idGenerator.nextId(), user.id(), ParentOnboardingStatus.PENDING, now, null);
        if (parentMapper.insertProfile(profile) != 1) {
            throw new IllegalStateException("家长认证档案保存失败");
        }
        insertAgreementAcceptance(user.id(), command, agreementVersion, sourceAddressHash, now);
        return new RegisteredParent(user, profile);
    }

    private ParentProfile requireExistingParent(User user) {
        ParentProfile profile = user == null ? null : parentMapper.findProfileByUserId(user.id());
        if (!isEnabledParent(user, profile)) {
            throw new AuthenticationFailedException();
        }
        return profile;
    }

    private User requireParentById(Long userId) {
        if (userId == null) {
            throw new AuthenticationFailedException();
        }
        User user = userMapper.findByIdForUpdate(userId);
        requireExistingParent(user);
        return user;
    }

    private boolean isEnabledParent(User user, ParentProfile profile) {
        return user != null
                && user.type() == UserType.FAMILY
                && user.status() == UserStatus.ENABLED
                && userRoleMapper.hasRoleCode(user.id(), PARENT_ROLE_CODE)
                && profile != null;
    }

    private boolean matchesPassword(String password, String passwordHash) {
        if (password == null || password.isBlank() || passwordHash == null) {
            return false;
        }
        try {
            return passwordEncoder.matches(password, passwordHash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void ensureCurrentAgreementAccepted(
            Long userId,
            ParentSmsLoginCommand command,
            String currentAgreementVersion,
            String sourceAddressHash
    ) {
        if (parentMapper.hasAgreementAcceptance(userId, currentAgreementVersion)) {
            return;
        }
        requireCurrentAgreementAcceptance(command, currentAgreementVersion);
        insertAgreementAcceptance(
                userId, command, currentAgreementVersion, sourceAddressHash, LocalDateTime.now(clock));
    }

    private void insertAgreementAcceptance(
            Long userId,
            ParentSmsLoginCommand command,
            String agreementVersion,
            String sourceAddressHash,
            LocalDateTime acceptedAt
    ) {
        insertAgreementAcceptance(
                userId, command.clientType(), agreementVersion, sourceAddressHash, acceptedAt);
    }

    private void insertAgreementAcceptance(
            Long userId,
            com.lingdong.learning.auth.domain.AuthClientType clientType,
            String agreementVersion,
            String sourceAddressHash,
            LocalDateTime acceptedAt
    ) {
        ParentAgreementAcceptance acceptance = new ParentAgreementAcceptance(
                idGenerator.nextId(), userId, agreementVersion, clientType, sourceAddressHash, acceptedAt);
        if (parentMapper.insertAgreementAcceptance(acceptance) != 1) {
            throw new IllegalStateException("家长用户协议接受记录保存失败");
        }
    }

    private void requireCurrentAgreementAcceptance(ParentSmsLoginCommand command, String currentVersion) {
        if (!command.agreementAccepted() || !currentVersion.equals(command.agreementVersion())) {
            throw new ParentAgreementAcceptanceRequiredException(currentVersion);
        }
    }

    private String requiredCurrentAgreementVersion() {
        String version = parentMapper.findCurrentAgreementVersion();
        if (version == null || version.isBlank() || version.length() > 32) {
            throw new IllegalStateException("当前家长用户协议版本未正确配置");
        }
        return version;
    }

    private String requiredText(String value, String fieldName, int maxLength) {
        String normalized = optionalText(value, fieldName, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return normalized;
    }

    private String optionalText(String value, String fieldName, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private record RegisteredParent(User user, ParentProfile profile) {
    }
}
