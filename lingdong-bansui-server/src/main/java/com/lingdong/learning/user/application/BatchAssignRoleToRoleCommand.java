package com.lingdong.learning.user.application;
import java.util.List;
/** 角色侧批量授予用户命令：整批事务原子，任一失败整批回滚。 */
public record BatchAssignRoleToRoleCommand(Long operatorId, Long roleId, List<BatchRoleAssignmentItem> assignments) { }
