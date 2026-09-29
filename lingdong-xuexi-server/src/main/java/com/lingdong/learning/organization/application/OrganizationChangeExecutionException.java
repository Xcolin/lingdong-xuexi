package com.lingdong.learning.organization.application;

/** 组织变更已获批准但不再满足执行条件。 */
public class OrganizationChangeExecutionException extends IllegalStateException {
    public OrganizationChangeExecutionException(String failureReason, Throwable cause) {
        super("组织变更执行失败：" + failureReason, cause);
    }
}
