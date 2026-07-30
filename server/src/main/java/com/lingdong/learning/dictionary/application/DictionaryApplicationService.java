package com.lingdong.learning.dictionary.application;

import com.lingdong.learning.dictionary.domain.DictionaryItem;
import com.lingdong.learning.dictionary.domain.DictionaryStatus;
import com.lingdong.learning.dictionary.domain.DictionaryType;
import com.lingdong.learning.dictionary.infrastructure.cache.DictionaryItemCache;
import com.lingdong.learning.dictionary.infrastructure.persistence.DictionaryItemMapper;
import com.lingdong.learning.dictionary.infrastructure.persistence.DictionaryTypeMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Manages ordinary dictionary configuration while preserving the one-default-item invariant. */
@Service
public class DictionaryApplicationService {
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{2,63}");
    /** Critical platform dictionaries must be changed through the audited system-task path. */
    private static final Set<String> KEY_DICTIONARY_TYPE_CODES = Set.of(
            "TASK_STATUS",
            "ROLE_TYPE",
            "ORGANIZATION_TYPE",
            "AUDIT_STATUS"
    );

    private final DictionaryTypeMapper dictionaryTypeMapper;
    private final DictionaryItemMapper dictionaryItemMapper;
    private final UserRoleMapper userRoleMapper;
    private final DictionaryItemCache dictionaryItemCache;

    public DictionaryApplicationService(
            DictionaryTypeMapper dictionaryTypeMapper,
            DictionaryItemMapper dictionaryItemMapper,
            UserRoleMapper userRoleMapper,
            DictionaryItemCache dictionaryItemCache
    ) {
        this.dictionaryTypeMapper = dictionaryTypeMapper;
        this.dictionaryItemMapper = dictionaryItemMapper;
        this.userRoleMapper = userRoleMapper;
        this.dictionaryItemCache = dictionaryItemCache;
    }

    /** Creates an enabled dictionary type that may later be used by forms and list filters. */
    @Transactional
    public DictionaryType createType(CreateDictionaryTypeCommand command) {
        Objects.requireNonNull(command, "创建字典类型请求不能为空");
        requireSystemAdministrator(command.operatorId());

        String code = normalizeCode(command.code(), "字典类型编码");
        requireDirectMutationAllowed(code);
        String name = requiredText(command.name(), "字典类型名称", 50);
        int sortOrder = normalizeSortOrder(command.sortOrder());
        if (dictionaryTypeMapper.existsByCode(code)) {
            throw new IllegalStateException("字典类型编码已存在：" + code);
        }

        try {
            dictionaryTypeMapper.insert(DictionaryType.enabled(code, name, sortOrder));
            return dictionaryTypeMapper.findByCode(code);
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("字典类型编码已存在：" + code);
        }
    }

    /** Updates an existing type without changing its stable code. */
    @Transactional
    public DictionaryType updateType(UpdateDictionaryTypeCommand command) {
        Objects.requireNonNull(command, "更新字典类型请求不能为空");
        requireSystemAdministrator(command.operatorId());
        if (command.typeId() == null) {
            throw new IllegalArgumentException("字典类型不能为空");
        }

        DictionaryType type = dictionaryTypeMapper.findByIdForUpdate(command.typeId());
        if (type == null) {
            throw new IllegalStateException("字典类型不存在：" + command.typeId());
        }
        requireDirectMutationAllowed(type.code());

        DictionaryType updated = new DictionaryType(
                type.id(),
                type.code(),
                requiredText(command.name(), "字典类型名称", 50),
                Objects.requireNonNull(command.status(), "字典类型状态不能为空"),
                normalizeSortOrder(command.sortOrder()),
                type.createdAt(),
                type.updatedAt()
        );
        if (dictionaryTypeMapper.update(updated) != 1) {
            throw new IllegalStateException("字典类型更新失败：" + command.typeId());
        }
        dictionaryItemCache.evict(type.code());
        return dictionaryTypeMapper.findById(type.id());
    }

