package com.lingdong.learning.organization.application;

/** 组织自身或任一祖先停用时，阻止继续发起新的业务操作。 */
public class OrganizationNotOperationalException extends RuntimeException {
    public OrganizationNotOperationalException(Long organizationId) {
        super("组织当前不可开展新业务：" + organizationId);
    }
}
