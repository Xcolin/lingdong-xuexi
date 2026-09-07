package com.lingdong.learning.auth.web;

import com.lingdong.learning.auth.application.StudentWechatAuthenticationService;
import com.lingdong.learning.auth.application.StudentWechatBindingCodeCommand;
import com.lingdong.learning.auth.application.StudentWechatBindingCommand;
import com.lingdong.learning.auth.application.StudentWechatSessionCommand;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 小程序学生微信快捷登录和首次双重绑定的公开预认证入口。 */
@RestController
@RequestMapping("/api/v1/auth")
public class StudentWechatAuthenticationController {
    private final StudentWechatAuthenticationService service;

    public StudentWechatAuthenticationController(StudentWechatAuthenticationService service) {
        this.service = service;
    }

    @PostMapping("/student-wechat-sessions")
    public StudentWechatSessionExchangeResponse exchange(@RequestBody StudentWechatSessionRequest request) {
        return StudentWechatSessionExchangeResponse.from(service.exchange(new StudentWechatSessionCommand(
                request.temporaryCode(), request.deviceId(), request.deviceName())));
    }

    @PostMapping("/student-wechat-binding-codes")
    @ResponseStatus(HttpStatus.CREATED)
    public StudentWechatBindingCodeResponse issueCode(
            @RequestBody StudentWechatBindingCodeRequest request,
            HttpServletRequest servletRequest
    ) {
        return StudentWechatBindingCodeResponse.from(service.issueBindingCode(
                new StudentWechatBindingCodeCommand(
                        request.bindingTicket(), request.studentAccount(), request.loginCode(),
                        request.deviceId(), request.deviceName(), request.captchaChallengeId(),
                        request.captchaAnswer(), servletRequest.getRemoteAddr())));
    }

    @PostMapping("/student-wechat-bindings")
    @ResponseStatus(HttpStatus.CREATED)
    public StudentWechatAuthenticatedSessionResponse bind(@RequestBody StudentWechatBindingRequest request) {
        return StudentWechatAuthenticatedSessionResponse.from(service.bind(new StudentWechatBindingCommand(
                request.verificationTicket(), request.smsCode(), request.deviceId(), request.deviceName())));
    }
}