    /** Adds an enabled item and atomically replaces the old default when requested. */
    @Transactional
    public DictionaryItem createItem(CreateDictionaryItemCommand command) {
        Objects.requireNonNull(command, "创建字典项请求不能为空");
        requireSystemAdministrator(command.operatorId());
        if (command.typeId() == null) {
            throw new IllegalArgumentException("字典类型不能为空");
        }

        DictionaryType type = command.defaultItem()
                ? dictionaryTypeMapper.findByIdForUpdate(command.typeId())
                : dictionaryTypeMapper.findById(command.typeId());
        if (type == null || type.status() != DictionaryStatus.ENABLED) {
            throw new IllegalStateException("字典类型不存在或已停用：" + command.typeId());
        }
        requireDirectMutationAllowed(type.code());

        String code = normalizeCode(command.code(), "字典项编码");
        String name = requiredText(command.name(), "字典项名称", 50);
        int sortOrder = normalizeSortOrder(command.sortOrder());
        if (dictionaryItemMapper.existsByTypeIdAndCode(type.id(), code)) {
            throw new IllegalStateException("同一字典类型下编码已存在：" + code);
        }

        if (command.defaultItem()) {
            dictionaryItemMapper.clearDefaultByTypeId(type.id());
        }
        try {
            dictionaryItemMapper.insert(DictionaryItem.enabled(type.id(), code, name, sortOrder, command.defaultItem()));
            DictionaryItem item = dictionaryItemMapper.findByTypeIdAndCode(type.id(), code);
            dictionaryItemCache.evict(type.code());
            return item;
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("同一字典类型下编码已存在：" + code);
        }
    }

    /** Updates mutable item properties and removes the type's selectable-item cache after success. */
    @Transactional
    public DictionaryItem updateItem(UpdateDictionaryItemCommand command) {
        Objects.requireNonNull(command, "更新字典项请求不能为空");
        requireSystemAdministrator(command.operatorId());
        if (command.itemId() == null) {
            throw new IllegalArgumentException("字典项不能为空");
        }

        DictionaryItem snapshot = dictionaryItemMapper.findById(command.itemId());
        if (snapshot == null) {
            throw new IllegalStateException("字典项不存在：" + command.itemId());
        }

        // Lock the type before its items so this path matches default-item creation.
        DictionaryType type = dictionaryTypeMapper.findByIdForUpdate(snapshot.typeId());
        if (type == null) {
            throw new IllegalStateException("字典类型不存在：" + snapshot.typeId());
        }
        requireDirectMutationAllowed(type.code());

        DictionaryItem item = dictionaryItemMapper.findByIdForUpdate(command.itemId());
        if (item == null) {
            throw new IllegalStateException("字典项不存在：" + command.itemId());
        }

        String name = requiredText(command.name(), "字典项名称", 50);
        int sortOrder = normalizeSortOrder(command.sortOrder());
        DictionaryStatus status = Objects.requireNonNull(command.status(), "字典项状态不能为空");
        if (status == DictionaryStatus.DISABLED && command.defaultItem()) {
            throw new IllegalArgumentException("停用的字典项不能设为默认项");
        }
        if (command.defaultItem()) {
            dictionaryItemMapper.clearDefaultByTypeId(type.id());
        }

        DictionaryItem updated = new DictionaryItem(
                item.id(),
                item.typeId(),
                item.code(),
                name,
                sortOrder,
                command.defaultItem(),
                status,
                item.createdAt(),
                item.updatedAt()
        );
        if (dictionaryItemMapper.update(updated) != 1) {
            throw new IllegalStateException("字典项更新失败：" + command.itemId());
        }
        dictionaryItemCache.evict(type.code());
        return dictionaryItemMapper.findById(item.id());
    }

    private void requireSystemAdministrator(Long operatorId) {
        if (operatorId == null || !userRoleMapper.hasRoleCode(operatorId, "SYS_ADMIN")) {
            throw new IllegalStateException("仅系统管理员可管理数据字典");
        }
    }

    private String normalizeCode(String value, String fieldName) {
        String code = requiredText(value, fieldName, 64).toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException(fieldName + "仅允许3至64位大写字母、数字和下划线，且必须以字母开头");
        }
        return code;
    }

    private void requireDirectMutationAllowed(String typeCode) {
        if (KEY_DICTIONARY_TYPE_CODES.contains(typeCode)) {
            throw new IllegalStateException("关键字典仅能通过系统任务审批后变更：" + typeCode);
        }
    }

    private String requiredText(String value, String fieldName, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private int normalizeSortOrder(Integer sortOrder) {
        if (sortOrder == null) {
            return 0;
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("排序值不能小于0");
        }
        return sortOrder;
    }
}
