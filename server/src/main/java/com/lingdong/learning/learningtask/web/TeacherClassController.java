package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.learningtask.application.TeacherClassAssignmentService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 教师班级关系的 Web 管理与教师本人查询入口。 */
@RestController
@RequestMapping("/api/v1/teachers/{teacherUserId}/classes")
public class TeacherClassController {
    private final TeacherClassAssignmentService teacherClassAssignmentService;

    public TeacherClassController(TeacherClassAssignmentService teacherClassAssignmentService) {
        this.teacherClassAssignmentService = teacherClassAssignmentService;
    }

    @RequirePermission("TEACHER_CLASS_ASSIGN")
    @PutMapping("/{classId}")
    public TeacherClassResponse assign(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long teacherUserId,
            @PathVariable Long classId
    ) {
        return TeacherClassResponse.from(
                teacherClassAssignmentService.assign(currentUser, teacherUserId, classId));
    }

    @RequirePermission("TEACHER_CLASS_ASSIGN")
    @DeleteMapping("/{classId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long teacherUserId,
            @PathVariable Long classId
    ) {
        teacherClassAssignmentService.remove(currentUser, teacherUserId, classId);
    }

    @RequirePermission("LEARNING_TASK_CREATE")
    @GetMapping
    public List<TeacherClassResponse> list(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long teacherUserId
    ) {
        return teacherClassAssignmentService.list(currentUser, teacherUserId).stream()
                .map(TeacherClassResponse::from)
                .toList();
    }
}
