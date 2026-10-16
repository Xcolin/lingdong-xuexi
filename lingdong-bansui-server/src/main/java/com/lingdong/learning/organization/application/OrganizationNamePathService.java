package com.lingdong.learning.organization.application;

import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 组织全路径名（如 学校·年级·班级）的统一口径，供组织树、用户目录与各业务选项展示使用。 */
@Service
public class OrganizationNamePathService {
    private static final String SEPARATOR = "·";
    private final OrganizationMapper mapper;

    public OrganizationNamePathService(OrganizationMapper mapper) {
        this.mapper = mapper;
    }

    /** 计算当前全部组织的 id → 全路径名映射（父链以「·」连接，根节点即自身名称）。 */
    public Map<Long, String> namePathsById() {
        List<Organization> all = mapper.findAll();
        Map<Long, Organization> byId = new HashMap<>();
        all.forEach(organization -> byId.put(organization.id(), organization));
        Map<Long, String> paths = new HashMap<>();
        for (Organization organization : all) {
            namePath(organization.id(), byId, paths);
        }
        return paths;
    }

    private String namePath(Long id, Map<Long, Organization> byId, Map<Long, String> memo) {
        String memoized = memo.get(id);
        if (memoized != null) return memoized;
        Organization organization = byId.get(id);
        if (organization == null) return "";
        String path;
        if (organization.parentId() == null) {
            path = organization.name();
        } else {
            String parentPath = namePath(organization.parentId(), byId, memo);
            path = parentPath.isBlank() ? organization.name() : parentPath + SEPARATOR + organization.name();
        }
        memo.put(id, path);
        return path;
    }
}
