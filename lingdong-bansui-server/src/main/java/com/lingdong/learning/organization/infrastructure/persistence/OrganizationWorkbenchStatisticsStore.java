package com.lingdong.learning.organization.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 工作台统计的组织层级计数，按数据范围路径前缀聚合。 */
@Component
public class OrganizationWorkbenchStatisticsStore {

    private final JdbcTemplate jdbc;

    public OrganizationWorkbenchStatisticsStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 统计范围内启用中的校区/年级/班级数量，键为组织类型编码。 */
    public Map<String, Long> countEducationalOrganizations(boolean allOrganizations, List<String> rootPaths) {
        if (!allOrganizations && (rootPaths == null || rootPaths.isEmpty())) {
            return Map.of();
        }
        List<Object> arguments = new ArrayList<>();
        String scopePredicate;
        if (allOrganizations) {
            scopePredicate = "1 = 1";
        } else {
            StringBuilder predicates = new StringBuilder("(");
            for (int i = 0; i < rootPaths.size(); i++) {
                if (i > 0) {
                    predicates.append(" OR ");
                }
                predicates.append("org.organization_path LIKE CONCAT(?, '%')");
                arguments.add(rootPaths.get(i));
            }
            predicates.append(")");
            scopePredicate = predicates.toString();
        }
        String sql = "SELECT org.organization_type, COUNT(1)"
                + " FROM sys_organization org"
                + " WHERE org.organization_type IN ('CAMPUS','GRADE','CLASS')"
                + " AND org.status = 'ENABLED' AND org.effective_status = 'ENABLED'"
                + " AND " + scopePredicate
                + " GROUP BY org.organization_type";
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query(sql, (java.sql.ResultSet rs) -> {
            counts.put(rs.getString(1), rs.getLong(2));
        }, arguments.toArray());
        counts.putIfAbsent("CAMPUS", 0L);
        counts.putIfAbsent("GRADE", 0L);
        counts.putIfAbsent("CLASS", 0L);
        return counts;
    }
}
