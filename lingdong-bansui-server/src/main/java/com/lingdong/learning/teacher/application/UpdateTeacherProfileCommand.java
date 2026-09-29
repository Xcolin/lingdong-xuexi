package com.lingdong.learning.teacher.application;

/** 机构管理员可修改的教师资料；手机号未提供时保持原值，显式标记后才清空。 */
public record UpdateTeacherProfileCommand(String displayName, String mobile, boolean clearMobile) {
    public UpdateTeacherProfileCommand(String displayName, String mobile) {
        this(displayName, mobile, false);
    }
}
