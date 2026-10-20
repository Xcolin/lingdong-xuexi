package com.lingdong.learning.organization.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 按班级组织统计有效学员数（ACTIVE 班级关系 + 正常学生），供层级树与班级卡展示。 */
@Component
public class ClassMemberCountStore {
    private final JdbcTemplate jdbc;

    public ClassMemberCountStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 统计给定班级的有效学员数，未出现在结果中的班级计数按 0 处理。 */
    public Map<Long, Long> countMembersByClassIds(List<Long> classIds) {
        if (classIds == null || classIds.isEmpty()) return Map.of();
        var distinct = classIds.stream().distinct().toList();
        if (distinct.size() > 500) throw new IllegalArgumentException("一次最多统计500个班级");
        var placeholders = String.join(",", distinct.stream().map(id -> "?").toList());
        var arguments = distinct.stream().map(id -> (Object) id).toList();
        var counts = new HashMap<Long, Long>();
        jdbc.query("SELECT relation.organization_id, COUNT(DISTINCT relation.student_id) AS member_count"
                        + " FROM edu_student_organization relation"
                        + " JOIN edu_student student ON student.id = relation.student_id AND student.status = 'ENABLED'"
                        + " JOIN sys_organization org ON org.id = relation.organization_id"
                        + "   AND org.organization_type = 'CLASS' AND org.status = 'ENABLED' AND org.effective_status = 'ENABLED'"
                        + " WHERE relation.relation_type = 'CLASS' AND relation.status = 'ACTIVE'"
                        + "   AND relation.organization_id IN (" + placeholders + ")"
                        + " GROUP BY relation.organization_id",
                (java.sql.ResultSet rs) -> { counts.put(rs.getLong(1), rs.getLong(2)); },
                arguments.toArray());
        return counts;
    }
}
