package com.lingdong.learning.datascope.application;

import com.lingdong.learning.organization.domain.Organization;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 当前用户经过角色、组织关系和组织管理员边界交集后的不可变组织数据范围。 */
public record OrganizationDataScope(
        boolean allOrganizations,
        boolean selfAllowed,
        List<String> rootPaths
) {
    public OrganizationDataScope {
        rootPaths = allOrganizations ? List.of() : normalize(rootPaths);
    }

    public static OrganizationDataScope empty() {
        return new OrganizationDataScope(false, false, List.of());
    }

    public static OrganizationDataScope all(boolean selfAllowed) {
        return new OrganizationDataScope(true, selfAllowed, List.of());
    }

    public boolean allows(Organization organization) {
        if (organization == null) {
            return false;
        }
        return allOrganizations || rootPaths.stream()
                .anyMatch(rootPath -> organization.path().startsWith(rootPath));
    }

    public boolean isEmpty() {
        return !allOrganizations && !selfAllowed && rootPaths.isEmpty();
    }

    static List<String> normalize(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return List.of();
        }
        List<String> orderedPaths = paths.stream()
                .filter(path -> path != null && !path.isBlank())
                .distinct()
                .sorted(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()))
                .toList();
        List<String> normalizedPaths = new ArrayList<>();
        for (String path : orderedPaths) {
            if (normalizedPaths.stream().noneMatch(path::startsWith)) {
                normalizedPaths.add(path);
            }
        }
        return List.copyOf(normalizedPaths);
    }
}
