package com.lingdong.learning.teacher.application;

/** 教师批量操作中的单项确定结果。 */
public record TeacherBatchItemResult(
        Long teacherUserId,
        boolean success,
        String errorCode,
        String message
) {
    public static TeacherBatchItemResult success(Long teacherUserId) {
        return new TeacherBatchItemResult(teacherUserId, true, null, null);
    }

    public static TeacherBatchItemResult failure(Long teacherUserId, String errorCode, String message) {
        return new TeacherBatchItemResult(teacherUserId, false, errorCode, message);
    }
}
