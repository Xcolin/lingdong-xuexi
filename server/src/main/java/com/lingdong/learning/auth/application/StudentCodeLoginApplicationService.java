package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.infrastructure.config.StudentLoginProtectionProperties;
import com.lingdong.learning.auth.infrastructure.security.SessionTokenService;
import com.lingdong.learning.auth.infrastructure.security.StudentLoginCodeDigest;
import com.lingdong.learning.auth.infrastructure.security.StudentLoginCodeHasher;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.domain.StudentCredential;
import com.lingdong.learning.student.domain.StudentStatus;
import com.lingdong.learning.student.infrastructure.persistence.StudentCredentialMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

/** 学生账号、登录码和图形验证码的数据库行锁登录状态机。 */
@Service
public class StudentCodeLoginApplicationService {
    private static final String FEATURE_CODE = "STUDENT_CODE_LOGIN";
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("\\d{8}");
    private static final Pattern CODE_PATTERN = Pattern.compile("\\d{4}");

    private final UserMapper userMapper;
    private final StudentMapper studentMapper;
    private final StudentCredentialMapper credentialMapper;
    private final StudentLoginCodeHasher loginCodeHasher;
    private final CaptchaChallengeService captchaChallengeService;
    private final StudentLoginProtectionStore protectionStore;
    private final SessionTokenService tokenService;
    private final AuthenticationApplicationService authenticationService;
    private final FeatureAccessService featureAccessService;
    private final StudentLoginProtectionProperties properties;
    private final Clock clock;
    private final StudentLoginCodeDigest dummyDigest;

    public StudentCodeLoginApplicationService(
            UserMapper userMapper,
            StudentMapper studentMapper,
            StudentCredentialMapper credentialMapper,
            StudentLoginCodeHasher loginCodeHasher,
            CaptchaChallengeService captchaChallengeService,
            StudentLoginProtectionStore protectionStore,
            SessionTokenService tokenService,
            AuthenticationApplicationService authenticationService,
            FeatureAccessService featureAccessService,
            StudentLoginProtectionProperties properties,
            Clock clock
    ) {
        this.userMapper = userMapper;
        this.studentMapper = studentMapper;
        this.credentialMapper = credentialMapper;
        this.loginCodeHasher = loginCodeHasher;
        this.captchaChallengeService = captchaChallengeService;
        this.protectionStore = protectionStore;
        this.tokenService = tokenService;
        this.authenticationService = authenticationService;
        this.featureAccessService = featureAccessService;
        this.properties = properties;
        this.clock = clock;
        this.dummyDigest = loginCodeHasher.hash("0000");
    }

    @Transactional(noRollbackFor = {
            StudentAuthenticationFailedException.class,
            CaptchaRequiredException.class,
            StudentAccountLockedException.class
    })
    public AuthenticatedSession login(StudentCodeLoginCommand command) {
        Objects.requireNonNull(command, "学生登录请求不能为空");
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        String account = normalize(command.studentAccount());
        String deviceId = required(command.deviceId(), "设备标识", 128);
        String deviceName = required(command.deviceName(), "设备名称", 100);
        String sourceAddress = normalize(command.sourceAddress());
        protectionStore.checkLoginRate(
                tokenService.hash((account == null ? "invalid" : account) + ":" + deviceId),
                tokenService.hash(sourceAddress == null ? "unknown" : sourceAddress)
        );

        if (account == null || !ACCOUNT_PATTERN.matcher(account).matches()
                || command.loginCode() == null || !CODE_PATTERN.matcher(command.loginCode()).matches()) {
            performDummyComparison(command.loginCode());
            throw new StudentAuthenticationFailedException();
        }
        User user = userMapper.findByUsername(account);
        if (user == null || user.type() != UserType.STUDENT || user.status() != UserStatus.ENABLED) {
            performDummyComparison(command.loginCode());
            throw new StudentAuthenticationFailedException();
        }
        Student student = studentMapper.findByStudentUserId(user.id());
        if (student == null || student.status() != StudentStatus.ENABLED) {
            performDummyComparison(command.loginCode());
            throw new StudentAuthenticationFailedException();
        }
        StudentCredential credential = credentialMapper.findByStudentUserIdForUpdate(user.id());
        if (credential == null) {
            performDummyComparison(command.loginCode());
            throw new StudentAuthenticationFailedException();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        int failureCount = credential.failureCount();
        boolean captchaRequired = credential.captchaRequired();
        if (credential.lockedUntil() != null && credential.lockedUntil().isAfter(now)) {
            throw new StudentAccountLockedException(credential.lockedUntil());
        }
        if (credential.lockedUntil() != null) {
            failureCount = 0;
            captchaRequired = false;
            updateFailureState(user.id(), failureCount, false, null);
        }
        if (captchaRequired) {
            captchaChallengeService.verify(
                    command.captchaChallengeId(), command.captchaAnswer(), account, deviceId);
        }

        boolean matches = loginCodeHasher.matches(
                command.loginCode(), credential.codeHash(), credential.codeSalt(), credential.keyVersion());
        if (!matches) {
            int nextFailureCount = failureCount + 1;
            if (nextFailureCount >= 10) {
                LocalDateTime lockedUntil = now.plus(properties.getAccountLockDuration());
                updateFailureState(user.id(), 10, true, lockedUntil);
                throw new StudentAccountLockedException(lockedUntil);
            }
            boolean requireCaptcha = nextFailureCount >= 5;
            updateFailureState(user.id(), nextFailureCount, requireCaptcha, null);
            if (requireCaptcha) {
                throw new CaptchaRequiredException();
            }
            throw new StudentAuthenticationFailedException();
        }

        if (credentialMapper.markLoginSuccess(user.id(), now) != 1) {
            throw new IllegalStateException("学生登录成功状态保存失败");
        }
        return authenticationService.createSession(user.id(), AuthClientType.MINIAPP, deviceId, deviceName);
    }

    private void updateFailureState(Long studentUserId, int failureCount, boolean captchaRequired, LocalDateTime lockedUntil) {
        if (credentialMapper.updateFailureState(studentUserId, failureCount, captchaRequired, lockedUntil) != 1) {
            throw new IllegalStateException("学生登录失败状态保存失败");
        }
    }

    private void performDummyComparison(String candidateCode) {
        String normalizedCode = candidateCode != null && CODE_PATTERN.matcher(candidateCode).matches()
                ? candidateCode : "0000";
        loginCodeHasher.matches(normalizedCode, dummyDigest.hash(), dummyDigest.salt(), dummyDigest.keyVersion());
    }

    private String required(String value, String fieldName, int maxLength) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
