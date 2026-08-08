package com.lingdong.learning.auth.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;

import java.util.Objects;

/** 先消费一次性二维码票据，再复用既有学生登录码风控建立小程序会话。 */
@Service
public class StudentQrLoginApplicationService {
    private final StudentQrTicketApplicationService ticketService;
    private final StudentCodeLoginApplicationService codeLoginService;
    private final UserMapper userMapper;
    private final CaptchaChallengeService captchaChallengeService;

    public StudentQrLoginApplicationService(
            StudentQrTicketApplicationService ticketService,
            StudentCodeLoginApplicationService codeLoginService,
            UserMapper userMapper,
            CaptchaChallengeService captchaChallengeService
    ) {
        this.ticketService = ticketService;
        this.codeLoginService = codeLoginService;
        this.userMapper = userMapper;
        this.captchaChallengeService = captchaChallengeService;
    }

    @Transactional(noRollbackFor = {
            StudentAuthenticationFailedException.class,
            CaptchaRequiredException.class,
            StudentAccountLockedException.class,
            StudentQrTicketInvalidException.class
    })
    public StudentQrAuthenticatedSession login(StudentQrLoginCommand command) {
        Objects.requireNonNull(command, "学生扫码登录请求不能为空");
        Long studentUserId = ticketService.consume(command.qrContent());
        AuthenticatedSession session = codeLoginService.loginByStudentUserId(
                studentUserId, command.loginCode(), command.deviceId(), command.deviceName(),
                command.captchaChallengeId(), command.captchaAnswer(), command.sourceAddress());
        User studentUser = userMapper.findById(studentUserId);
        if (studentUser == null) {
            throw new IllegalStateException("扫码登录成功后未找到学生账号");
        }
        return new StudentQrAuthenticatedSession(session, studentUser.username());
    }

    public IssuedCaptchaChallenge issueCaptcha(String qrContent, String deviceId) {
        Long studentUserId = ticketService.resolveActiveStudentUserId(qrContent);
        User studentUser = userMapper.findById(studentUserId);
        if (studentUser == null) {
            throw new StudentQrTicketInvalidException();
        }
        return captchaChallengeService.issue(studentUser.username(), deviceId);
    }
}
