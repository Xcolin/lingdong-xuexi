package com.lingdong.learning.user.application;
import java.util.List;
/** 批量授予失败：整批回滚并携带失败用户与原因明细。 */
public class BatchRoleAssignmentException extends RuntimeException {
    private final List<String> details;
    public BatchRoleAssignmentException(List<String> details) {
        super("批量授予失败：" + String.join("；", details));
        this.details = List.copyOf(details);
    }
    public List<String> details() { return details; }
}
