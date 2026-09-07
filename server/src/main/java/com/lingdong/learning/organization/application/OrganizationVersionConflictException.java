package com.lingdong.learning.organization.application;

/** 组织节点已被其他操作修改时阻止旧版本覆盖。 */
public class OrganizationVersionConflictException extends IllegalStateException {
    public OrganizationVersionConflictException() {
        super("组织数据已变化，请刷新后重试");
    }
}
