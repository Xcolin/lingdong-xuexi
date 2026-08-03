package com.lingdong.learning.iam.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.domain.RoleDataScope;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Application service for custom role lifecycle commands.
 */
@Service
public class RoleApplicationService {
    private static final Pattern ROLE_CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{2,63}");

    private final RoleMapper roleMapper;
    private final IdGenerator idGenerator;

    public RoleApplicationService(RoleMapper roleMapper, IdGenerator idGenerator) {
        this.roleMapper = roleMapper;
        this.idGenerator = idGenerator;
    }

    /**
     * Keeps validation, duplicate detection and persistence in one transaction. The database unique
     * constraint remains the final guard when concurrent administrators submit the same role code.
     */
    @Transactional
    public Role createCustomRole(CreateCustomRoleCommand command) {
        Objects.requireNonNull(command, "创建角色请求不能为空");

        String code = requiredText(command.code(), "角色编码", 64);
        String name = requiredText(command.name(), "角色名称", 64);
        String description = optionalText(command.description(), 512);
        RoleDataScope dataScope = Objects.requireNonNull(command.dataScope(), "数据权限范围不能为空");

        if (!ROLE_CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("角色编码仅允许3至64位大写字母、数字和下划线，且必须以字母开头");
        }
        if (roleMapper.existsByCode(code)) {
            throw new DuplicateRoleCodeException(code);
        }

        Role role = Role.custom(idGenerator.nextId(), code, name, description, dataScope);
        try {
            roleMapper.insert(role);
            return roleMapper.findByCode(code);
        } catch (DuplicateKeyException exception) {
            throw new DuplicateRoleCodeException(code);
        }
    }

    private String requiredText(String value, String fieldName, int maxLength) {
        String normalized = optionalText(value, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return normalized;
    }

    private String optionalText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("文本长度不能超过" + maxLength + "个字符");
        }
        return normalized;
    }
}
