package com.lingdong.learning.teacher.application;

/** Web 端允许执行的教师批量操作。 */
public enum TeacherBatchOperation {
    ENABLE,
    DISABLE,
    LOCK,
    BIND_CLASS,
    UNBIND_CLASS;

    public boolean requiresClass() {
        return this == BIND_CLASS || this == UNBIND_CLASS;
    }
}
