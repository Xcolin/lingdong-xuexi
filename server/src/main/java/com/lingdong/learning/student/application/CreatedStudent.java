package com.lingdong.learning.student.application;

import com.lingdong.learning.student.domain.Student;

/** 新建学生事务结果，明文登录码不得进入后续查询模型。 */
public record CreatedStudent(
        Student student,
        String studentAccount,
        String initialLoginCode
) {
}
