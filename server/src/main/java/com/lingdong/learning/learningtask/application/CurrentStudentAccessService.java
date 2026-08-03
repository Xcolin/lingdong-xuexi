package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.domain.StudentStatus;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
import org.springframework.stereotype.Service;

/** 统一从认证会话解析可用学生档案，禁止以请求参数替代当前学生身份。 */
@Service
public class CurrentStudentAccessService {
    private final StudentMapper studentMapper;

    public CurrentStudentAccessService(StudentMapper studentMapper) {
        this.studentMapper = studentMapper;
    }

    public Student require(AuthenticatedUser currentUser) {
        if (currentUser == null || !currentUser.roleCodes().contains("STUDENT")) {
            throw new SystemOperationAccessDeniedException("仅学生可操作本人任务");
        }
        Student student = studentMapper.findByStudentUserId(currentUser.userId());
        if (student == null || student.status() != StudentStatus.ENABLED) {
            throw new ResourceNotFoundException("学生档案不存在或不可用");
        }
        return student;
    }
}
