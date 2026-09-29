package com.lingdong.learning.auth.application;

/** V42 对外展示的注销前置状态，到期只标记待后续执行。 */
public enum ParentAccountCancellationViewStatus {
    NONE,
    COOLING_OFF,
    READY_FOR_FINALIZATION
}
