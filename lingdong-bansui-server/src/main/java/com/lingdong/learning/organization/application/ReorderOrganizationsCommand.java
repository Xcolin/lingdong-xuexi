package com.lingdong.learning.organization.application;
import java.util.List;
/** 组织同级排序命令：parentId 为空表示根层级；条目集合必须完整覆盖该父级下的全部节点。 */
public record ReorderOrganizationsCommand(Long operatorId, Long parentId, List<OrganizationOrderItem> items) { }
