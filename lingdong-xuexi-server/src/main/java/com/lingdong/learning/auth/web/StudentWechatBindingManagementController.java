package com.lingdong.learning.auth.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.application.StudentWechatBindingManagementService;
import com.lingdong.learning.common.security.RequirePermission;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Web 与小程序家长端共用的学生微信绑定管理接口。 */
@RestController
@RequestMapping("/api/v1")
public class StudentWechatBindingManagementController {
    private final StudentWechatBindingManagementService service;

    public StudentWechatBindingManagementController(StudentWechatBindingManagementService service) {
        this.service = service;
    }

    @GetMapping("/student-wechat-bindings")
    @RequirePermission("STUDENT_WECHAT_UNBIND")
    public List<StudentWechatBindingSummaryResponse> list(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return service.list(currentUser).stream().map(StudentWechatBindingSummaryResponse::from).toList();
    }

    @PostMapping("/students/{studentId}/wechat-unbindings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission("STUDENT_WECHAT_UNBIND")
    public void unbind(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long studentId,
            @RequestBody StudentWechatUnbindingRequest request
    ) {
        service.unbind(currentUser, studentId, request.confirmation());
    }
}
