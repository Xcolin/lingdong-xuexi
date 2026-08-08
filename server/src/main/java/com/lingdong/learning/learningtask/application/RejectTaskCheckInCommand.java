package com.lingdong.learning.learningtask.application;

/** 当前审核人驳回任务打卡命令。 */
public record RejectTaskCheckInCommand(String reviewComment) {
}
