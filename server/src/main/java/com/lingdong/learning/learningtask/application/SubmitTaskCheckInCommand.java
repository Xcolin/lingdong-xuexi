package com.lingdong.learning.learningtask.application;

import java.util.List;

/** 学生提交文字和图片打卡命令。 */
public record SubmitTaskCheckInCommand(String content, List<Long> fileIds) {
    public SubmitTaskCheckInCommand(String content) {
        this(content, List.of());
    }
}
