package com.lingdong.learning.exportjob.application;
import java.util.List;
/** 从当前账号解析的任务报表权限，绝不接受 HTTP 提供。 */
public record StudentTaskVisibility(long userId, String role, boolean allOrganizations, List<String> rootPaths) {
 public StudentTaskVisibility { rootPaths = List.copyOf(rootPaths); }
}
