package com.lingdong.learning.user.application;

import com.lingdong.learning.user.infrastructure.persistence.UserNameRow;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 将用户标识解析为展示姓名，供申请人/审核人等只读展示统一使用。
 * 约定：标识为 null 时返回 null（前端渲染占位符）；标识非空但账号不存在（已注销）时返回“未知用户”。
 */
@Component
public class UserDisplayNameResolver {
    static final String UNKNOWN_USER = "未知用户";

    private final UserMapper userMapper;

    public UserDisplayNameResolver(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** 解析单个用户标识为姓名。 */
    public String resolve(Long userId) {
        if (userId == null) {
            return null;
        }
        return resolveAll(Set.of(userId)).getOrDefault(userId, UNKNOWN_USER);
    }

    /** 批量解析用户标识为姓名，返回标识到姓名的映射；不存在的标识不会出现在结果中。 */
    public Map<Long, String> resolveAll(Collection<Long> userIds) {
        Set<Long> ids = new LinkedHashSet<>();
        if (userIds != null) {
            for (Long id : userIds) {
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (UserNameRow row : userMapper.findNamesByIds(ids)) {
            if (row.id() != null) {
                names.put(row.id(), row.displayName());
            }
        }
        return names;
    }

    /** 依据批量解析结果将标识转换为展示姓名，缺失账号回退“未知用户”。 */
    public String nameOf(Map<Long, String> resolvedNames, Long userId) {
        if (userId == null) {
            return null;
        }
        return Objects.toString(resolvedNames.get(userId), UNKNOWN_USER);
    }
}
