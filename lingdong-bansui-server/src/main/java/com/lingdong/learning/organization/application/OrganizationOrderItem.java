package com.lingdong.learning.organization.application;
/** 同级排序条目：目标顺序由条目列表顺序决定，附乐观锁版本。 */
public record OrganizationOrderItem(Long organizationId, Integer expectedVersion) { }
