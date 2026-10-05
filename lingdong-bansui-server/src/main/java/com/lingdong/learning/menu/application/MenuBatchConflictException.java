package com.lingdong.learning.menu.application;
import java.util.List;
/** 批量提交编码冲突：整批拒绝并携带冲突明细。 */
public class MenuBatchConflictException extends RuntimeException {
    private final List<String> details;
    public MenuBatchConflictException(List<String> details) {
        super("编码冲突：" + String.join("；", details));
        this.details = List.copyOf(details);
    }
    public List<String> details() { return details; }
}
