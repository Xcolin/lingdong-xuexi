package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.domain.DeviceSessionRecord;
import com.lingdong.learning.auth.infrastructure.persistence.DeviceSessionMapper;
import com.lingdong.learning.student.application.IssuedStudentCredential;
import com.lingdong.learning.student.application.StudentIdentityProvisioningService;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.domain.StudentCredential;
import com.lingdong.learning.student.infrastructure.persistence.StudentCredentialMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StudentCodeLoginApplicationServiceTest {
    @Autowired private StudentCodeLoginApplicationService loginService;
    @Autowired private StudentIdentityProvisioningService provisioningService;
    @Autowired private CaptchaChallengeService captchaChallengeService;
    @Autowired private StudentMapper studentMapper;
    @Autowired private StudentCredentialMapper credentialMapper;
    @Autowired private DeviceSessionMapper sessionMapper;

    private IssuedStudentCredential issued;
    private String deviceId;

    @BeforeEach
    void createEnabledStudent() {
        issued = provisioningService.issue("登录状态机学生");
        deviceId = "student-device-" + issued.studentUserId();
        studentMapper.insert(Student.create(
                1_874_244_142_494_646_520L, "登录状态机学生", "G3", issued.studentUserId()));
    }

    @Test
    void createsMiniappSessionAndClearsFailureStateAfterSuccessfulCodeLogin() {
        AuthenticatedSession session = loginService.login(command(issued.plainLoginCode(), null, null));

        DeviceSessionRecord persisted = sessionMapper.findById(session.sessionId());
        StudentCredential credential = credentialMapper.findByStudentUserId(issued.studentUserId());
        assertThat(persisted.clientType()).isEqualTo(AuthClientType.MINIAPP);
        assertThat(persisted.userId()).isEqualTo(issued.studentUserId());
        assertThat(credential.failureCount()).isZero();
        assertThat(credential.lastSuccessAt()).isNotNull();
    }

    @Test
    void requiresCaptchaFromFifthFailureAndLocksOnTenthCodeFailure() {
        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThatThrownBy(() -> loginService.login(command("9999", null, null)))
                    .isInstanceOf(StudentAuthenticationFailedException.class);
        }
        assertThatThrownBy(() -> loginService.login(command("9999", null, null)))
                .isInstanceOf(CaptchaRequiredException.class);
        assertThat(credentialMapper.findByStudentUserId(issued.studentUserId()).failureCount()).isEqualTo(5);

        for (int attempt = 6; attempt <= 9; attempt++) {
            IssuedCaptchaChallenge challenge = captchaChallengeService.issue(issued.studentAccount(), deviceId);
            assertThatThrownBy(() -> loginService.login(command("9999", challenge.challengeId(), "AB12")))
                    .isInstanceOf(CaptchaRequiredException.class);
        }
        IssuedCaptchaChallenge tenthChallenge = captchaChallengeService.issue(
                issued.studentAccount(), deviceId);
        assertThatThrownBy(() -> loginService.login(command("9999", tenthChallenge.challengeId(), "AB12")))
                .isInstanceOf(StudentAccountLockedException.class);

        StudentCredential locked = credentialMapper.findByStudentUserId(issued.studentUserId());
        assertThat(locked.failureCount()).isEqualTo(10);
        assertThat(locked.lockedUntil()).isNotNull();
    }

    @Test
    void invalidCaptchaDoesNotIncreaseCodeFailureCount() {
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                loginService.login(command("9999", null, null));
            } catch (StudentAuthenticationFailedException | CaptchaRequiredException ignored) {
                // 前五次错误只用于进入验证码状态。
            }
        }
        IssuedCaptchaChallenge challenge = captchaChallengeService.issue(issued.studentAccount(), deviceId);

        assertThatThrownBy(() -> loginService.login(command("9999", challenge.challengeId(), "WRONG")))
                .isInstanceOf(CaptchaRequiredException.class);
        assertThat(credentialMapper.findByStudentUserId(issued.studentUserId()).failureCount()).isEqualTo(5);
    }

    private StudentCodeLoginCommand command(String loginCode, String challengeId, String captchaAnswer) {
        return new StudentCodeLoginCommand(
                issued.studentAccount(), loginCode, deviceId, "学生测试设备",
                challengeId, captchaAnswer, "127.0.0.1"
        );
    }
}
