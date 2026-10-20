package com.lingdong.learning.learningtask.application;

import java.util.List;

/** 学生提交文字和图片打卡命令；完成情况区分已完成与部分完成（05-08）。 */
public record SubmitTaskCheckInCommand(String content, List<Long> fileIds, String completion) {
    public SubmitTaskCheckInCommand(String content, List<Long> fileIds) {
        this(content, fileIds, null);
    }

    public SubmitTaskCheckInCommand(String content) {
        this(content, List.of(), null);
    }
}
