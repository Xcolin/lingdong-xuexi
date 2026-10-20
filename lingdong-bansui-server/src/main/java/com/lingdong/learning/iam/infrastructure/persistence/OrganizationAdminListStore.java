package com.lingdong.learning.iam.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 读取单个组织的管理员列表（直接管理员 + 负责人），供小程序组织管理员设置视图展示。 */
@Component
public class OrganizationAdminListStore {
    private final JdbcTemplate jdbc;

    public OrganizationAdminListStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 管理员条目：owner 表示负责人（超级管理员），administrator 表示直接管理员（班级管理员）。 */
    public record AdminRow(Long userId, String username, String displayName, String mobile,
                           String status, boolean owner, boolean administrator) { }

    /** 读取给定组织的全部管理员与负责人，负责人排在前。 */
    public List<AdminRow> findAdministrators(long organizationId) {
        var rows = new ArrayList<AdminRow>();
        jdbc.query("SELECT user.id, user.username, user.display_name, user.mobile, user.status,"
                        + " (owner.user_id IS NOT NULL), (admin.user_id IS NOT NULL)"
                        + " FROM sys_user user"
                        + " LEFT JOIN sys_organization_owner owner ON owner.organization_id = ? AND owner.user_id = user.id"
                        + " LEFT JOIN sys_organization_admin admin ON admin.organization_id = ? AND admin.user_id = user.id"
                        + " WHERE owner.user_id IS NOT NULL OR admin.user_id IS NOT NULL"
                        + " ORDER BY (owner.user_id IS NOT NULL) DESC, user.id",
                (java.sql.ResultSet rs) -> {
                    rows.add(new AdminRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getString(5), rs.getBoolean(6), rs.getBoolean(7)));
                },
                organizationId, organizationId);
        return rows;
    }

    /** 批量读取用户直接管理（担任管理员）的组织名称列表。 */
    public Map<Long, List<String>> findManagedOrganizationNames(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        var distinct = userIds.stream().distinct().toList();
        if (distinct.size() > 500) throw new IllegalArgumentException("一次最多读取500个用户");
        var placeholders = String.join(",", distinct.stream().map(id -> "?").toList());
        var arguments = distinct.stream().map(id -> (Object) id).toList();
        var names = new HashMap<Long, List<String>>();
        jdbc.query("SELECT relation.user_id, organization.organization_name"
                        + " FROM sys_organization_admin relation"
                        + " JOIN sys_organization organization ON organization.id = relation.organization_id"
                        + " WHERE relation.user_id IN (" + placeholders + ")"
                        + " ORDER BY organization.organization_path, organization.id",
                (java.sql.ResultSet rs) -> {
                    names.computeIfAbsent(rs.getLong(1), key -> new ArrayList<String>()).add(rs.getString(2));
                },
                arguments.toArray());
        return names;
    }
}
