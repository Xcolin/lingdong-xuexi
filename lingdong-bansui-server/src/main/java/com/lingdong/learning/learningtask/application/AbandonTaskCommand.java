package com.lingdong.learning.learningtask.application;

/** 学生放弃本人任务命令；放弃类型区分放弃本次与申请免执行（05-10）。 */
public record AbandonTaskCommand(String reason, String abandonType) {
    public AbandonTaskCommand(String reason) {
        this(reason, null);
    }
}
