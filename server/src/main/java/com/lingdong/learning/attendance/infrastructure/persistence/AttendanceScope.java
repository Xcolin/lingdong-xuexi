package com.lingdong.learning.attendance.infrastructure.persistence;

import java.util.List;

/** 服务端生成的数据范围；空组织路径必须失败关闭。 */
public record AttendanceScope(Long userId, String mode, boolean allOrganizations, List<String> rootPaths) {
    public AttendanceScope { rootPaths = List.copyOf(rootPaths); }
}
