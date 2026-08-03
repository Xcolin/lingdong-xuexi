package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.domain.DeviceSessionRecord;
import com.lingdong.learning.auth.domain.DeviceSessionStatus;
import com.lingdong.learning.auth.infrastructure.config.AuthenticationProperties;
import com.lingdong.learning.auth.infrastructure.persistence.DeviceSessionMapper;
import com.lingdong.learning.auth.infrastructure.security.SessionTokenService;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.domain.StudentStatus;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** 管理平台账号密码和可撤销的 Web 设备会话。 */
@Service
public class AuthenticationApplicationService {
    private static final String SYSTEM_ADMIN_ROLE = "SYS_ADMIN";

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final StudentMapper studentMapper;
    private final DeviceSessionMapper sessionMapper;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final SessionTokenService tokenService;
    private final AuthenticationProperties properties;
    private final IdGenerator idGenerator;

    public AuthenticationApplicationService(
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            StudentMapper studentMapper,
            DeviceSessionMapper sessionMapper,
            PasswordPolicy passwordPolicy,
            PasswordEncoder passwordEncoder,
            SessionTokenService tokenService,
            AuthenticationProperties properties,
            IdGenerator idGenerator
    ) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.studentMapper = studentMapper;
        this.sessionMapper = sessionMapper;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.properties = properties;
        this.idGenerator = idGenerator;
    }

    /** 仅系统管理员可以为现有平台账号设置或重置密码。 */
    @Transactional
    public void setPlatformUserPassword(SetPlatformUserPasswordCommand command) {
        Objects.requireNonNull(command, "设置平台账号密码请求不能为空");
        requireSystemAdmin(command.operatorId());
        User user = requireUser(command.userId());
        if (user.type() != UserType.PLATFORM) {
            throw new IllegalArgumentException("仅平台账号可以设置此密码");
        }
        passwordPolicy.validate(command.password());
        if (userMapper.updatePasswordHash(user.id(), passwordEncoder.encode(command.password())) != 1) {
            throw new IllegalStateException("平台账号密码保存失败");
        }
    }

    /** 使用平台账号密码建立新的 Web 设备会话。 */
    @Transactional
    public AuthenticatedSession loginByPassword(PasswordLoginCommand command) {
        Objects.requireNonNull(command, "密码登录请求不能为空");
        String username = requiredText(command.username(), "用户账号", 64);
        String deviceId = requiredText(command.deviceId(), "设备标识", 128);
        String deviceName = requiredText(command.deviceName(), "设备名称", 100);
        User user = userMapper.findByUsername(username);
        if (!isEnabledPlatformUserWithMatchingPassword(user, command.password())) {
            throw authenticationFailed();
        }
        return createSession(user.id(), AuthClientType.WEB, deviceId, deviceName);
    }

    /** 使用刷新凭证轮换两类原始令牌，旧令牌在成功刷新后立即失效。 */
    @Transactional
    public AuthenticatedSession refreshSession(RefreshSessionCommand command) {
        Objects.requireNonNull(command, "刷新会话请求不能为空");
        String refreshTokenHash = safeTokenHash(command.refreshToken());
        DeviceSessionRecord session = sessionMapper.findActiveByRefreshTokenHash(refreshTokenHash);
        LocalDateTime now = LocalDateTime.now();
        ensureUsableSession(session, now, false);
        if (!session.refreshExpiresAt().isAfter(now)) {
            expire(session, now);
            throw authenticationFailed();
        }
        String accessToken = tokenService.newToken();
        String refreshToken = tokenService.newToken();
        LocalDateTime accessExpiresAt = now.plus(properties.getAccessTokenTtl());
        LocalDateTime refreshExpiresAt = now.plus(properties.getRefreshTokenTtl());
        if (sessionMapper.rotateTokens(session.id(), refreshTokenHash, tokenService.hash(accessToken), tokenService.hash(refreshToken),
                accessExpiresAt, refreshExpiresAt, now) != 1) {
            throw authenticationFailed();
        }
        return new AuthenticatedSession(session.id(), accessToken, refreshToken, accessExpiresAt, refreshExpiresAt);
    }

    /** 验证访问凭证并返回后续鉴权所需的最小身份摘要。 */
    public AuthenticatedUser authenticateAccessToken(String accessToken) {
        DeviceSessionRecord session = sessionMapper.findActiveByAccessTokenHash(safeTokenHash(accessToken));
        LocalDateTime now = LocalDateTime.now();
        User user = ensureUsableSession(session, now, true);
        return new AuthenticatedUser(user.id(), session.id(), user.username(), user.displayName(), session.clientType(),
                userRoleMapper.findEnabledRoleCodesByUserId(user.id()));
    }

    /** 当前用户退出指定的当前设备会话。 */
    @Transactional
    public void logoutCurrentSession(Long currentUserId, Long sessionId) {
        updateOwnedSessionStatus(currentUserId, sessionId, DeviceSessionStatus.SIGNED_OUT);
    }

    /** 当前用户只能撤销自己的指定设备会话。 */
    @Transactional
    public void signOutDevice(Long currentUserId, Long sessionId) {
        updateOwnedSessionStatus(currentUserId, sessionId, DeviceSessionStatus.REVOKED);
    }

    /** 撤销当前用户的全部活动会话，包括发起操作的会话。 */
    @Transactional
    public void signOutAllDevices(Long currentUserId) {
        requireUser(currentUserId);
        sessionMapper.revokeAllActiveByUserId(currentUserId, LocalDateTime.now());
    }

    /** 账号停用或锁定时撤销全部活动会话，旧令牌不得在恢复账号后复用。 */
    @Transactional
    public void revokeAllActiveSessionsForUser(Long userId) {
        requireUser(userId);
        sessionMapper.revokeAllActiveByUserId(userId, LocalDateTime.now());
    }

    /** 查询当前用户的活动设备，不暴露令牌或令牌摘要。 */
    public List<DeviceSession> listCurrentUserDevices(Long currentUserId) {
        requireUser(currentUserId);
        return sessionMapper.findActiveByUserId(currentUserId).stream().map(this::toDeviceSession).toList();
    }

    AuthenticatedSession createSession(Long userId, AuthClientType clientType, String deviceId, String deviceName) {
        LocalDateTime now = LocalDateTime.now();
        String accessToken = tokenService.newToken();
        String refreshToken = tokenService.newToken();
        LocalDateTime accessExpiresAt = now.plus(properties.getAccessTokenTtl());
        LocalDateTime refreshExpiresAt = now.plus(properties.getRefreshTokenTtl());
        DeviceSessionRecord session = new DeviceSessionRecord(
                idGenerator.nextId(), userId, clientType, deviceId, deviceName,
                tokenService.hash(accessToken), tokenService.hash(refreshToken), accessExpiresAt, refreshExpiresAt,
                DeviceSessionStatus.ACTIVE, now, null, null, null
        );
        if (sessionMapper.insert(session) != 1) {
            throw new IllegalStateException("设备会话保存失败");
        }
        return new AuthenticatedSession(session.id(), accessToken, refreshToken, accessExpiresAt, refreshExpiresAt);
    }

    private void updateOwnedSessionStatus(Long currentUserId, Long sessionId, DeviceSessionStatus targetStatus) {
        if (currentUserId == null) {
            throw authenticationFailed();
        }
        DeviceSessionRecord session = requireSession(sessionId);
        if (!currentUserId.equals(session.userId())) {
            throw new IllegalStateException("无权操作其他用户的设备会话");
        }
        if (session.status() == DeviceSessionStatus.ACTIVE
                && sessionMapper.updateStatusIfActive(session.id(), targetStatus, LocalDateTime.now()) != 1) {
            throw new IllegalStateException("设备会话状态更新失败");
        }
    }

    private User ensureUsableSession(DeviceSessionRecord session, LocalDateTime now, boolean accessToken) {
        if (session == null) {
            throw authenticationFailed();
        }
        if (accessToken && !session.accessExpiresAt().isAfter(now)) {
            throw authenticationFailed();
        }
        User user = userMapper.findById(session.userId());
        if (!isUsableForClient(user, session.clientType())) {
            sessionMapper.updateStatusIfActive(session.id(), DeviceSessionStatus.REVOKED, now);
            throw authenticationFailed();
        }
        return user;
    }

    private boolean isUsableForClient(User user, AuthClientType clientType) {
        if (user == null || user.status() != UserStatus.ENABLED) {
            return false;
        }
        if (clientType == AuthClientType.WEB) {
            return user.type() == UserType.PLATFORM;
        }
        if (clientType == AuthClientType.MINIAPP && user.type() == UserType.STUDENT) {
            Student student = studentMapper.findByStudentUserId(user.id());
            return student != null && student.status() == StudentStatus.ENABLED;
        }
        return false;
    }

    private void expire(DeviceSessionRecord session, LocalDateTime now) {
        sessionMapper.updateStatusIfActive(session.id(), DeviceSessionStatus.EXPIRED, now);
    }

    private boolean isEnabledPlatformUserWithMatchingPassword(User user, String password) {
        if (user == null || user.type() != UserType.PLATFORM || user.status() != UserStatus.ENABLED
                || user.passwordHash() == null || password == null) {
            return false;
        }
        try {
            return passwordEncoder.matches(password, user.passwordHash());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void requireSystemAdmin(Long operatorId) {
        if (operatorId == null || !userRoleMapper.hasRoleCode(operatorId, SYSTEM_ADMIN_ROLE)) {
            throw new SystemOperationAccessDeniedException("仅系统管理员可设置平台账号密码");
        }
    }

    private User requireUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户标识不能为空");
        }
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("用户不存在：" + userId);
        }
        return user;
    }

    private DeviceSessionRecord requireSession(Long sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("设备会话标识不能为空");
        }
        DeviceSessionRecord session = sessionMapper.findById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("设备会话不存在：" + sessionId);
        }
        return session;
    }

    private String safeTokenHash(String rawToken) {
        try {
            return tokenService.hash(rawToken);
        } catch (IllegalArgumentException exception) {
            throw authenticationFailed();
        }
    }

    private String requiredText(String value, String fieldName, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        String normalizedValue = value.trim();
        if (normalizedValue.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过" + maxLength + "个字符");
        }
        return normalizedValue;
    }

    private AuthenticationFailedException authenticationFailed() {
        return new AuthenticationFailedException();
    }

    private DeviceSession toDeviceSession(DeviceSessionRecord session) {
        return new DeviceSession(session.id(), session.clientType(), session.deviceId(), session.deviceName(),
                session.accessExpiresAt(), session.refreshExpiresAt(), session.lastActiveAt());
    }
}
