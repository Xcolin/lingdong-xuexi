package com.lingdong.learning.organization.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 按班级组织批量读取班主任姓名（edu_class_academic_metadata 关联正常用户），供班级卡展示。 */
@Component
public class ClassHeadTeacherStore {
    private final JdbcTemplate jdbc;

    public ClassHeadTeacherStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 读取给定班级的班主任姓名，未设置或用户异常的班级不在结果中（按 null 处理）。 */
    public Map<Long, String> findHeadTeachersByClassIds(List<Long> classIds) {
        if (classIds == null || classIds.isEmpty()) return Map.of();
        var distinct = classIds.stream().distinct().toList();
        if (distinct.size() > 500) throw new IllegalArgumentException("一次最多读取500个班级");
        var placeholders = String.join(",", distinct.stream().map(id -> "?").toList());
        var arguments = distinct.stream().map(id -> (Object) id).toList();
        var names = new HashMap<Long, String>();
        jdbc.query("SELECT metadata.class_organization_id, user.display_name"
                        + " FROM edu_class_academic_metadata metadata"
                        + " JOIN sys_user user ON user.id = metadata.head_teacher_user_id AND user.status = 'ENABLED'"
                        + " WHERE metadata.class_organization_id IN (" + placeholders + ")",
                (java.sql.ResultSet rs) -> { names.put(rs.getLong(1), rs.getString(2)); },
                arguments.toArray());
        return names;
    }
}
